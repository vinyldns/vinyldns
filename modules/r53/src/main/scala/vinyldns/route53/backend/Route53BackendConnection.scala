package vinyldns.route53.backend

import cats.data.OptionT
import cats.effect.IO
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.route53.AmazonRoute53AsyncClient
import com.amazonaws.services.route53.model._
import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult}
import vinyldns.core.domain.backend.{BackendConnection, BackendResponse}
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{RecordSet, RecordSetChange, RecordSetChangeType}
import vinyldns.core.domain.zone.Zone

import scala.collection.concurrent.TrieMap

/**
  * Connects to the backend for a single AWS account
  *
  * @param id VinylDNS backend identifier used to connect to route 53
  * @param hostedZones A list of hosted zones, loaded when the application is started.  Necessary
  *                    as most interactions with Route53 go through the zone id, not the zone name.
  *                    This will be used as a cache, and on cache miss will lookup the zone in real time
  * @param client A route 53 client with credentials that can talk to this route 53 aws account
  */
class Route53BackendConnection(
    val id: String,
    hostedZones: List[HostedZone],
    client: AmazonRoute53AsyncClient
) extends BackendConnection
    with Route53Conversions {

  /* Concurrent friendly map */
  private val zoneMap: TrieMap[String, String] = TrieMap(
    hostedZones.map(z => z.getName -> z.getId): _*
  )

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

  /* Lookup in the local cache, if a new zone is added since start, we have to retrieve it in real time */
  private def lookupHostedZone(zoneName: String): OptionT[IO, String] =
    OptionT.fromOption[IO](zoneMap.get(zoneName)).orElseF {
      r53(
        new ListHostedZonesByNameRequest().withDNSName(zoneName),
        client.listHostedZonesByNameAsync
      ).map(result => Option(result.getHostedZoneId))
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
    for {
      hostedZoneId <- lookupHostedZone(zoneName)
      awsRRType <- OptionT.fromOption[IO](toRoute53RecordType(typ))
      result <- OptionT.liftF {
        r53(
          new ListResourceRecordSetsRequest()
            .withHostedZoneId(hostedZoneId)
            .withStartRecordName(name)
            .withStartRecordType(awsRRType),
          client.listResourceRecordSetsAsync
        )
      }
    } yield toVinylRecordSets(result.getResourceRecordSets)
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
    ): ChangeResourceRecordSetsRequest =
      new ChangeResourceRecordSetsRequest().withChangeBatch(
        new ChangeBatch().withChanges(
          new Change().withAction(changeAction(typ)).withResourceRecordSet(rs)
        )
      )

    for {
      hostedZoneId <- lookupHostedZone(change.zone.name).value.flatMap {
        case Some(x) => IO(x)
        case None =>
          IO.raiseError(
            new RuntimeException(s"Unable to find hosted zone for zone name ${change.zone.name}")
          )
      }

      r53RecordSet <- IO.fromOption(toR53RecordSet(change.recordSet))(
        new RuntimeException(
          s"Unable to convert record set to route 53 format for ${change.recordSet}"
        )
      )

      _ <- r53(
        changeRequest(change.changeType, r53RecordSet).withHostedZoneId(hostedZoneId),
        client.changeResourceRecordSetsAsync
      )
    } yield Route53Response.NoError
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
  def loadZone(zone: Zone, maxZoneSize: Int): IO[List[RecordSet]] = ???
}
