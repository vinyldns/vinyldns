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

package vinyldns.api.domain.dns

import cats.effect._
import cats.syntax.all._
import org.slf4j.{Logger, LoggerFactory}
import org.xbill.DNS
import vinyldns.api.Interfaces.{result, _}
import vinyldns.api.crypto.Crypto
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{RecordSet, RecordSetChange, RecordSetChangeType}
import vinyldns.core.domain.zone.{Zone, ZoneConnection}

object DnsProtocol {

  sealed trait DnsRequest
  final case class Apply(change: RecordSetChange) extends DnsRequest

  // TODO: Remove origin once we change to using Zone Activation
  case class Resolve(name: String, zone: Zone, typ: RecordType)
  case class UpdateConnection(zoneConnection: ZoneConnection)

  sealed trait DnsResponse
  final case class NoError(message: DNS.Message) extends DnsResponse

  abstract class DnsFailure(message: String) extends Throwable(message)
  case class InvalidRecord(message: String) extends DnsFailure(message)
  case class BadKey(message: String)
      extends DnsFailure(s"The dns key provided is invalid: $message")
  case class BadMode(message: String) extends DnsFailure(s"The mode is invalid: $message")
  case class BadSig(message: String)
      extends DnsFailure(s"The signature on the dns key is invalid: $message")
  case class BadTime(message: String) extends DnsFailure(s"The time is out of range: $message")
  case class FormatError(message: String)
      extends DnsFailure(s"Format Error: the server was unable to interpret the query: $message")
  case class NotAuthorized(message: String)
      extends DnsFailure(s"The requestor is not authorized to perform this operation: $message")
  case class NotImplemented(message: String)
      extends DnsFailure(s"The operation requested is not implemented on this server: $message")
  case class NotZone(message: String)
      extends DnsFailure(s"The zone specified in the query is not a zone: $message")
  case class NameNotFound(message: String) extends DnsFailure(s"The name does not exist: $message")
  case class RecordSetNotFound(message: String)
      extends DnsFailure(s"The record set (name, type) does not exist: $message")
  case class Refused(message: String)
      extends DnsFailure(s"The operation was refused by the server: $message")
  case class ServerFailure(message: String) extends DnsFailure(s"Server failure: $message")
  case class NameExists(message: String) extends DnsFailure(s"The name specified exists: $message")
  case class RecordSetExists(message: String)
      extends DnsFailure(s"The record set specified exists: $message")
  case class UnrecognizedResponse(message: String)
      extends DnsFailure(s"The response from the server was not recognized: $message")
  case class ZoneConnectionNotFound(zone: String)
      extends DnsFailure(s"The connection info for zone $zone was not found")
  case class TryAgain(message: String) extends DnsFailure(message)
  case class Unrecoverable(message: String) extends DnsFailure(message)
  case class HostNotFound(message: String) extends DnsFailure(message)
  case class TypeNotFound(message: String) extends DnsFailure(message)

}

// Unfortunate necessary evil as DNS.Lookup is a final class and cannot be mocked :(
class DnsQuery(val lookup: DNS.Lookup, val zoneName: DNS.Name) {

  def run(): List[DNS.Record] = Option(lookup.run()).map(_.toList).getOrElse(Nil)

  def result: Int = lookup.getResult

  def error: String = lookup.getErrorString
}

class DnsConnection(val resolver: DNS.Resolver) extends DnsConversions {

  import DnsProtocol._

  val logger: Logger = LoggerFactory.getLogger(classOf[DnsConnection])

  def applyChange(change: RecordSetChange): Result[DnsResponse] = change.changeType match {
    case RecordSetChangeType.Create => addRecord(change)
    case RecordSetChangeType.Update => updateRecord(change)
    case RecordSetChangeType.Delete => deleteRecord(change)
  }

  def resolve(name: String, zoneName: String, typ: RecordType): Result[List[RecordSet]] =
    IO {
      for {
        query <- toQuery(name, zoneName, typ)
        records <- runQuery(query)
      } yield records
    }.toResult

  private[dns] def toQuery(
      name: String,
      zoneName: String,
      typ: RecordType): Either[Throwable, DnsQuery] = {
    val dnsName = recordDnsName(name, zoneName)
    logger.info(s"Querying for dns dnsRecordName='${dnsName.toString}'; recordType='$typ'")
    val lookup = new DNS.Lookup(dnsName, toDnsRecordType(typ))
    lookup.setResolver(resolver)
    lookup.setSearchPath(Array.empty[String])
    lookup.setCache(null)

    Right(new DnsQuery(lookup, zoneDnsName(zoneName)))
  }

