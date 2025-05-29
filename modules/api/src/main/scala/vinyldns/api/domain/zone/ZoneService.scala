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
import vinyldns.api.domain.access.AccessValidationsAlgebra
import vinyldns.api.Interfaces
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{Group, GroupRepository, ListUsersResults, User, UserRepository}
import vinyldns.core.domain.zone.{ZoneCommandResult, _}
import vinyldns.core.queue.MessageQueue
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.backend.BackendResolver
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import com.cronutils.model.CronType
import org.slf4j.LoggerFactory
import vinyldns.api.domain.membership.MembershipService
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.networknt.schema.{JsonSchemaFactory, SpecVersion}
import org.json4s.{JValue, JObject}
import scala.util.Try
import scala.jdk.CollectionConverters._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.{HttpURLConnection, URL}
import scala.io.Source

object ZoneService {
  def apply(
      dataAccessor: ApiDataAccessor,
      connectionValidator: ZoneConnectionValidatorAlgebra,
      messageQueue: MessageQueue,
      zoneValidations: ZoneValidations,
      accessValidation: AccessValidationsAlgebra,
      backendResolver: BackendResolver,
      crypto: CryptoAlgebra,
      membershipService:MembershipService,
      dnsProviderApiConnection : DnsProviderApiConnection
           ): ZoneService =
    new ZoneService(
      dataAccessor.zoneRepository,
      dataAccessor.groupRepository,
      dataAccessor.userRepository,
      dataAccessor.zoneChangeRepository,
      connectionValidator,
      messageQueue,
      zoneValidations,
      accessValidation,
      backendResolver,
      crypto,
      membershipService,
      dnsProviderApiConnection,
      dataAccessor.generateZoneRepository
    )
}

