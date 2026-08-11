/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.api.domain.zone

import cats.effect.IO
import cats.implicits._
import vinyldns.api.Interfaces
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.api.domain.membership.MembershipService
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.Encryption
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{Group, GroupRepository}
import vinyldns.core.domain.zone.{ZoneRepository, ZoneStatus}
import vinyldns.core.domain.zone.generate._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.networknt.schema.{JsonSchemaFactory, SpecVersion}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.{JObject, JValue}
import org.slf4j.LoggerFactory

import java.net.{HttpURLConnection, URL}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Try

object GenerateZoneService {
  def apply(
      dataAccessor: ApiDataAccessor,
      zoneValidations: ZoneValidations,
      accessValidation: AccessValidationsAlgebra,
      crypto: CryptoAlgebra,
      membershipService: MembershipService,
      dnsProviderApiConnection: DnsProviderApiConnection
  ): GenerateZoneService =
    new GenerateZoneService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.generateZoneRepository,
      zoneValidations,
      accessValidation,
      crypto,
      membershipService,
      dnsProviderApiConnection
    )
}

class GenerateZoneService(
    zoneRepository: ZoneRepository,
    groupRepository: GroupRepository,
    generateZoneRepository: GenerateZoneRepository,
    zoneValidations: ZoneValidations,
    accessValidation: AccessValidationsAlgebra,
    crypto: CryptoAlgebra,
    membershipService: MembershipService,
    dnsProviderApiConnection: DnsProviderApiConnection
) extends GenerateZoneServiceAlgebra {

  import accessValidation._
  import zoneValidations._
  import Interfaces._

  private val logger = LoggerFactory.getLogger(classOf[GenerateZoneService])

  def getGenerateZoneByName(zoneName: String, auth: AuthPrincipal): Result[GenerateZone] =
    for {
      generateZone <- getGenerateZoneByNameOrFail(ensureTrailingDot(zoneName))
      _ <- canSeeGenerateZone(auth, generateZone).toResult
    } yield generateZone

  def getGeneratedZoneById(zoneId: String, auth: AuthPrincipal): Result[GenerateZone] =
    for {
      generateZone <- getGeneratedZoneOrFail(zoneId)
      _ <- canSeeGenerateZone(auth, generateZone).toResult
    } yield generateZone

  // Bound external provider calls so a slow or hung provider cannot block the request thread
  // indefinitely. Values are in milliseconds.
  private val dnsProviderConnectTimeoutMs = 10000
  private val dnsProviderReadTimeoutMs = 30000

  def createConnection(apiUrl: String): HttpURLConnection = {
    val connection = new URL(apiUrl).openConnection().asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(dnsProviderConnectTimeoutMs)
    connection.setReadTimeout(dnsProviderReadTimeoutMs)
    connection
  }

  private def schemaValidationResult(
                                      providerConfig: DnsProviderConfig,
                                      operation: String,
                                      params: Map[String, JValue]
                                    ): Result[Unit] = providerConfig.schemas.get(operation) match {
    case Some(schema) => JsonSchemaValidator.validate(schema, params).toResult
    case None =>
      // Fail closed: without a schema we cannot validate the provider params, so refuse to
      // forward unvalidated input to the provider. A missing schema is an operator
      // misconfiguration, not a client error, so surface it as a server-side failure.
      val failure: Either[Throwable, Unit] = Left(
        new RuntimeException(
          s"No request-validation schema is configured for operation '$operation'; " +
            "refusing to process provider parameters without one."
        )
      )
      failure.toResult
  }

  def handleGenerateZoneRequest(
                                 request: ZoneGenerationInput,
                                 auth: AuthPrincipal
                               ): Result[GenerateZone] =
    for {
      _ <- validateZoneName(request.zoneName).toResult
      _ <- membershipService.emailValidation(request.email)
      // Validate input
      providerConfig <- validateProvider(request.provider, dnsProviderApiConnection.providers).toResult

      _ <- schemaValidationResult(providerConfig, "create-zone", request.providerParams)
      _ <- logger.info(s"Request providerParams: ${request.providerParams}").toResult

      // Build request and endpoint
      endpointTemplate <- requireEndpoint(providerConfig, "create-zone").toResult
      endpoint <- buildGenerateZoneEndpoint(endpointTemplate, request).toResult
      requestJsonOpt = buildGenerateZoneRequestJson(providerConfig.requestTemplates.get("create-zone"), request)

      // Authorization and existence checks
      _ <- adminGroupExists(request.groupId)
      _ <- canChangeZone(auth, request.zoneName, request.groupId).toResult
      _ <- generateZoneDoesNotExist(request.zoneName)
      // Cross-table check (D6): a generated zone must not collide with an existing
      // VinylDNS-managed zone. The reverse check in connectToZone is intentionally omitted:
      // the supported two-step flow generates a zone and then connects to it by the same name,
      // so an existing generate_zone record must not block connecting to that zone.
      _ <- zoneDoesNotExist(request.zoneName)

      // Send request
      _ <- logger.info(s"Request: provider=${request.provider}, path=$endpoint, request=$requestJsonOpt").toResult
      dnsProviderConn <- createConnection(endpoint).toResult
      dnsConnResponse <- createDnsZoneService(Encryption.decrypt(crypto, providerConfig.apiKey), "create-zone", requestJsonOpt, dnsProviderConn).toResult

      // Process response
      responseCode = dnsConnResponse.getResponseCode
      _ <- logger.info(s"response code: $responseCode").toResult
      inputStream = if (responseCode >= 400) dnsConnResponse.getErrorStream else dnsConnResponse.getInputStream
      responseMessage: String = Source.fromInputStream(inputStream, "UTF-8").mkString
      _ <- isValidGenerateZoneConn(responseCode, responseMessage).toResult

      // Only parse JSON if the response is non-empty
      responseJson = if (responseMessage.nonEmpty) parse(responseMessage) else JNothing
      zoneGenerateResponse = ZoneGenerationResponse(
        responseCode = Some(responseCode),
        status = Some(dnsConnResponse.getResponseMessage),
        message = Some(responseJson),
        changeType = GenerateZoneChangeType.Create
      )
      zoneToGenerate = GenerateZone(request).copy(response = Some(zoneGenerateResponse))
      _ <- logger.info(s"zone generation response: Create: $zoneToGenerate").toResult
      _ <- generateZoneRepository.save(zoneToGenerate).toResult[GenerateZone]

    } yield zoneToGenerate

  def handleUpdateGeneratedZoneRequest(
                                 request: ZoneGenerationInput,
                                 auth: AuthPrincipal
                               ): Result[GenerateZone] =
    for {
      existingGeneratedZone <- getGenerateZoneByName(request.zoneName, auth)
      _ <- validateProviderUnchanged(request.provider, existingGeneratedZone.provider).toResult
      _ <- membershipService.emailValidation(request.email)
      _ <- canChangeZone(auth, existingGeneratedZone.zoneName, existingGeneratedZone.groupId).toResult
      _ <- adminGroupExists(request.groupId)
      // Only re-check access when the admin group changes (see updateZone); canChangeZone
      // authorizes purely on admin group membership.
      _ <- if (request.groupId != existingGeneratedZone.groupId)
        canChangeZone(auth, request.zoneName, request.groupId).toResult
      else IO.unit.toResult

      // Validate input
      providerConfig <- validateProvider(request.provider, dnsProviderApiConnection.providers).toResult
      _ <- validateZoneName(request.zoneName).toResult
      _ <- schemaValidationResult(providerConfig, "update-zone", request.providerParams)

      _ <- logger.info(s"Request providerParams: ${request.providerParams}").toResult

      // Build request and endpoint
      endpointTemplate <- requireEndpoint(providerConfig, "update-zone").toResult
      endpoint <- buildGenerateZoneEndpoint(endpointTemplate, request).toResult
      requestJsonOpt = buildGenerateZoneRequestJson(providerConfig.requestTemplates.get("update-zone"), request)

      // Send request
      _ <- logger.info(s"Request: provider=${request.provider}, path=$endpoint, request=$requestJsonOpt").toResult
      dnsProviderConn <- createConnection(endpoint).toResult
      dnsConnResponse <- createDnsZoneService(Encryption.decrypt(crypto, providerConfig.apiKey), "update-zone", requestJsonOpt, dnsProviderConn).toResult

      // Process response
      responseCode = dnsConnResponse.getResponseCode
      _ <- logger.info(s"response code: $responseCode").toResult
      inputStream = if (responseCode >= 400) dnsConnResponse.getErrorStream else dnsConnResponse.getInputStream
      responseMessage: String = Source.fromInputStream(inputStream, "UTF-8").mkString
      _ <- isValidGenerateZoneConn(responseCode, responseMessage).toResult

      // Only parse JSON if the response is non-empty
      responseJson = if (responseMessage.nonEmpty) parse(responseMessage) else JNothing

      zoneGenerateResponse = ZoneGenerationResponse(
        responseCode = Some(responseCode),
        status = Some(dnsConnResponse.getResponseMessage),
        message = Some(responseJson),
        changeType = GenerateZoneChangeType.Update
      )
      zoneToUpdate = existingGeneratedZone.copy(
        email = request.email,
        groupId = request.groupId,
        // PUT semantics: the request's providerParams fully replace the stored set so params
        // can be removed. (Previously merged with ++, which made removal impossible.)
        providerParams = request.providerParams,
        response = Some(zoneGenerateResponse),
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
      )
      _ <- logger.info(s"zone generation response: Update: $zoneToUpdate").toResult
      _ <- generateZoneRepository.save(zoneToUpdate).toResult[GenerateZone]
    } yield zoneToUpdate


  def handleDeleteGeneratedZoneRequest(
                                           generatedZoneId: String,
                                           auth: AuthPrincipal
                                       ): Result[GenerateZone] =
    for {
      generatedZone <- getGeneratedZoneOrFail(generatedZoneId)
      _ <- canChangeZone(auth, generatedZone.zoneName, generatedZone.groupId).toResult
      providerConfig <- validateProvider(generatedZone.provider, dnsProviderApiConnection.providers).toResult
      request = ZoneGenerationInput(
        zoneName = generatedZone.zoneName,
        provider = generatedZone.provider,
        groupId = generatedZone.groupId,
        email = generatedZone.email,
        providerParams = generatedZone.providerParams
      )

      deleteEndpointUrl <- requireEndpoint(providerConfig, "delete-zone").toResult
      endpoint <- buildGenerateZoneEndpoint(deleteEndpointUrl, request).toResult

      dnsProviderConn <- createConnection(endpoint).toResult
      dnsConnResponse <- createDnsZoneService(Encryption.decrypt(crypto, providerConfig.apiKey), "delete-zone", None, dnsProviderConn).toResult

      // Process response
      responseCode = dnsConnResponse.getResponseCode
      _ <- logger.info(s"response code: $responseCode").toResult
      inputStream = if (responseCode >= 400) dnsConnResponse.getErrorStream else dnsConnResponse.getInputStream
      responseMessage: String = Source.fromInputStream(inputStream, "UTF-8").mkString
      _ <- isValidGenerateZoneConn(responseCode, responseMessage).toResult

      // Only parse JSON if the response is non-empty
      responseJson = if (responseMessage.nonEmpty) parse(responseMessage) else JNothing
      zoneGenerateResponse = ZoneGenerationResponse(
        responseCode = Some(responseCode),
        status = Some(dnsConnResponse.getResponseMessage),
        message = Some(responseJson),
        changeType = GenerateZoneChangeType.Delete
      )
      // Preserve the stored zone's server-owned fields (created/updated, id, etc.) and only
      // attach the delete response, rather than rebuilding from the request.
      zoneToDelete = generatedZone.copy(response = Some(zoneGenerateResponse))
      _ <- logger.info(s"zone generation response: Delete: $zoneToDelete").toResult
      _ <- generateZoneRepository.delete(zoneToDelete).toResult[GenerateZone]

    } yield zoneToDelete


  // Build a Generate Zone JSON request using template engine
  private def buildGenerateZoneRequestJson(
                                            maybeRequestTemplate: Option[String],
                                            zoneGenerationInput: ZoneGenerationInput
                                          ): Option[String] = {
    val baseParams = Map(
      "zoneName" -> JString(zoneGenerationInput.zoneName),
      "provider" -> JString(zoneGenerationInput.provider),
      "groupId"  -> JString(zoneGenerationInput.groupId),
      "email"    -> JString(zoneGenerationInput.email)
    )

    maybeRequestTemplate.map { requestTemplate =>
      TemplateEngine.renderTemplate(requestTemplate, baseParams ++ zoneGenerationInput.providerParams)
    }
  }

  // Fail closed on a missing endpoint, mirroring schemaValidationResult: a bare
  // providerConfig.endpoints(op) throws a raw NoSuchElementException on operator misconfig,
  // which undermines the fail-closed contract. Surface it as a clean server-side failure.
  private def requireEndpoint(
                               providerConfig: DnsProviderConfig,
                               operation: String
                             ): Either[Throwable, String] =
    providerConfig.endpoints.get(operation) match {
      case Some(url) => Right(url)
      case None =>
        Left(
          new RuntimeException(
            s"No endpoint is configured for operation '$operation'; refusing to process the request."
          )
        )
    }

  private def buildGenerateZoneEndpoint(
                                         endpointTemplate: String,
                                         zoneGenerationInput: ZoneGenerationInput
                                       ): Either[Throwable, String] = {
    val baseParams = Map(
      "zoneName" -> zoneGenerationInput.zoneName,
      "provider" -> zoneGenerationInput.provider,
      "groupId" -> zoneGenerationInput.groupId,
      "email" -> zoneGenerationInput.email
    )

    val providerParams = zoneGenerationInput.providerParams.map {
      case (k, JString(v)) => k -> v
      case (k, JInt(v)) => k -> v.toString
      case (k, JDouble(v)) => k -> v.toString
      case (k, JBool(v)) => k -> v.toString
      case (k, JNull) => k -> ""
      case (k, v) => k -> compact(render(v)) // for arrays/objects
    }

    val endpoint = TemplateEngine.substituteEndpoint(endpointTemplate, baseParams ++ providerParams)

    // Never send an endpoint URL with unresolved {{key}} placeholders to the provider. Unlike the
    // JSON body (where unfilled placeholders are pruned as optional params), a leftover in the URL
    // means the operator's endpoint template references a param we cannot supply — reject it.
    val unresolved = "\\{\\{[^}]*\\}\\}".r.findAllIn(endpoint).toList
    if (unresolved.nonEmpty)
      Left(
        new RuntimeException(
          s"Endpoint URL has unresolved placeholders after substitution: ${unresolved.mkString(", ")}"
        )
      )
    else Right(endpoint)
  }

  object TemplateEngine {
    /** Renders a JSON template, substituting fields and string placeholders with provided params. */
    def renderTemplate(template: String, params: Map[String, JValue]): String = {
      val templateJson = parse(template)
      val withFieldsReplaced = replaceFields(templateJson, params)
      val withPlaceholdersReplaced = replacePlaceholders(withFieldsReplaced, params)
      val pruned = withPlaceholdersReplaced.pruneUnusedFields()
      compact(render(pruned))
    }

    /** Substitutes {{key}} in a plain string template with URL-encoded values from params. */
    def substituteEndpoint(endpointTemplate: String, params: Map[String, String]): String =
      params.foldLeft(endpointTemplate) { case (url, (key, value)) =>
        val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.toString)
        url.replace(s"{{$key}}", encoded)
      }

    // --- Helpers ---

    private def replaceFields(json: JValue, params: Map[String, JValue]): JValue =
      json.transformField {
        case (key, _) if params.contains(key) => (key, params(key))
      }

    private def replacePlaceholders(json: JValue, params: Map[String, JValue]): JValue =
      json.transform {
        case JString(s) if s.startsWith("{{") && s.endsWith("}}") =>
          val key = s.substring(2, s.length - 2).trim
          params.getOrElse(key, JString(s))
        case other => other
      }

    implicit class JsonPruner(json: JValue) {
      def pruneUnusedFields(): JValue = json match {
        case JObject(fields) =>
          JObject(fields.flatMap {
            case (key, value) =>
              val pruned = value.pruneUnusedFields()
              pruned match {
                case JString(s) if s.startsWith("{{") && s.endsWith("}}") => None
                case _ => Some((key, pruned))
              }
          })
        case JArray(items) => JArray(items.map(_.pruneUnusedFields()))
        case other => other
      }
    }
  }

  object JsonSchemaValidator {
    private val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    def validate(
                  schemaJson: String,
                  data: Map[String, JValue]
                ): Either[Throwable, Unit] = {
      for {
        dataNode <- {
          val dataJson = JObject(data.toList)
          val dataString = compact(render(dataJson))
          Try(objectMapper.readTree(dataString)).toEither.left.map(e =>
            InvalidRequest(s"Could not parse data as JSON: ${e.getMessage}")
          )
        }
        schema <- Try(schemaFactory.getSchema(schemaJson)).toEither.left.map(e =>
          InvalidRequest(s"Could not parse schema: ${e.getMessage}")
        )
        _ <- {
          val errors = schema.validate(dataNode).asScala.toSet
          if (errors.nonEmpty)
            Left(InvalidRequest(s"JSON schema validation error: ${errors.map(_.getMessage).mkString("; ")}"))
          else Right(())
        }
      } yield ()
    }
  }

  def createDnsZoneService(
                            dnsApiKey: String,
                            operation: String,
                            request: Option[String],
                            connection: HttpURLConnection
                          ): Either[Throwable, HttpURLConnection] = {
    try {
      // Map operation to HTTP method
      val method = operation match {
        case "create-zone" => "POST"
        case "update-zone" => "PUT"
        case "delete-zone" => "DELETE"
        case other => throw new IllegalArgumentException(s"Unsupported operation: $other")
      }

      connection.setRequestMethod(method)
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("X-API-Key", dnsApiKey)

      // Only send a body if the HTTP method and request are appropriate
      val methodsWithBody = Set("POST", "PUT", "PATCH")
      if (methodsWithBody.contains(method) && request.isDefined) {
        connection.setDoOutput(true)
        val outputStream = connection.getOutputStream
        try {
          outputStream.write(request.get.getBytes("UTF-8"))
        } finally {
          outputStream.close()
        }
      }

      Right(connection)
    } catch {
      case e: Exception =>
        Left(e)
    }
  }

  def listGeneratedZones(
                          authPrincipal: AuthPrincipal,
                          nameFilter: Option[String] = None,
                          startFrom: Option[String] = None,
                          maxItems: Int = 100,
                          searchByAdminGroup: Boolean = false
                        ): Result[ListGeneratedZonesResponse] = {
    if(!searchByAdminGroup || nameFilter.isEmpty){
      for {
        listZonesResult <- generateZoneRepository.listGenerateZones(
          authPrincipal,
          nameFilter,
          startFrom,
          maxItems
        )
        generatedZones = listZonesResult.generatedZones
        groupIds = generatedZones.map(_.groupId).toSet
        groups <- groupRepository.getGroups(groupIds)
        generateZoneSummaryInfos = generateZoneSummaryInfoMapping(generatedZones, authPrincipal, groups)
      } yield ListGeneratedZonesResponse(
        generateZoneSummaryInfos,
        listZonesResult.zonesFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems
      )}
    else {
      for {
        groupIds <- getGroupsIdsByName(nameFilter.get)
        listZonesResult <- generateZoneRepository.listGeneratedZonesByAdminGroupIds(
          authPrincipal,
          startFrom,
          maxItems,
          groupIds
        )
        generatedZones = listZonesResult.generatedZones
        groups <- groupRepository.getGroups(groupIds)
        generateZoneSummaryInfos = generateZoneSummaryInfoMapping(generatedZones, authPrincipal, groups)
      } yield ListGeneratedZonesResponse(
        generateZoneSummaryInfos,
        nameFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems
      )
    }
  }.toResult

  def allowedDNSProviders(): Result[List[String]] =
    dnsProviderApiConnection.allowedProviders.toResult

  def dnsNameServers(): Result[List[String]] =
    dnsProviderApiConnection.nameServers.toResult

  def generateZoneSummaryInfoMapping(
                              zones: List[GenerateZone],
                              auth: AuthPrincipal,
                              groups: Set[Group]
                            ): List[GenerateZoneSummaryInfo] =
    zones.map { zn =>
      val groupName = groups.find(_.id == zn.groupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      val zoneAccess = getGenerateZoneAccess(auth, zn)
      GenerateZoneSummaryInfo(zn, groupName, zoneAccess)
    }

  private def getGroupsIdsByName(groupName: String): IO[Set[String]] =
    groupRepository.getGroupsByName(groupName).map(x => x.map(_.id))

  // Reject generating a zone whose name matches an existing VinylDNS-managed zone (cross-table
  // check for D6). Mirrors ZoneService.zoneDoesNotExist.
  private def zoneDoesNotExist(zoneName: String): Result[Unit] =
    zoneRepository
      .getZoneByName(zoneName)
      .map {
        case Some(existingZone) if existingZone.status != ZoneStatus.Deleted =>
          ZoneAlreadyExistsError(
            s"Zone with name $zoneName already exists. " +
              s"Please contact ${existingZone.email} to request access to the zone."
          ).asLeft
        case _ => ().asRight
      }
      .toResult

  private def generateZoneDoesNotExist(zoneName: String): Result[Unit] =
    generateZoneRepository
      .getGenerateZoneByName(zoneName)
      .map {
        case Some(existingZone) =>
          ZoneAlreadyExistsError(
            s"Zone with name $zoneName already exists. " +
              s"Please contact ${existingZone.groupId} to request access."
          ).asLeft
        case None =>
          ().asRight
      }
      .toResult

  private def adminGroupExists(groupId: String): Result[Unit] =
    groupRepository
      .getGroup(groupId)
      .map {
        case Some(_) => ().asRight
        case None => InvalidGroupError(s"Admin group with ID $groupId does not exist").asLeft
      }
      .toResult

  private def getGenerateZoneByNameOrFail(zoneName: String): Result[GenerateZone] =
    generateZoneRepository
      .getGenerateZoneByName(zoneName)
      .orFail(ZoneNotFoundError(s"Zone with name $zoneName does not exists"))
      .toResult[GenerateZone]

  private def getGeneratedZoneOrFail(generatedZoneId: String): Result[GenerateZone] =
    generateZoneRepository
      .getGenerateZoneById(generatedZoneId)
      .orFail(ZoneNotFoundError(s"Generated zone with id $generatedZoneId does not exists"))
      .toResult[GenerateZone]
}
