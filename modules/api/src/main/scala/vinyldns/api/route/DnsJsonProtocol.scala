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

package vinyldns.api.route

import java.util.UUID

import vinyldns.api.domain.dns.DnsConversions._
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone._

trait DnsJsonProtocol extends JsonValidation {
  import vinyldns.api.domain.record.RecordType._

  val dnsSerializers = Seq(
    ZoneSerializer,
    ZoneConnectionSerializer,
    RecordSetSerializer,
    RecordSetInfoSerializer,
    ZoneChangeSerializer,
    RecordSetChangeSerializer,
    JsonEnumV(ZoneStatus),
    JsonEnumV(ZoneChangeStatus),
    JsonEnumV(RecordSetStatus),
    JsonEnumV(RecordSetChangeStatus),
    JsonEnumV(RecordType),
    JsonEnumV(ZoneChangeType),
    JsonEnumV(RecordSetChangeType),
    ASerializer,
    AAAASerializer,
    CNAMESerializer,
    MXSerializer,
    NSSerializer,
    PTRSerializer,
    SOASerializer,
    SPFSerializer,
    SRVSerializer,
    SSHFPSerializer,
    TXTSerializer,
    JsonV[ZoneACL]
  )

  case object ZoneChangeSerializer extends ValidationSerializer[ZoneChange] {
    override def fromJson(js: JValue): JsonDeserialized[ZoneChange] =
      (
        (js \ "zone").required[Zone]("Missing ZoneChange.zone")
          |@| (js \ "userId").required[String]("Missing ZoneChange.userId")
          |@| (js \ "changeType").required(ZoneChangeType, "Missing ZoneChange.changeType")
          |@| (js \ "status").required(ZoneChangeStatus, "Missing ZoneChange.status")
          |@| (js \ "created").default[DateTime](DateTime.now)
          |@| (js \ "systemMessage").optional[String]
          |@| (js \ "id").default[String](UUID.randomUUID.toString)
      )(ZoneChange.apply)
  }

  case object RecordSetChangeSerializer extends ValidationSerializer[RecordSetChange] {
    override def fromJson(js: JValue): JsonDeserialized[RecordSetChange] =
      (
        (js \ "zone").required[Zone]("Missing RecordSetChange.zone")
          |@| (js \ "recordSet").required[RecordSet]("Missing RecordSetChange.recordSet")
          |@| (js \ "userId").required[String]("Missing RecordSetChange.userId")
          |@| (js \ "changeType")
            .required(RecordSetChangeType, "Missing RecordSetChange.changeType")
          |@| (js \ "status").default(RecordSetChangeStatus, RecordSetChangeStatus.Pending)
          |@| (js \ "created").default[DateTime](DateTime.now)
          |@| (js \ "systemMessage").optional[String]
          |@| (js \ "updates").optional[RecordSet]
          |@| (js \ "id").default[String](UUID.randomUUID.toString)
          |@| (js \ "singleBatchChangeIds").default[List[String]](List())
      )(RecordSetChange.apply)
  }

  case object ZoneSerializer extends ValidationSerializer[Zone] {
    override def fromJson(js: JValue): JsonDeserialized[Zone] = {
      val curriedApply = (Zone.apply _).curried

      // need to apply the arguments last to first with <*>
      // cant use |@| here, hit the limit for how many can be chained
      ((js \ "latestSync").optional[DateTime]
        <*> ((js \ "adminGroupId").default[String]("system")
          <*> ((js \ "acl").default[ZoneACL](ZoneACL())
            <*> ((js \ "shared").default[Boolean](false)
              <*> ((js \ "account").default[String]("system")
                <*> ((js \ "transferConnection").optional[ZoneConnection]
                  <*> ((js \ "connection").optional[ZoneConnection]
                    <*> ((js \ "id").default[String](UUID.randomUUID().toString)
                      <*> ((js \ "updated").optional[DateTime]
                        <*> ((js \ "created").default[DateTime](DateTime.now)
                          <*> ((js \ "status").default(ZoneStatus, ZoneStatus.Active)
                            <*> ((js \ "email").required[String]("Missing Zone.email")
                              <*> ((js \ "name")
                                .required[String]("Missing Zone.name")
                                .map(name => if (name.endsWith(".")) name else s"$name."))
                                .map(curriedApply)))))))))))))
    }
  }