class ZoneService(
    zoneRepository: ZoneRepository,
    groupRepository: GroupRepository,
    userRepository: UserRepository,
    zoneChangeRepository: ZoneChangeRepository,
    connectionValidator: ZoneConnectionValidatorAlgebra,
    messageQueue: MessageQueue,
    zoneValidations: ZoneValidations,
    accessValidation: AccessValidationsAlgebra,
    backendResolver: BackendResolver,
    crypto: CryptoAlgebra,
    membershipService: MembershipService,
    dnsProviderApiConnection: DnsProviderApiConnection,
    generateZoneRepository: GenerateZoneRepository

                 ) extends ZoneServiceAlgebra {

  import accessValidation._
  import zoneValidations._
  import Interfaces._

  private val logger = LoggerFactory.getLogger(classOf[ZoneService])

  def connectToZone(
      ConnectZoneInput: ConnectZoneInput,
      auth: AuthPrincipal
  ): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(ConnectZoneInput.acl).toResult
      _ <- membershipService.emailValidation(ConnectZoneInput.email)
      _ <- connectionValidator.isValidBackendId(ConnectZoneInput.backendId).toResult
      _ <- validateSharedZoneAuthorized(ConnectZoneInput.shared, auth.signedInUser).toResult
      _ <- zoneDoesNotExist(ConnectZoneInput.name)
      _ <- adminGroupExists(ConnectZoneInput.adminGroupId)
      _ <- if(ConnectZoneInput.recurrenceSchedule.isDefined) canScheduleZoneSync(auth).toResult else IO.unit.toResult
      isCronStringValid = if(ConnectZoneInput.recurrenceSchedule.isDefined) isValidCronString(ConnectZoneInput.recurrenceSchedule.get) else true
      _ <- validateCronString(isCronStringValid).toResult
      _ <- canChangeZone(auth, ConnectZoneInput.name, ConnectZoneInput.adminGroupId).toResult
      createdZoneInput = if(ConnectZoneInput.recurrenceSchedule.isDefined) ConnectZoneInput.copy(scheduleRequestor = Some(auth.signedInUser.userName)) else ConnectZoneInput
      zoneToCreate = Zone(createdZoneInput, auth.isTestUser)
      _ <- connectionValidator.validateZoneConnections(zoneToCreate)
      createZoneChange <- ZoneChangeGenerator.forAdd(zoneToCreate, auth).toResult
      _ <- messageQueue.send(createZoneChange).toResult[Unit]
    } yield createZoneChange

  def updateZone(updateZoneInput: UpdateZoneInput, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      _ <- isValidZoneAcl(updateZoneInput.acl).toResult
      _ <- membershipService.emailValidation(updateZoneInput.email)
      _ <- connectionValidator.isValidBackendId(updateZoneInput.backendId).toResult
      existingZone <- getZoneOrFail(updateZoneInput.id)
      _ <- validateSharedZoneAuthorized(
        existingZone.shared,
        updateZoneInput.shared,
        auth.signedInUser
      ).toResult
      _ <- canChangeZone(auth, existingZone.name, existingZone.adminGroupId).toResult
      _ <- if(updateZoneInput.recurrenceSchedule.isDefined) canScheduleZoneSync(auth).toResult else IO.unit.toResult
      isCronStringValid = if(updateZoneInput.recurrenceSchedule.isDefined) isValidCronString(updateZoneInput.recurrenceSchedule.get) else true
      _ <- validateCronString(isCronStringValid).toResult
      _ <- adminGroupExists(updateZoneInput.adminGroupId)
      // if admin group changes, this confirms user has access to new group
      _ <- canChangeZone(auth, updateZoneInput.name, updateZoneInput.adminGroupId).toResult
      updatedZoneInput = if(updateZoneInput.recurrenceSchedule.isDefined) updateZoneInput.copy(scheduleRequestor = Some(auth.signedInUser.userName)) else updateZoneInput
      zoneWithUpdates = Zone(updatedZoneInput, existingZone)
      _ <- validateZoneConnectionIfChanged(zoneWithUpdates, existingZone)
      updateZoneChange <- ZoneChangeGenerator
        .forUpdate(zoneWithUpdates, existingZone, auth, crypto)
        .toResult
      _ <- messageQueue.send(updateZoneChange).toResult[Unit]
    } yield updateZoneChange

  def deleteZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone.name, zone.adminGroupId).toResult
      deleteZoneChange <- ZoneChangeGenerator.forDelete(zone, auth).toResult
      _ <- messageQueue.send(deleteZoneChange).toResult[Unit]
    } yield deleteZoneChange

  def getGenerateZoneByName(zoneName: String, auth: AuthPrincipal): Result[GenerateZone] =
    for {
      generateZone <- getGenerateZoneByNameOrFail(ensureTrailingDot(zoneName))
    } yield generateZone


  private def createConnection(apiUrl: String): HttpURLConnection = {
   new URL(apiUrl).openConnection().asInstanceOf[HttpURLConnection]
  }

  private def schemaValidationResult(
                                      providerConfig: DnsProviderConfig,
                                      operation: String,
                                      params: Map[String, JValue]
                                    ): Result[Unit] = providerConfig.schemas.get(operation) match {
    case Some(schema) => JsonSchemaValidator.validate(schema, params).toResult
    case None => result(())
  }

  private def existenceCheck(operation: String, zoneName: String): Result[Unit] = operation match {
    case "create-zone" => generateZoneDoesNotExist(zoneName).toResult
    case "delete-zone" => generateZoneExists(zoneName).toResult
    case "update-zone" => generateZoneExists(zoneName).toResult
    case _ => Left(InvalidRequest(s"Unsupported operation: $operation")).toResult
  }

  private def executeRepoAction(
                                 operation: String,
                                 request: ZoneGenerationInput,
                                 response: ZoneGenerationResponse
                               ): Result[Unit] = {
    val zoneToGenerate = GenerateZone(request)
    operation match {
      case "delete-zone" => generateZoneRepository.deleteTx(zoneToGenerate).toResult
      case "create-zone" => generateZoneRepository.save(zoneToGenerate.copy(response = Some(response))).toResult
      case "update-zone" => generateZoneRepository.save(zoneToGenerate.copy(response = Some(response))).toResult
      case _ => Left(InvalidRequest(s"Unsupported operation: $operation")).toResult
    }
  }
  def handleGenerateZoneRequest(
                                 operation: String,
                                 request: ZoneGenerationInput,
                                 auth: AuthPrincipal
                               ): Result[ZoneGenerationResponse] = {
    for {
      // Validate input
      providerConfig <- validateProvider(request.provider, dnsProviderApiConnection.providers).toResult
      _ <- validateZoneName(request.zoneName).toResult
      _ <- schemaValidationResult(providerConfig, operation, request.providerParams)

      _ <- logger.info(s"Request providerParams: ${request.providerParams}").toResult

      // Build request and endpoint
      endpoint = buildGenerateZoneEndpoint(providerConfig.endpoints(operation), request)
      requestJsonOpt = buildGenerateZoneRequestJson(providerConfig.requestTemplates.get(operation), request)

      // Authorization and existence checks
      _ <- canChangeZone(auth, request.zoneName, request.groupId).toResult
      _ <- existenceCheck(operation, request.zoneName)

      // Send request
      _ <- logger.info(s"Request: provider=${request.provider}, path=$endpoint, request=$requestJsonOpt").toResult
      dnsProviderConn <- createConnection(endpoint).toResult
      dnsConnResponse <- createDnsZoneService(providerConfig.apiKey, operation, requestJsonOpt, dnsProviderConn).toResult

      // Process response
      responseCode = dnsConnResponse.getResponseCode
      _ <- logger.info(s"response code: $responseCode").toResult
      inputStream = if (responseCode >= 400) dnsConnResponse.getErrorStream else dnsConnResponse.getInputStream
      responseMessage: String = Source.fromInputStream(inputStream, "UTF-8").mkString
      _ <- isValidGenerateZoneConn(responseCode, responseMessage).toResult

      // Only parse JSON if the response is non-empty
      responseJson = if (responseMessage.nonEmpty) parse(responseMessage) else JNothing

      // Create response object
      zoneGenerateResponse = ZoneGenerationResponse(
        provider = request.provider,
        responseCode = responseCode,
        status = dnsConnResponse.getResponseMessage,
        message = responseJson
      )
      _ <- logger.info(s"response: $zoneGenerateResponse").toResult

      _ <- executeRepoAction(operation, request, zoneGenerateResponse)

    } yield zoneGenerateResponse
  }

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

  private def buildGenerateZoneEndpoint(
                                         endpointTemplate: String,
                                         zoneGenerationInput: ZoneGenerationInput
                                       ): String = {
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

    TemplateEngine.substituteEndpoint(endpointTemplate, baseParams ++ providerParams)
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
                          searchByAdminGroup: Boolean = false,
                          ignoreAccess: Boolean = false
                        ): Result[ListGeneratedZonesResponse] = {
    if(!searchByAdminGroup || nameFilter.isEmpty){
      for {
        listZonesResult <- generateZoneRepository.listGenerateZones(
          authPrincipal,
          nameFilter,
          startFrom,
          maxItems,
          ignoreAccess
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
        listZonesResult.maxItems,
        listZonesResult.ignoreAccess
      )}
    else {
      for {
        groupIds <- getGroupsIdsByName(nameFilter.get)
        listZonesResult <- generateZoneRepository.listGeneratedZonesByAdminGroupIds(
          authPrincipal,
          startFrom,
          maxItems,
          groupIds,
          ignoreAccess
        )
        generatedZones = listZonesResult.generatedZones
        groups <- groupRepository.getGroups(groupIds)
        generateZoneSummaryInfos = generateZoneSummaryInfoMapping(generatedZones, authPrincipal, groups)
      } yield ListGeneratedZonesResponse(
        generateZoneSummaryInfos,
        nameFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems,
        listZonesResult.ignoreAccess
      )
    }
  }.toResult

  def allowedDNSProviders(): Result[List[String]] =
    dnsProviderApiConnection.allowedProviders.toResult

  def dnsNameServers(): Result[List[String]] =
    dnsProviderApiConnection.nameServers.toResult

  def syncZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(auth, zone.name, zone.adminGroupId).toResult
      _ <- outsideSyncDelay(zone).toResult
      syncZoneChange <- ZoneChangeGenerator.forSync(zone, auth).toResult
      _ <- messageQueue.send(syncZoneChange).toResult[Unit]
    } yield syncZoneChange

  def getZone(zoneId: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZone(auth, zone).toResult
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
      accessLevel = getZoneAccess(auth, zone)
    } yield ZoneInfo(zone, aclInfo, groupName, accessLevel)

  def getCommonZoneDetails(zoneId: String, auth: AuthPrincipal): Result[ZoneDetails] =
    for {
      zone <- getZoneOrFail(zoneId)
      groupName <- getGroupName(zone.adminGroupId)
    } yield ZoneDetails(zone, groupName)

  def getZoneByName(zoneName: String, auth: AuthPrincipal): Result[ZoneInfo] =
    for {
      zone <- getZoneByNameOrFail(ensureTrailingDot(zoneName))
      aclInfo <- getZoneAclDisplay(zone.acl)
      groupName <- getGroupName(zone.adminGroupId)
      accessLevel = getZoneAccess(auth, zone)
    } yield ZoneInfo(zone, aclInfo, groupName, accessLevel)

  // List zones. Uses zone name as default while using search to list zones or by admin group name if selected.
  def listZones(
      authPrincipal: AuthPrincipal,
      nameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      searchByAdminGroup: Boolean = false,
      ignoreAccess: Boolean = false,
      includeReverse: Boolean = true
  ): Result[ListZonesResponse] = {
    if(!searchByAdminGroup || nameFilter.isEmpty){
      for {
        listZonesResult <- zoneRepository.listZones(
          authPrincipal,
          nameFilter,
          startFrom,
          maxItems,
          ignoreAccess,
          includeReverse
      )
      zones = listZonesResult.zones
      groupIds = zones.map(_.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      zoneSummaryInfos = zoneSummaryInfoMapping(zones, authPrincipal, groups)
    } yield ListZonesResponse(
      zoneSummaryInfos,
      listZonesResult.zonesFilter,
      listZonesResult.startFrom,
      listZonesResult.nextId,
      listZonesResult.maxItems,
      listZonesResult.ignoreAccess,
      listZonesResult.includeReverse
    )}
    else {
      for {
        groupIds <- getGroupsIdsByName(nameFilter.get)
        listZonesResult <- zoneRepository.listZonesByAdminGroupIds(
          authPrincipal,
          startFrom,
          maxItems,
          groupIds,
          ignoreAccess,
          includeReverse
        )
        zones = listZonesResult.zones
        groups <- groupRepository.getGroups(groupIds)
        zoneSummaryInfos = zoneSummaryInfoMapping(zones, authPrincipal, groups)
      } yield ListZonesResponse(
        zoneSummaryInfos,
        nameFilter,
        listZonesResult.startFrom,
        listZonesResult.nextId,
        listZonesResult.maxItems,
        listZonesResult.ignoreAccess,
        listZonesResult.includeReverse
      )
    }
  }.toResult

  def listDeletedZones(
                        authPrincipal: AuthPrincipal,
                        nameFilter: Option[String] = None,
                        startFrom: Option[String] = None,
                        maxItems: Int = 100,
                        ignoreAccess: Boolean = false
                      ): Result[ListDeletedZoneChangesResponse] = {
    for {
      listZonesChangeResult <- zoneChangeRepository.listDeletedZones(
        authPrincipal,
        nameFilter,
        startFrom,
        maxItems,
        ignoreAccess
      )
      zoneChanges = listZonesChangeResult.zoneDeleted
      groupIds = zoneChanges.map(_.zone.adminGroupId).toSet
      groups <- groupRepository.getGroups(groupIds)
      userId = zoneChanges.map(_.userId).toSet
      users <- userRepository.getUsers(userId,None,None)
      zoneDeleteSummaryInfos = ZoneChangeDeletedInfoMapping(zoneChanges, authPrincipal, groups, users)
    } yield {
      ListDeletedZoneChangesResponse(
        zoneDeleteSummaryInfos,
        listZonesChangeResult.zoneChangeFilter,
        listZonesChangeResult.nextId,
        listZonesChangeResult.startFrom,
        listZonesChangeResult.maxItems,
        listZonesChangeResult.ignoreAccess
      )
    }
  }.toResult

  private def ZoneChangeDeletedInfoMapping(
                                            zoneChange: List[ZoneChange],
                                            auth: AuthPrincipal,
                                            groups: Set[Group],
                                            users: ListUsersResults
                                          ): List[ZoneChangeDeletedInfo] =
    zoneChange.map { zc =>
      val groupName = groups.find(_.id == zc.zone.adminGroupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      val userName = users.users.find(_.id == zc.userId) match {
        case Some(user) => user.userName
        case None => "Unknown user name"
      }
      val zoneAccess = getZoneAccess(auth, zc.zone)
      ZoneChangeDeletedInfo(zc, groupName,userName, zoneAccess)
    }

  def zoneSummaryInfoMapping(
      zones: List[Zone],
      auth: AuthPrincipal,
      groups: Set[Group]
  ): List[ZoneSummaryInfo] =
    zones.map { zn =>
      val groupName = groups.find(_.id == zn.adminGroupId) match {
        case Some(group) => group.name
        case None => "Unknown group name"
      }
      val zoneAccess = getZoneAccess(auth, zn)
      ZoneSummaryInfo(zn, groupName, zoneAccess)
    }

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

  def listZoneChanges(
      zoneId: String,
      authPrincipal: AuthPrincipal,
      startFrom: Option[String] = None,
      maxItems: Int = 100
  ): Result[ListZoneChangesResponse] =
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canSeeZoneChange(authPrincipal, zone).toResult
      zoneChangesResults <- zoneChangeRepository
        .listZoneChanges(zone.id, startFrom, maxItems)
        .toResult[ListZoneChangesResults]
    } yield ListZoneChangesResponse(zone.id, zoneChangesResults)

  def listFailedZoneChanges(
                             authPrincipal: AuthPrincipal,
                             startFrom: Int= 0,
                             maxItems: Int = 100
                           ): Result[ListFailedZoneChangesResponse] =
    for {
      zoneChangesFailedResults <- zoneChangeRepository
        .listFailedZoneChanges(maxItems, startFrom)
        .toResult[ListFailedZoneChangesResults]
      _ <- zoneAccess(zoneChangesFailedResults.items, authPrincipal).toResult
    } yield
      ListFailedZoneChangesResponse(
        zoneChangesFailedResults.items,
        zoneChangesFailedResults.nextId,
        startFrom,
        maxItems
      )

  def zoneAccess(
                  zoneCh: List[ZoneChange],
                  auth: AuthPrincipal
                ): List[Result[Unit]] =
    zoneCh.map { zn =>
      canSeeZone(auth, zn.zone).toResult
    }

  def addACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      _ <- isValidAclRule(newRule).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.addACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal,
          crypto
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def deleteACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult] = {
    val newRule = ACLRule(aclRuleInfo)
    for {
      zone <- getZoneOrFail(zoneId)
      _ <- canChangeZone(authPrincipal, zone.name, zone.adminGroupId).toResult
      zoneChange <- ZoneChangeGenerator
        .forUpdate(
          newZone = zone.deleteACLRule(newRule),
          oldZone = zone,
          authPrincipal = authPrincipal,
          crypto
        )
        .toResult
      _ <- messageQueue.send(zoneChange).toResult[Unit]
    } yield zoneChange
  }

  def getGroupsIdsByName(groupName: String): IO[Set[String]] = {
    groupRepository.getGroupsByName(groupName).map(x => x.map(_.id))
  }

  def getBackendIds(): Result[List[String]] =
    backendResolver.ids.toList.toResult

  def isValidCronString(maybeString: String): Boolean = {
    val isValid = try {
      val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
      val parser: CronParser = new CronParser(cronDefinition)
      val quartzCron = parser.parse(maybeString)
      quartzCron.validate
      true
    }
    catch {
      case _: Exception =>
        false
    }
    isValid
  }

  def validateCronString(isValid: Boolean): Either[Throwable, Unit] =
    ensuring(
      InvalidRequest("Invalid cron expression. Please enter a valid cron expression in 'recurrenceSchedule'.")
    )(
      isValid
    )

  def zoneDoesNotExist(zoneName: String): Result[Unit] =
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

  private def generateZoneDoesNotExist(zoneName: String): Either[Throwable, Unit] = {
    val existingZoneOpt: Option[GenerateZone] =
      generateZoneRepository.getGenerateZoneByName(zoneName).unsafeRunSync()

    existingZoneOpt match {
      case Some(existingZone) =>
        Left(ZoneAlreadyExistsError(
          s"Zone with name $zoneName already exists. " +
            s"Please contact ${existingZone.groupId} to request access."
        ))
      case None =>
        Right(())
    }
  }

  private def generateZoneExists(zoneName: String): Either[Throwable, Unit] = {
    val existingZoneOpt: Option[GenerateZone] =
      generateZoneRepository.getGenerateZoneByName(zoneName).unsafeRunSync()

    existingZoneOpt match {
      case Some(_) =>
        Right(())
      case None =>
        Left(ZoneNotFoundError(
          s"Zone with name $zoneName does not exist."
        ))
    }
  }

  def canScheduleZoneSync(auth: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User '${auth.signedInUser.userName}' is not authorized to schedule zone sync in this zone.")
    )(
      auth.isSystemAdmin
    )

  def adminGroupExists(groupId: String): Result[Unit] =
    groupRepository
      .getGroup(groupId)
      .map {
        case Some(_) => ().asRight
        case None => InvalidGroupError(s"Admin group with ID $groupId does not exist").asLeft
      }
      .toResult

  def getGroupName(groupId: String): Result[String] = {
    groupRepository.getGroup(groupId).map {
      case Some(group) => group.name
      case None => "Unknown group name"
    }
  }.toResult

  def getZoneOrFail(zoneId: String): Result[Zone] =
    zoneRepository
      .getZone(zoneId)
      .orFail(ZoneNotFoundError(s"Zone with id $zoneId does not exists"))
      .toResult[Zone]

  def getZoneByNameOrFail(zoneName: String): Result[Zone] =
    zoneRepository
      .getZoneByName(zoneName)
      .orFail(ZoneNotFoundError(s"Zone with name $zoneName does not exists"))
      .toResult[Zone]

  def getGenerateZoneByNameOrFail(zoneName: String): Result[GenerateZone] =
    generateZoneRepository
      .getGenerateZoneByName(zoneName)
      .orFail(ZoneNotFoundError(s"Zone with name $zoneName does not exists"))
      .toResult[GenerateZone]

  def validateZoneConnectionIfChanged(newZone: Zone, existingZone: Zone): Result[Unit] =
    if (newZone.connection != existingZone.connection
      || newZone.transferConnection != existingZone.transferConnection) {
      connectionValidator.validateZoneConnections(newZone)
    } else {
      ().toResult
    }

  def getZoneAclDisplay(zoneAcl: ZoneACL): Result[ZoneACLInfo] = {
    val (withUserId, without) = zoneAcl.rules.partition(_.userId.isDefined)
    val (withGroupId, _) = without.partition(_.groupId.isDefined)

    val userIdsToFetch = withUserId.map(_.userId.get)
    val groupIdsToFetch = withGroupId.map(_.groupId.get)

    for {
      users <- userRepository.getUsers(userIdsToFetch, None, None).map(_.users).toResult[Seq[User]]
      groups <- groupRepository.getGroups(groupIdsToFetch).toResult[Set[Group]]
      groupMap = groups.map(g => (g.id, g.name)).toMap
      userMap = users.map(u => (u.id, u.userName)).toMap
      ruleInfos = zoneAcl.rules.map { rule =>
        (rule.userId, rule.groupId) match {
          case (Some(uid), _) => ACLRuleInfo(rule, userMap.get(uid))
          case (_, Some(gid)) => ACLRuleInfo(rule, groupMap.get(gid))
          case _ => ACLRuleInfo(rule, Some("All Users"))
        }
      }
    } yield ZoneACLInfo(ruleInfos.filter(_.displayName.isDefined))
  }
}