  private def recordsArePresent(change: RecordSetChange): Either[Throwable, RecordSetChange] =
    change.recordSet.records match {
      case Nil => Left(InvalidRecord(s"DNS.Record submitted ${change.recordSet} has no records"))
      case _ => Right(change)
    }

  private[dns] def addRecord(change: RecordSetChange): Result[DnsResponse] = result {
    for {
      change <- recordsArePresent(change)
      addRecord <- toDnsRRset(change.recordSet, change.zone.name)
      update <- toAddRecordMessage(addRecord, change.zone.name)
      response <- send(update)
    } yield response
  }

  private[dns] def updateRecord(change: RecordSetChange): Result[DnsResponse] = result {
    for {
      change <- recordsArePresent(change)
      dnsRecord <- toDnsRRset(change.recordSet, change.zone.name)
      oldRecord <- change.updates.map(toDnsRRset(_, change.zone.name)).getOrElse(dnsRecord.asRight)
      update <- toUpdateRecordMessage(dnsRecord, oldRecord, change.zone.name)
      response <- send(update)
    } yield response
  }

  private[dns] def deleteRecord(change: RecordSetChange): Result[DnsResponse] = result {
    for {
      change <- recordsArePresent(change)
      dnsRecord <- toDnsRRset(change.recordSet, change.zone.name)
      update <- toDeleteRecordMessage(dnsRecord, change.zone.name)
      response <- send(update)
    } yield response
  }

  private def send(msg: DNS.Message): Either[Throwable, DnsResponse] = {
    val result =
      for {
        resp <- Either.catchNonFatal(resolver.send(msg))
        resp <- toDnsResponse(resp)
      } yield resp

    logger.info(
      s"DnsConnection.send - Sending DNS Message ${obscuredDnsMessage(msg).toString}\n...received response $result")

    result
  }

  private def runQuery(query: DnsQuery): Either[Throwable, List[RecordSet]] = {
    val answers = query.run()

    logger.info(s"Result of DNS lookup is ${answers.map(_.toString)}; result code: ${query.result}")

    query.result match {
      case DNS.Lookup.TRY_AGAIN =>
        // dns java Lookup obscures all RCODEs that it cannot handle as a TRY AGAIN; we do not want to do that
        // because things like NotAuthorized and FormatError should not be something we want to keep trying...
        // dns java puts the string value of the Rcode in the query error for those things it does not deal with
        // gracefully
        // so if we can parse the error into an rcode, then we need to handle it properly; otherwise, we can try again
        // The DNS.Rcode.value function will return -1 if the error cannot be parsed into an integer
        if (DNS.Rcode.value(query.error) >= 0) {
          logger.info(s"Received TRY_AGAIN from DNS lookup; converting error: ${query.error}")
          fromDnsRcodeToError(DNS.Rcode.value(query.error), query.error)
        } else {
          logger.info(s"Unparseable error code returned from DNS: ${query.error}")
          Left(TryAgain(query.error))
        }

      case DNS.Lookup.UNRECOVERABLE => Left(Unrecoverable(query.error))
      case DNS.Lookup.TYPE_NOT_FOUND =>
        Right(List()) //The host exists, but has no records associated with the queried type.
      case DNS.Lookup.HOST_NOT_FOUND =>
        Right(Nil) // This is NXDOMAIN, which means not found, return an empty list
      case DNS.Lookup.SUCCESSFUL => Right(toFlattenedRecordSets(answers, query.zoneName))
    }
  }
}

object DnsConnection {

  def apply(conn: ZoneConnection): DnsConnection = new DnsConnection(createResolver(conn))

  def createResolver(conn: ZoneConnection): DNS.SimpleResolver = {
    // IMPORTANT!  Make sure we decrypt the zone connection before creating the resolver
    val decryptedConnection = conn.decrypted(Crypto.instance)
    val (host, port) = parseHostAndPort(decryptedConnection.primaryServer)
    val resolver = new DNS.SimpleResolver(host)
    resolver.setPort(port)
    resolver.setTSIGKey(new DNS.TSIG(decryptedConnection.keyName, decryptedConnection.key))

    resolver
  }

  private def parseHostAndPort(primaryServer: String): (String, Int) = {
    val parts = primaryServer.trim().split(':')
    if (parts.length < 2)
      (primaryServer, 53)
    else
      (parts(0), parts(1).toInt)
  }
}
