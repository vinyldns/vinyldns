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

package vinyldns.route53.backend

import java.util.UUID
import cats.data.OptionT
import cats.effect.IO
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.route53.{AmazonRoute53Async, AmazonRoute53AsyncClientBuilder}
import com.amazonaws.services.route53.model.{GetHostedZoneRequest, _}
import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult}
import org.slf4j.LoggerFactory
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.backend.{Backend, BackendResponse}
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{RecordSet, RecordSetChange, RecordSetChangeType, RecordType}
import vinyldns.core.domain.zone.{Zone, ZoneStatus}

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * Backend for a single AWS account
  *
  * @param id VinylDNS backend identifier used to connect to route 53
  * @param hostedZones A list of hosted zones, loaded when the application is started.  Necessary
  *                    as most interactions with Route53 go through the zone id, not the zone name.
  *                    This will be used as a cache, and on cache miss will lookup the zone in real time
  * @param client A route 53 client with credentials that can talk to this route 53 aws account
  */
class Route53Backend(
    val id: String,
    hostedZones: List[HostedZone],
    val client: AmazonRoute53Async
) extends Backend
    with Route53Conversions {
  import Route53Backend.r53

  private val logger = LoggerFactory.getLogger(classOf[Route53Backend])

  /* Concurrent friendly map */
  private val zoneMap: TrieMap[String, String] = TrieMap(
    hostedZones.map(z => z.getName -> z.getId): _*
  )

  /* Lookup in the local cache, if a new zone is added since start, we have to retrieve it in real time */
  private def lookupHostedZone(zoneName: String): OptionT[IO, String] = {
    // pain but we must parse to use the hosted zone ids from the cache
    def parseHostedZoneId(hzid: String): String = {
      val lastSlash = hzid.lastIndexOf('/')
      if (lastSlash > 0) {
        hzid.substring(lastSlash + 1)
      } else {
        hzid
      }
    }
    OptionT.fromOption[IO](zoneMap.get(zoneName)).orElseF {
      r53(
        new ListHostedZonesByNameRequest().withDNSName(zoneName),
        client.listHostedZonesByNameAsync
      ).map { result =>
        // We must parse the hosted zone id which is annoying
        val found = result.getHostedZones.asScala.toList.headOption.map { hz =>
          val hzid = parseHostedZoneId(hz.getId)

          // adds the hosted zone name and id to our cache if not present
          zoneMap.putIfAbsent(hz.getName, hzid)
          hzid
        }
        if (found.isEmpty) {
          logger.warn(s"Unable to find hosted zone for '$zoneName'")
        }
        found
      }
    }
  }

  /**
    * Does a lookup for a record given the record name, zone name, and record type
    *
    * The record name + zone name should form the FQDN
    *
    * @param name     The name of the record (without the zone - e.g. www)
    * @param zoneName The full domain name (e.g. example.com)
    * @param typ      The type of record (e.g. AAAA)
    * @return A list of record sets matching the name, empty if not found
    */
  def resolve(name: String, zoneName: String, typ: RecordType): IO[List[RecordSet]] = {
    val fqdn = Fqdn.merge(name, zoneName).fqdn
    def filterResourceRecordSet(
        rrs: java.util.List[ResourceRecordSet],
        rrType: RRType
    ): java.util.List[ResourceRecordSet] =
      rrs.asScala.filter { r =>
        r.getName == fqdn && RRType.fromValue(r.getType) == rrType
      }.asJava
    for {
      hostedZoneId <- lookupHostedZone(zoneName)
      awsRRType <- OptionT.fromOption[IO](toRoute53RecordType(typ))
      result <- OptionT.liftF {
        r53(
          new ListResourceRecordSetsRequest()
            .withHostedZoneId(hostedZoneId)
            .withStartRecordName(fqdn)
            .withStartRecordType(awsRRType),
          client.listResourceRecordSetsAsync
        )
      }
    } yield toVinylRecordSets(
      filterResourceRecordSet(result.getResourceRecordSets, awsRRType),
      zoneName: String
    )
  }.getOrElse(Nil)

  /**
    * Applies a single record set change against the DNS backend
    *
    * @param change A RecordSetChange to apply.  Note: the key for a record set is the record name + type.
    *               A single RecordSetChange can add or remove multiple individual records in a record set at one time.
    * @return A BackendResponse that is backend provider specific
    */
  def applyChange(change: RecordSetChange): IO[BackendResponse] = {
    def changeAction(typ: RecordSetChangeType): ChangeAction = typ match {
      case RecordSetChangeType.Create => ChangeAction.CREATE
      case RecordSetChangeType.Update => ChangeAction.UPSERT
      case RecordSetChangeType.Delete => ChangeAction.DELETE
    }

    def changeRequest(
        typ: RecordSetChangeType,
        rs: ResourceRecordSet
    ): ChangeResourceRecordSetsRequest = {
      logger.debug(s"Applying change to zone, record set is $rs")
      new ChangeResourceRecordSetsRequest().withChangeBatch(
        new ChangeBatch().withChanges(
          new Change().withAction(changeAction(typ)).withResourceRecordSet(rs)
        )
      )
    }

    // We want to FAIL if unrecoverable errors occur so that the change ultimately is marked as failed
    for {
      hostedZoneId <- lookupHostedZone(change.zone.name).value.flatMap {
        case Some(x) => IO(x)
        case None =>
          IO.raiseError(
            Route53BackendResponse.ZoneNotFoundError(
              s"Unable to find hosted zone for zone name ${change.zone.name}"
            )
          )
      }

      r53RecordSet <- IO.fromOption(toR53RecordSet(change.zone, change.recordSet))(
        Route53BackendResponse.ConversionError(
          s"Unable to convert record set to route 53 format for ${change.recordSet}"
        )
      )

      result <- r53(
        changeRequest(change.changeType, r53RecordSet).withHostedZoneId(hostedZoneId),
        client.changeResourceRecordSetsAsync
      ).map { response =>
        logger.debug(s"Applied record change $change, change result is ${response.getChangeInfo}")
        BackendResponse.NoError(response.toString)
      }
    } yield result
  }

  /**
    * Loads all record sets in a zone.  Used typically for zone syncs.
    *
    * Note, this will cause memory issues for large zones (100,000s of records).  Need to make
    * zone sync memory safe before changing this
    *
    * @param zone        The zone to load
    * @param maxZoneSize The maximum number of records that we allow loading, typically configured
    * @return All record sets in the zone
    */
  def loadZone(zone: Zone, maxZoneSize: Int): IO[List[RecordSet]] = {
    // Loads a single page, up to 100 record sets
    def loadPage(request: ListResourceRecordSetsRequest): IO[ListResourceRecordSetsResult] =
      r53(
        request,
        client.listResourceRecordSetsAsync
      )

    // recursively pages through, exits once we hit the last page
    def recurseLoadNextPage(
        request: ListResourceRecordSetsRequest,
        result: ListResourceRecordSetsResult,
        acc: List[RecordSet]
    ): IO[List[RecordSet]] = {
      val updatedAcc = acc ++ toVinylRecordSets(result.getResourceRecordSets, zone.name, zone.id)

      // Here is our base case right here, getIsTruncated returns true if there are more records
      if (result.getIsTruncated) {
        loadPage(
          request
            .withStartRecordName(result.getNextRecordName)
            .withStartRecordType(result.getNextRecordType)
        ).flatMap(nextResult => recurseLoadNextPage(request, nextResult, updatedAcc))
      } else {
        IO(updatedAcc)
      }
    }

    for {
      hz <- lookupHostedZone(zone.name)
      recordSets <- OptionT.liftF {
        val req = new ListResourceRecordSetsRequest().withHostedZoneId(hz)

        // recurse to load all pages
        loadPage(req).flatMap(recurseLoadNextPage(req, _, Nil))
      }

      // get the delegation set so we can load the name server records if we do not have any
      fabbedPrivateZoneNsRecordSets <- if (recordSets.exists(_.typ == RecordType.NS)) {
        OptionT.pure[IO](List.empty[RecordSet])
      } else
        OptionT.liftF {
          r53(new GetHostedZoneRequest().withId(hz), client.getHostedZoneAsync)
            .map(_.getDelegationSet)
            .map(ds => List(toVinylNSRecordSet(ds, zone.name, zone.id)))
        }
    } yield recordSets ++ fabbedPrivateZoneNsRecordSets
  }.getOrElse(Nil)

  /**
    * Indicates if the zone is present in the backend
    *
    * @param zone The zone to check if exists
    * @return true if it exists; false otherwise
    */
  def zoneExists(zone: Zone): IO[Boolean] = lookupHostedZone(zone.name).isDefined

  /* Note: naive implementation to assist in testing, not meant for production yet */
  def createZone(zone: Zone): IO[Zone] =
    for {
      result <- r53(
        new CreateHostedZoneRequest().withCallerReference(zone.id).withName(zone.name),
        client.createHostedZoneAsync
      )
      _ <- IO(logger.info(s"create zone result is $result"))
    } yield zone.copy(status = ZoneStatus.Active)
}

