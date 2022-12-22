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
import cats.data._
import cats.implicits._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s.JsonDSL._
import org.json4s._
import scodec.bits.{Bases, ByteVector}
import vinyldns.api.domain.zone.{RecordSetGlobalInfo, RecordSetInfo, RecordSetListInfo}
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.DomainHelpers.removeWhitespace
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.Messages._

trait DnsJsonProtocol extends JsonValidation {
  import vinyldns.core.domain.record.RecordType._

  val dnsSerializers = Seq(
    CreateZoneInputSerializer,
    UpdateZoneInputSerializer,
    ZoneConnectionSerializer,
    AlgorithmSerializer,
    RecordSetSerializer,
    RecordSetListInfoSerializer,
    RecordSetGlobalInfoSerializer,
    RecordSetInfoSerializer,
    RecordSetChangeSerializer,
    JsonEnumV(ZoneStatus),
    JsonEnumV(ZoneChangeStatus),
    JsonEnumV(RecordSetStatus),
    JsonEnumV(RecordSetChangeStatus),
    JsonEnumV(RecordType),
    JsonEnumV(ZoneChangeType),
    JsonEnumV(RecordSetChangeType),
    JsonEnumV(NameSort),
    ASerializer,
    AAAASerializer,
    CNAMESerializer,
    DSSerializer,
    MXSerializer,
    NSSerializer,
    PTRSerializer,
    SOASerializer,
    SPFSerializer,
    SRVSerializer,
    NAPTRSerializer,
    SSHFPSerializer,
    TXTSerializer,
    JsonV[ZoneACL],
    FqdnSerializer
  )