  case object ZoneConnectionSerializer extends ValidationSerializer[ZoneConnection] {
    override def fromJson(js: JValue): JsonDeserialized[ZoneConnection] =
      (
        (js \ "name").required[String]("Missing ZoneConnection.name")
          |@| (js \ "keyName").required[String]("Missing ZoneConnection.keyName")
          |@| (js \ "key").required[String]("Missing ZoneConnection.key")
          |@| (js \ "primaryServer").required[String]("Missing ZoneConnection.primaryServer")
      )(ZoneConnection.apply)
  }

  def checkDomainNameLen(s: String): Boolean = s.length <= 255
  def nameContainsDots(s: String): Boolean = s.contains(".")

  val ipv4Re =
    """^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".r

  // Adapted from https://stackoverflow.com/questions/53497/regular-expression-that-matches-valid-ipv6-addresses
  // As noted in comments, might fail in very unusual edge cases
  val ipv6Re =
    """^(
      #([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|
      #([0-9a-fA-F]{1,4}:){1,7}:|
      #([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|
      #([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|
      #([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|
      #([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|
      #([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|
      #[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|
      #:((:[0-9a-fA-F]{1,4}){1,7}|:)|
      #fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|
      #::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|
      #(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|
      #([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|
      #(2[0-4]|1{0,1}[0-9]){0,1}[0-9])
      #)$""".stripMargin('#').replaceAll("\n", "").r

  def ipv4Match(s: String): Boolean = ipv4Re.findFirstIn(s).isDefined
  def ipv6Match(s: String): Boolean = ipv6Re.findFirstIn(s).isDefined

  case object RecordSetSerializer extends ValidationSerializer[RecordSet] {
    import RecordType._
    override def fromJson(js: JValue): JsonDeserialized[RecordSet] = {
      val recordType = (js \ "type").required(RecordType, "Missing RecordSet.type")
      // This variable is ONLY used for record type checks where the type is already known to be a success.
      // The "getOrElse" is only so that it will cast the type
      val recordTypeGet: RecordType = recordType.getOrElse(A)
      val recordSetResult = (
        (js \ "zoneId").required[String]("Missing RecordSet.zoneId")
          |@| (js \ "name")
            .required[String]("Missing RecordSet.name")
            .check(
              "Record name must not exceed 255 characters" -> checkDomainNameLen
              // Following checks require recordType
            )
            .checkif(recordType.isSuccess)(
              "Record name cannot contain '.' with given type" ->
                ((s: String) => !((recordTypeGet == CNAME) && nameContainsDots(s)))
            )
          |@| recordType
          |@| (js \ "ttl")
            .required[Long]("Missing RecordSet.ttl")
            .check(
              // RFC 1035.2.3.4 and  RFC 2181.8
              "RecordSet.ttl must be a positive signed 32 bit number" -> (_ <= 2147483647),
              "RecordSet.ttl must be a positive signed 32 bit number greater than or equal to 30" -> (_ >= 30)
            )
          |@| (js \ "status").default(RecordSetStatus, RecordSetStatus.Pending)
          |@| (js \ "created").default[DateTime](DateTime.now)
          |@| (js \ "updated").optional[DateTime]
          |@| recordType.flatMap(extractRecords(_, js \ "records"))
          |@| (js \ "id").default[String](UUID.randomUUID().toString)
          |@| (js \ "account").default[String]("system")
      )(RecordSet.apply)

      // Put additional record set level checks below
      recordSetResult.checkif(recordTypeGet == RecordType.CNAME)(
        "CNAME record sets cannot contain multiple records" -> { rs =>
          rs.records.length <= 1
        }
      )
    }

    // necessary because "type" != "typ"
    override def toJson(rs: RecordSet): JValue =
      ("type" -> Extraction.decompose(rs.typ)) ~
        ("zoneId" -> rs.zoneId) ~
        ("name" -> rs.name) ~
        ("ttl" -> rs.ttl) ~
        ("status" -> Extraction.decompose(rs.status)) ~
        ("created" -> Extraction.decompose(rs.created)) ~
        ("updated" -> Extraction.decompose(rs.updated)) ~
        ("records" -> Extraction.decompose(rs.records)) ~
        ("id" -> rs.id) ~
        ("account" -> rs.account)
  }

  case object RecordSetInfoSerializer extends ValidationSerializer[RecordSetInfo] {
    override def fromJson(js: JValue): JsonDeserialized[RecordSetInfo] =
      (RecordSetSerializer.fromJson(js)
        |@| (js \ "accessLevel").required[AccessLevel.AccessLevel]("Missing RecordSet.zoneId"))(
        RecordSetInfo.apply)

    override def toJson(rs: RecordSetInfo): JValue =
      ("type" -> Extraction.decompose(rs.typ)) ~
        ("zoneId" -> rs.zoneId) ~
        ("name" -> rs.name) ~
        ("ttl" -> rs.ttl) ~
        ("status" -> Extraction.decompose(rs.status)) ~
        ("created" -> Extraction.decompose(rs.created)) ~
        ("updated" -> Extraction.decompose(rs.updated)) ~
        ("records" -> Extraction.decompose(rs.records)) ~
        ("id" -> rs.id) ~
        ("account" -> rs.account) ~
        ("accessLevel" -> rs.accessLevel.toString)
  }

  def extractRecords(typ: RecordType, js: JValue): JsonDeserialized[List[RecordData]] =
    typ match {
      case RecordType.A => js.required[List[AData]]("Missing A Records")
      case RecordType.AAAA => js.required[List[AAAAData]]("Missing AAAA Records")
      case RecordType.CNAME => js.required[List[CNAMEData]]("Missing CNAME Records")
      case RecordType.MX => js.required[List[MXData]]("Missing MX Records")
      case RecordType.NS => js.required[List[NSData]]("Missing NS Records")
      case RecordType.PTR => js.required[List[PTRData]]("Missing PTR Records")
      case RecordType.SOA => js.required[List[SOAData]]("Missing SOA Records")
      case RecordType.SPF => js.required[List[SPFData]]("Missing SPF Records")
      case RecordType.SRV => js.required[List[SRVData]]("Missing SRV Records")
      case RecordType.SSHFP => js.required[List[SSHFPData]]("Missing SSHFP Records")
      case RecordType.TXT => js.required[List[TXTData]]("Missing TXT Records")
      case _ => s"Unsupported type $typ, valid types include ${RecordType.values}".failureNel
    }

  case object ASerializer extends ValidationSerializer[AData] {
    override def fromJson(js: JValue): JsonDeserialized[AData] =
      (js \ "address")
        .required[String]("Missing A.address")
        .check(
          "A must be a valid IPv4 Address" -> ipv4Match
        )
        .map(AData.apply)
  }

  case object AAAASerializer extends ValidationSerializer[AAAAData] {
    override def fromJson(js: JValue): JsonDeserialized[AAAAData] =
      (js \ "address")
        .required[String]("Missing AAAA.address")
        .check(
          "AAAA must be a valid IPv6 Address" -> ipv6Match
        )
        .map(AAAAData.apply)
  }

  case object CNAMESerializer extends ValidationSerializer[CNAMEData] {
    override def fromJson(js: JValue): JsonDeserialized[CNAMEData] =
      (js \ "cname")
        .required[String]("Missing CNAME.cname")
        .check(
          "CNAME domain name must not exceed 255 characters" -> checkDomainNameLen,
          "CNAME data must be absolute" -> nameContainsDots
        )
        .map(ensureTrailingDot)
        .map(CNAMEData.apply)
  }

  case object MXSerializer extends ValidationSerializer[MXData] {
    override def fromJson(js: JValue): JsonDeserialized[MXData] =
      (
        (js \ "preference")
          .required[Integer]("Missing MX.preference")
          .check(
            "MX.preference must be a 16 bit integer" -> (i => i <= 65535 && i >= 0)
          )
          |@| (js \ "exchange")
            .required[String]("Missing MX.exchange")
            .check(
              "MX.exchange must be less than 255 characters" -> checkDomainNameLen
            )
            .map(ensureTrailingDot)
      )(MXData.apply)
  }

  case object NSSerializer extends ValidationSerializer[NSData] {
    override def fromJson(js: JValue): JsonDeserialized[NSData] =
      (js \ "nsdname")
        .required[String]("Missing NS.nsdname")
        .check(
          "NS must be less than 255 characters" -> checkDomainNameLen,
          "NS data must be absolute" -> nameContainsDots
        )
        .map(NSData.apply)
  }

  case object PTRSerializer extends ValidationSerializer[PTRData] {
    override def fromJson(js: JValue): JsonDeserialized[PTRData] =
      (js \ "ptrdname")
        .required[String]("Missing PTR.ptrdname")
        .check(
          "PTR must be less than 255 characters" -> checkDomainNameLen
        )
        .map(ensureTrailingDot)
        .map(PTRData.apply)
  }

  case object SOASerializer extends ValidationSerializer[SOAData] {
    override def fromJson(js: JValue): JsonDeserialized[SOAData] =
      (
        (js \ "mname")
          .required[String]("Missing SOA.mname")
          .check(
            "SOA.mname must be less than 255 characters" -> checkDomainNameLen
          )
          |@| (js \ "rname")
            .required[String]("Missing SOA.rname")
            .check(
              "SOA.rname must be less than 255 characters" -> checkDomainNameLen
            )
          |@| (js \ "serial")
            .required[Long]("Missing SOA.serial")
            .check(
              "SOA.serial must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
            )
          |@| (js \ "refresh")
            .required[Long]("Missing SOA.refresh")
            .check(
              "SOA.refresh must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
            )
          |@| (js \ "retry")
            .required[Long]("Missing SOA.retry")
            .check(
              "SOA.retry must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
            )
          |@| (js \ "expire")
            .required[Long]("Missing SOA.expire")
            .check(
              "SOA.expire must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
            )
          |@| (js \ "minimum")
            .required[Long]("Missing SOA.minimum")
            .check(
              "SOA.minimum must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
            )
      )(SOAData.apply)
  }

  case object SPFSerializer extends ValidationSerializer[SPFData] {
    override def fromJson(js: JValue): JsonDeserialized[SPFData] =
      (js \ "text")
        .required[String]("Missing SPF.text")
        .check(
          "SPF record must be less than 64764 characters" -> (_.length < 64764)
        )
        .map(SPFData.apply)
  }

  case object SRVSerializer extends ValidationSerializer[SRVData] {
    override def fromJson(js: JValue): JsonDeserialized[SRVData] =
      (
        (js \ "priority")
          .required[Integer]("Missing SRV.priority")
          .check(
            "SRV.priority must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          )
          |@| (js \ "weight")
            .required[Integer]("Missing SRV.weight")
            .check(
              "SRV.weight must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
            )
          |@| (js \ "port")
            .required[Integer]("Missing SRV.port")
            .check(
              "SRV.port must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
            )
          |@| (js \ "target")
            .required[String]("Missing SRV.target")
            .check(
              "SRV.target must be less than 255 characters" -> checkDomainNameLen
            )
            .map(ensureTrailingDot)
      )(SRVData.apply)
  }

  case object SSHFPSerializer extends ValidationSerializer[SSHFPData] {
    override def fromJson(js: JValue): JsonDeserialized[SSHFPData] =
      (
        (js \ "algorithm")
          .required[Integer]("Missing SSHFP.algorithm")
          .check(
            "SSHFP.algorithm must be an unsigned 8 bit number" -> (i => i <= 255 && i >= 0)
          )
          |@| (js \ "type")
            .required[Integer]("Missing SSHFP.type")
            .check(
              "SSHFP.type must be an unsigned 8 bit number" -> (i => i <= 255 && i >= 0)
            )
          |@| (js \ "fingerprint").required[String]("Missing SSHFP.fingerprint")
      )(SSHFPData.apply)

    // necessary because type != typ
    override def toJson(rr: SSHFPData): JValue =
      ("algorithm" -> Extraction.decompose(rr.algorithm)) ~
        ("type" -> Extraction.decompose(rr.typ)) ~
        ("fingerprint" -> rr.fingerprint)
  }

  case object TXTSerializer extends ValidationSerializer[TXTData] {
    override def fromJson(js: JValue): JsonDeserialized[TXTData] =
      (js \ "text")
        .required[String]("Missing TXT.text")
        .check(
          "TXT record must be less than 64764 characters" -> (_.length < 64764)
        )
        .map(TXTData.apply)
  }
}