object Route53Backend {

  /* Convenience method for working async with AWS */
  def r53[A <: AmazonWebServiceRequest, B <: AmazonWebServiceResult[_]](
      request: A,
      f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B]
  ): IO[B] =
    IO.async[B] { complete: (Either[Throwable, B] => Unit) =>
      val asyncHandler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit = complete(Left(exception))

        def onSuccess(request: A, result: B): Unit = complete(Right(result))
      }

      f(request, asyncHandler)
    }

  // Loads a Route53 backend
  def load(config: Route53BackendConfig): IO[Route53Backend] = {
    val clientIO = IO {
      val r53ClientBuilder = AmazonRoute53AsyncClientBuilder.standard
      r53ClientBuilder.withEndpointConfiguration(
        new EndpointConfiguration(config.serviceEndpoint, config.signingRegion)
      )

      val r53CredBuilder = Route53Credentials.builder
      for {
        accessKey <- config.accessKey
        secretKey <- config.secretKey
      } r53CredBuilder.basicCredentials(accessKey, secretKey)

      for (role <- config.roleArn) {
        config.externalId match {
          case Some(externalId) =>
            r53CredBuilder.withRole(role, UUID.randomUUID().toString, externalId)
          case None => r53CredBuilder.withRole(role, UUID.randomUUID().toString)
        }
      }

      val credProvider = r53CredBuilder.build().provider

      r53ClientBuilder.withCredentials(credProvider).build()
    }

    // Connect to the client AND load the zones
    for {
      client <- clientIO
      result <- r53(
        new ListHostedZonesRequest(),
        client.listHostedZonesAsync
      )
    } yield new Route53Backend(config.id, result.getHostedZones.asScala.toList, client)
  }
}