  case object RecordSetChangeSerializer extends ValidationSerializer[RecordSetChange] {
    override def fromJson(js: JValue): ValidatedNel[String, RecordSetChange] =
      (
        (js \ "zone").required[Zone]("Missing RecordSetChange.zone"),
        (js \ "recordSet").required[RecordSet]("Missing RecordSetChange.recordSet"),
        (js \ "userId").required[String]("Missing RecordSetChange.userId"),
        (js \ "changeType").required(RecordSetChangeType, "Missing RecordSetChange.changeType"),
        (js \ "status").default(RecordSetChangeStatus, RecordSetChangeStatus.Pending),
        (js \ "created").default[Instant](Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        (js \ "systemMessage").optional[String],
        (js \ "updates").optional[RecordSet],
        (js \ "id").default[String](UUID.randomUUID.toString),
        (js \ "singleBatchChangeIds").default[List[String]](List())
        ).mapN(RecordSetChange.apply)

    override def toJson(rs: RecordSetChange): JValue =
      ("zone" -> Extraction.decompose(rs.zone)) ~
        ("recordSet" -> Extraction.decompose(rs.recordSet)) ~
        ("userId" -> rs.userId) ~
        ("changeType" -> Extraction.decompose(rs.changeType)) ~
        ("status" -> Extraction.decompose(rs.status)) ~
        ("created" -> Extraction.decompose(rs.created)) ~
        ("systemMessage" -> rs.systemMessage) ~
        ("updates" -> Extraction.decompose(rs.updates)) ~
        ("id" -> rs.id) ~
        ("singleBatchChangeIds" -> Extraction.decompose(rs.singleBatchChangeIds))
  }

  case object CreateZoneInputSerializer extends ValidationSerializer[CreateZoneInput] {
    override def fromJson(js: JValue): ValidatedNel[String, CreateZoneInput] =
      (
        (js \ "name")
          .required[String]("Missing Zone.name")
          .map(removeWhitespace)
          .map(name => if (name.endsWith(".")) name else s"$name."),
        (js \ "email").required[String]("Missing Zone.email"),
        (js \ "connection").optional[ZoneConnection],
        (js \ "transferConnection").optional[ZoneConnection],
        (js \ "shared").default[Boolean](false),
        (js \ "acl").default[ZoneACL](ZoneACL()),
        (js \ "adminGroupId").required[String]("Missing Zone.adminGroupId"),
        (js \ "backendId").optional[String]
        ).mapN(CreateZoneInput.apply)
  }

  case object UpdateZoneInputSerializer extends ValidationSerializer[UpdateZoneInput] {
    override def fromJson(js: JValue): ValidatedNel[String, UpdateZoneInput] =
      (
        (js \ "id").required[String]("Missing Zone.id"),
        (js \ "name")
          .required[String]("Missing Zone.name")
          .map(ensureTrailingDot),
        (js \ "email").required[String]("Missing Zone.email"),
        (js \ "connection").optional[ZoneConnection],
        (js \ "transferConnection").optional[ZoneConnection],
        (js \ "shared").default[Boolean](false),
        (js \ "acl").default[ZoneACL](ZoneACL()),
        (js \ "adminGroupId").required[String]("Missing Zone.adminGroupId"),
        (js \ "backendId").optional[String]
        ).mapN(UpdateZoneInput.apply)
  }

  case object AlgorithmSerializer extends ValidationSerializer[Algorithm] {
    override def fromJson(js: JValue): ValidatedNel[String, Algorithm] =
      js match {
        case JString(value) => Algorithm.fromString(value).toValidatedNel
        case _ => "Unsupported type for key algorithm, must be a string".invalidNel
      }

    override def toJson(a: Algorithm): JValue = JString(a.name)
  }

  case object ZoneConnectionSerializer extends ValidationSerializer[ZoneConnection] {
    override def fromJson(js: JValue): ValidatedNel[String, ZoneConnection] =
      (
        (js \ "name").required[String]("Missing ZoneConnection.name"),
        (js \ "keyName").required[String]("Missing ZoneConnection.keyName"),
        (js \ "key").required[String]("Missing ZoneConnection.key"),
        (js \ "primaryServer").required[String]("Missing ZoneConnection.primaryServer"),
        (js \ "algorithm").default[Algorithm](Algorithm.HMAC_MD5)
        ).mapN(ZoneConnection.apply)
  }

  def checkDomainNameLen(s: String): Boolean = s.length <= 255
  def nameContainsDots(s: String): Boolean = s.contains(".")
  def nameDoesNotContainSpaces(s: String): Boolean = !s.contains(" ")

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
    override def fromJson(js: JValue): ValidatedNel[String, RecordSet] = {
      val recordType = (js \ "type").required(RecordType, "Missing RecordSet.type")
      // This variable is ONLY used for record type checks where the type is already known to be a success.
      // The "getOrElse" is only so that it will cast the type
      val recordTypeGet: RecordType = recordType.getOrElse(A)
      val recordSetResult = (
        (js \ "zoneId").required[String]("Missing RecordSet.zoneId"),
        (js \ "name")
          .required[String]("Missing RecordSet.name")
          .check(
            "Record name must not exceed 255 characters" -> checkDomainNameLen,
            "Record name cannot contain spaces" -> nameDoesNotContainSpaces
          ),
        recordType,
        (js \ "ttl")
          .required[Long]("Missing RecordSet.ttl")
          .check(
            // RFC 1035.2.3.4 and  RFC 2181.8
            "RecordSet.ttl must be a positive signed 32 bit number" -> (_ <= 2147483647),
            "RecordSet.ttl must be a positive signed 32 bit number greater than or equal to 30" -> (_ >= 30)
          ),
        (js \ "status").default(RecordSetStatus, RecordSetStatus.Pending),
        (js \ "created").default[Instant](Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        (js \ "updated").optional[Instant],
        recordType
          .andThen(extractRecords(_, js \ "records")),
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "account").default[String]("system"),
        (js \ "ownerGroupId").optional[String],
        (js \ "fqdn").optional[String]
        ).mapN(RecordSet.apply)

      // Put additional record set level checks below
      recordSetResult.checkIf(recordTypeGet == RecordType.CNAME)(
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
        ("account" -> rs.account) ~
        ("ownerGroupId" -> rs.ownerGroupId) ~
        ("fqdn" -> rs.fqdn)
  }

  case object RecordSetListInfoSerializer extends ValidationSerializer[RecordSetListInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, RecordSetListInfo] =
      (
        RecordSetInfoSerializer.fromJson(js),
        (js \ "accessLevel").required[AccessLevel.AccessLevel]("Missing RecordSet.zoneId")
        ).mapN(RecordSetListInfo.apply)

    override def toJson(rs: RecordSetListInfo): JValue =
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
        ("accessLevel" -> rs.accessLevel.toString) ~
        ("ownerGroupId" -> rs.ownerGroupId) ~
        ("ownerGroupName" -> rs.ownerGroupName) ~
        ("fqdn" -> rs.fqdn)
  }

  case object RecordSetInfoSerializer extends ValidationSerializer[RecordSetInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, RecordSetInfo] =
      (RecordSetSerializer.fromJson(js), (js \ "ownerGroupName").optional[String])
        .mapN(RecordSetInfo.apply)

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
        ("ownerGroupId" -> rs.ownerGroupId) ~
        ("ownerGroupName" -> rs.ownerGroupName) ~
        ("fqdn" -> rs.fqdn)
  }

  case object RecordSetGlobalInfoSerializer extends ValidationSerializer[RecordSetGlobalInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, RecordSetGlobalInfo] =
      (
        RecordSetSerializer.fromJson(js),
        (js \ "zoneName").required[String]("Missing Zone.name"),
        (js \ "zoneShared").required[Boolean]("Missing Zone.shared"),
        (js \ "ownerGroupName").optional[String]
        ).mapN(RecordSetGlobalInfo.apply)

    override def toJson(rs: RecordSetGlobalInfo): JValue =
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
        ("ownerGroupId" -> rs.ownerGroupId) ~
        ("ownerGroupName" -> rs.ownerGroupName) ~
        ("fqdn" -> rs.fqdn) ~
        ("zoneName" -> rs.zoneName) ~
        ("zoneShared" -> rs.zoneShared)
  }

  def extractRecords(typ: RecordType, js: JValue): ValidatedNel[String, List[RecordData]] =
    typ match {
      case RecordType.A => js.required[List[AData]]("Missing A Records")
      case RecordType.AAAA => js.required[List[AAAAData]]("Missing AAAA Records")
      case RecordType.CNAME => js.required[List[CNAMEData]]("Missing CNAME Records")
      case RecordType.DS => js.required[List[DSData]]("Missing DS Records")
      case RecordType.MX => js.required[List[MXData]]("Missing MX Records")
      case RecordType.NS => js.required[List[NSData]]("Missing NS Records")
      case RecordType.PTR => js.required[List[PTRData]]("Missing PTR Records")
      case RecordType.SOA => js.required[List[SOAData]]("Missing SOA Records")
      case RecordType.SPF => js.required[List[SPFData]]("Missing SPF Records")
      case RecordType.SRV => js.required[List[SRVData]]("Missing SRV Records")
      case RecordType.NAPTR => js.required[List[NAPTRData]]("Missing NAPTR Records")
      case RecordType.SSHFP => js.required[List[SSHFPData]]("Missing SSHFP Records")
      case RecordType.TXT => js.required[List[TXTData]]("Missing TXT Records")
      case _ => s"Unsupported type $typ, valid types include ${RecordType.values}".invalidNel
    }

  case object ASerializer extends ValidationSerializer[AData] {
    override def fromJson(js: JValue): ValidatedNel[String, AData] =
      (js \ "address")
        .required[String]("Missing A.address")
        .check(
          "A must be a valid IPv4 Address" -> ipv4Match
        )
        .map(AData.apply)
  }

  case object AAAASerializer extends ValidationSerializer[AAAAData] {
    override def fromJson(js: JValue): ValidatedNel[String, AAAAData] =
      (js \ "address")
        .required[String]("Missing AAAA.address")
        .check(
          "AAAA must be a valid IPv6 Address" -> ipv6Match
        )
        .map(AAAAData.apply)
  }

  case object CNAMESerializer extends ValidationSerializer[CNAMEData] {
    override def fromJson(js: JValue): ValidatedNel[String, CNAMEData] =
      (js \ "cname")
        .required[String]("Missing CNAME.cname")
        .check(
          "CNAME domain name must not exceed 255 characters" -> checkDomainNameLen,
          "CNAME data must be absolute" -> nameContainsDots
        )
        .map(Fqdn.apply)
        .map(CNAMEData.apply)
  }

  case object MXSerializer extends ValidationSerializer[MXData] {
    override def fromJson(js: JValue): ValidatedNel[String, MXData] =
      (
        (js \ "preference")
          .required[Integer]("Missing MX.preference")
          .check(
            "MX.preference must be a 16 bit integer" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "exchange")
          .required[String]("Missing MX.exchange")
          .check(
            "MX.exchange must be less than 255 characters" -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(MXData.apply)
  }

  case object NSSerializer extends ValidationSerializer[NSData] {
    override def fromJson(js: JValue): ValidatedNel[String, NSData] =
      (js \ "nsdname")
        .required[String]("Missing NS.nsdname")
        .check(
          "NS must be less than 255 characters" -> checkDomainNameLen,
          NSDataError -> nameContainsDots
        )
        .map(Fqdn.apply)
        .map(NSData.apply)
  }

  case object PTRSerializer extends ValidationSerializer[PTRData] {
    override def fromJson(js: JValue): ValidatedNel[String, PTRData] =
      (js \ "ptrdname")
        .required[String]("Missing PTR.ptrdname")
        .check(
          "PTR must be less than 255 characters" -> checkDomainNameLen
        )
        .map(Fqdn.apply)
        .map(PTRData.apply)
  }

  case object SOASerializer extends ValidationSerializer[SOAData] {
    override def fromJson(js: JValue): ValidatedNel[String, SOAData] =
      (
        (js \ "mname")
          .required[String]("Missing SOA.mname")
          .check(
            "SOA.mname must be less than 255 characters" -> checkDomainNameLen
          )
          .map(Fqdn.apply),
        (js \ "rname")
          .required[String]("Missing SOA.rname")
          .check(
            "SOA.rname must be less than 255 characters" -> checkDomainNameLen
          )
          .map(removeWhitespace),
        (js \ "serial")
          .required[Long]("Missing SOA.serial")
          .check(
            "SOA.serial must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "refresh")
          .required[Long]("Missing SOA.refresh")
          .check(
            "SOA.refresh must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "retry")
          .required[Long]("Missing SOA.retry")
          .check(
            "SOA.retry must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "expire")
          .required[Long]("Missing SOA.expire")
          .check(
            "SOA.expire must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "minimum")
          .required[Long]("Missing SOA.minimum")
          .check(
            "SOA.minimum must be an unsigned 32 bit number" -> (i => i <= 4294967295L && i >= 0)
          )
        ).mapN(SOAData.apply)
  }

  case object SPFSerializer extends ValidationSerializer[SPFData] {
    override def fromJson(js: JValue): ValidatedNel[String, SPFData] =
      (js \ "text")
        .required[String]("Missing SPF.text")
        .check(
          "SPF record must be less than 64764 characters" -> (_.length < 64764)
        )
        .map(SPFData.apply)
  }

  case object SRVSerializer extends ValidationSerializer[SRVData] {
    override def fromJson(js: JValue): ValidatedNel[String, SRVData] =
      (
        (js \ "priority")
          .required[Integer]("Missing SRV.priority")
          .check(
            "SRV.priority must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "weight")
          .required[Integer]("Missing SRV.weight")
          .check(
            "SRV.weight must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "port")
          .required[Integer]("Missing SRV.port")
          .check(
            "SRV.port must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "target")
          .required[String]("Missing SRV.target")
          .check(
            "SRV.target must be less than 255 characters" -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(SRVData.apply)
  }

  case object NAPTRSerializer extends ValidationSerializer[NAPTRData] {
    override def fromJson(js: JValue): ValidatedNel[String, NAPTRData] =
      (
        (js \ "order")
          .required[Integer]("Missing NAPTR.order")
          .check(
            "NAPTR.order must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "preference")
          .required[Integer]("Missing NAPTR.preference")
          .check(
            "NAPTR.preference must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "flags")
          .required[String]("Missing NAPTR.flags")
          .check(
            "NAPTR.flags must be less than 2 characters" -> (_.length < 2)
          ),
        (js \ "service")
          .required[String]("Missing NAPTR.service")
          .check(
            "NAPTR.service must be less than 255 characters" -> checkDomainNameLen
          ),
        (js \ "regexp")
          .required[String]("Missing NAPTR.regexp")
          .check(
            "NAPTR.regexp must be less than 255 characters" -> checkDomainNameLen
          ),

        (js \ "replacement")
          .required[String]("Missing NAPTR.replacement")
          .check(
            "NAPTR.replacement must be less than 255 characters" -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(NAPTRData.apply)
  }

  case object SSHFPSerializer extends ValidationSerializer[SSHFPData] {
    override def fromJson(js: JValue): ValidatedNel[String, SSHFPData] =
      (
        (js \ "algorithm")
          .required[Integer]("Missing SSHFP.algorithm")
          .check(
            "SSHFP.algorithm must be an unsigned 8 bit number" -> (i => i <= 255 && i >= 0)
          ),
        (js \ "type")
          .required[Integer]("Missing SSHFP.type")
          .check(
            "SSHFP.type must be an unsigned 8 bit number" -> (i => i <= 255 && i >= 0)
          ),
        (js \ "fingerprint").required[String]("Missing SSHFP.fingerprint")
        ).mapN(SSHFPData.apply)

    // necessary because type != typ
    override def toJson(rr: SSHFPData): JValue =
      ("algorithm" -> Extraction.decompose(rr.algorithm)) ~
        ("type" -> Extraction.decompose(rr.typ)) ~
        ("fingerprint" -> rr.fingerprint)
  }

  case object DSSerializer extends ValidationSerializer[DSData] {
    override def fromJson(js: JValue): ValidatedNel[String, DSData] =
      (
        (js \ "keytag")
          .required[Integer]("Missing DS.keytag")
          .check("DS.keytag must be an unsigned 16 bit number" -> (i => i <= 65535 && i >= 0)),
        (js \ "algorithm")
          .required[Integer]("Missing DS.algorithm")
          .map(DnsSecAlgorithm(_))
          .andThen {
            case DnsSecAlgorithm.UnknownAlgorithm(x) =>
              s"Algorithm $x is not a supported DNSSEC algorithm".invalidNel
            case supported => supported.validNel
          },
        (js \ "digesttype")
          .required[Integer]("Missing DS.digesttype")
          .map(DigestType(_))
          .andThen {
            case DigestType.UnknownDigestType(x) =>
              s"Digest Type $x is not a supported DS record digest type".invalidNel
            case supported => supported.validNel
          },
        (js \ "digest")
          .required[String]("Missing DS.digest")
          .map(ByteVector.fromHex(_))
          .andThen {
            case Some(v) => v.validNel
            case None => "Could not convert digest to valid hex".invalidNel
          }
        ).mapN(DSData.apply)

    override def toJson(rr: DSData): JValue =
      ("keytag" -> Extraction.decompose(rr.keyTag)) ~
        ("algorithm" -> rr.algorithm.value) ~
        ("digesttype" -> rr.digestType.value) ~
        ("digest" -> rr.digest.toHex(Bases.Alphabets.HexUppercase))
  }

  case object TXTSerializer extends ValidationSerializer[TXTData] {
    override def fromJson(js: JValue): ValidatedNel[String, TXTData] =
      (js \ "text")
        .required[String]("Missing TXT.text")
        .check(
          "TXT record must be less than 64764 characters" -> (_.length < 64764)
        )
        .map(TXTData.apply)
  }

  case object FqdnSerializer extends ValidationSerializer[Fqdn] {
    override def toJson(fqdn: Fqdn): JValue = Extraction.decompose(fqdn.fqdn)
  }
}
