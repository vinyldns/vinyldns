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
import vinyldns.core.domain.{EncryptFromJson, Encrypted, Fqdn}
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
    EncryptedSerializer,
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
    JsonEnumV(RecordTypeSort),
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
        (js \ "zone").required[Zone](MissingRecordSetZoneMsg),
        (js \ "recordSet").required[RecordSet](MissingRecordSetMsg),
        (js \ "userId").required[String](MissingRecordSetUserIdMsg),
        (js \ "changeType").required(RecordSetChangeType, MissingRecordSetChangeTypeMsg),
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
          .required[String](MissingZoneNameMsg)
          .map(removeWhitespace)
          .map(name => if (name.endsWith(".")) name else s"$name."),
        (js \ "email").required[String](MissingZoneEmailMsg),
        (js \ "connection").optional[ZoneConnection],
        (js \ "transferConnection").optional[ZoneConnection],
        (js \ "shared").default[Boolean](false),
        (js \ "acl").default[ZoneACL](ZoneACL()),
        (js \ "adminGroupId").required[String](MissingZoneGroupIdMsg),
        (js \ "backendId").optional[String],
        (js \ "recurrenceSchedule").optional[String],
        (js \ "scheduleRequestor").optional[String],
        ).mapN(CreateZoneInput.apply)
  }

  case object UpdateZoneInputSerializer extends ValidationSerializer[UpdateZoneInput] {
    override def fromJson(js: JValue): ValidatedNel[String, UpdateZoneInput] =
      (
        (js \ "id").required[String](MissingZoneIdMsg),
        (js \ "name")
          .required[String](MissingZoneNameMsg)
          .map(ensureTrailingDot),
        (js \ "email").required[String](MissingZoneEmailMsg),
        (js \ "connection").optional[ZoneConnection],
        (js \ "transferConnection").optional[ZoneConnection],
        (js \ "shared").default[Boolean](false),
        (js \ "acl").default[ZoneACL](ZoneACL()),
        (js \ "adminGroupId").required[String](MissingZoneGroupIdMsg),
        (js \ "recurrenceSchedule").optional[String],
        (js \ "scheduleRequestor").optional[String],
        (js \ "backendId").optional[String],
        ).mapN(UpdateZoneInput.apply)
  }

  case object AlgorithmSerializer extends ValidationSerializer[Algorithm] {
    override def fromJson(js: JValue): ValidatedNel[String, Algorithm] =
      js match {
        case JString(value) => Algorithm.fromString(value).toValidatedNel
        case _ => UnsupportedKeyAlgorithmMsg.invalidNel
      }

    override def toJson(a: Algorithm): JValue = JString(a.name)
  }

  case object EncryptedSerializer extends ValidationSerializer[Encrypted] {
    override def fromJson(js: JValue): ValidatedNel[String, Encrypted] =
      js match {
        case JString(value) => EncryptFromJson.fromString(value).toValidatedNel
        case _ => UnsupportedEncryptedTypeErrorMsg.invalidNel
      }

    override def toJson(a: Encrypted): JValue = JString(a.value)
  }

  case object ZoneConnectionSerializer extends ValidationSerializer[ZoneConnection] {
    override def fromJson(js: JValue): ValidatedNel[String, ZoneConnection] =
      (
        (js \ "name").required[String](MissingZoneConnectionNameMsg),
        (js \ "keyName").required[String](MissingZoneConnectionKeyNameMsg),
        (js \ "key").required[Encrypted](MissingZoneConnectionKeyMsg),
        (js \ "primaryServer").required[String](MissingZoneConnectionServer),
        (js \ "algorithm").default[Algorithm](Algorithm.HMAC_MD5)
        ).mapN(ZoneConnection.apply)
  }

  def checkDomainNameLen(s: String): Boolean = s.length <= 255
  def validateNaptrFlag(flag: String): Boolean = flag == "U" || flag  == "S" || flag  == "A" || flag  == "P"
  def validateNaptrRegexp(regexp: String): Boolean = regexp.startsWith("!") && regexp.endsWith("!") || regexp == ""
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
      val recordType = (js \ "type").required(RecordType, MissingRecordSetTypeMsg)
      // This variable is ONLY used for record type checks where the type is already known to be a success.
      // The "getOrElse" is only so that it will cast the type
      val recordTypeGet: RecordType = recordType.getOrElse(A)
      val recordSetResult = (
        (js \ "zoneId").required[String](MissingRecordSetZoneIdMsg),
        (js \ "name")
          .required[String](MissingRecordSetNameMsg)
          .check(
            RecordNameLengthMsg -> checkDomainNameLen,
            RecordContainsSpaceMsg -> nameDoesNotContainSpaces
          ),
        recordType,
        (js \ "ttl")
          .required[Long](MissingRecordSetTTL)
          .check(
            // RFC 1035.2.3.4 and  RFC 2181.8
            RecordSetTTLNotPositiveMsg -> (_ <= 2147483647),
            RecordSetTTLNotValidMsg -> (_ >= 30)
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
        CnameValidationMsg -> { rs =>
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
        (js \ "accessLevel").required[AccessLevel.AccessLevel](MissingRecordSetZoneIdMsg)
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
        (js \ "zoneName").required[String](MissingZoneNameMsg),
        (js \ "zoneShared").required[Boolean](MissingZoneSharedMsg),
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
      case RecordType.A => js.required[List[AData]](MissingARecordsMsg)
      case RecordType.AAAA => js.required[List[AAAAData]](MissingAAAARecordsMsg)
      case RecordType.CNAME => js.required[List[CNAMEData]](MissingCnameRecordsMsg)
      case RecordType.DS => js.required[List[DSData]](MissingDSRecordsMsg)
      case RecordType.MX => js.required[List[MXData]](MissingMXRecordsMsg)
      case RecordType.NS => js.required[List[NSData]](MissingNsRecordsMsg)
      case RecordType.PTR => js.required[List[PTRData]](MissingPTRRecordsMsg)
      case RecordType.SOA => js.required[List[SOAData]](MissingSOARecordsMsg)
      case RecordType.SPF => js.required[List[SPFData]](MissingSPFRecordsMsg)
      case RecordType.SRV => js.required[List[SRVData]](MissingSRVRecordsMsg)
      case RecordType.NAPTR => js.required[List[NAPTRData]](MissingNAPTRRecordsMsg)
      case RecordType.SSHFP => js.required[List[SSHFPData]](MissingSSHFPRecordsMsg)
      case RecordType.TXT => js.required[List[TXTData]](MissingTXTRecordsMsg)
      case _ => UnsupportedRecordTypeMsg.format(typ, RecordType.values).invalidNel
    }

  case object ASerializer extends ValidationSerializer[AData] {
    override def fromJson(js: JValue): ValidatedNel[String, AData] =
      (js \ "address")
        .required[String](MissingAAddressMsg)
        .check(
          InvalidIPv4Msg -> ipv4Match
        )
        .map(AData.apply)
  }

  case object AAAASerializer extends ValidationSerializer[AAAAData] {
    override def fromJson(js: JValue): ValidatedNel[String, AAAAData] =
      (js \ "address")
        .required[String](MissingAAAAAddressMsg)
        .check(
          InvalidIPv6Msg -> ipv6Match
        )
        .map(AAAAData.apply)
  }

  case object CNAMESerializer extends ValidationSerializer[CNAMEData] {
    override def fromJson(js: JValue): ValidatedNel[String, CNAMEData] =
      (js \ "cname")
        .required[String](MissingCnameMsg)
        .check(
          CnameLengthMsg -> checkDomainNameLen,
          CnameAbsoluteMsg -> nameContainsDots
        )
        .map(Fqdn.apply)
        .map(CNAMEData.apply)
  }

  case object MXSerializer extends ValidationSerializer[MXData] {
    override def fromJson(js: JValue): ValidatedNel[String, MXData] =
      (
        (js \ "preference")
          .required[Integer](MissingMXPreferenceMsg)
          .check(
            MXPreferenceValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "exchange")
          .required[String](MissingMXExchangeMsg)
          .check(
            MXExchangeValidationMsg -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(MXData.apply)
  }

  case object NSSerializer extends ValidationSerializer[NSData] {
    override def fromJson(js: JValue): ValidatedNel[String, NSData] =
      (js \ "nsdname")
        .required[String](MissingNSNameMsg)
        .check(
          NSNameValidationMsg -> checkDomainNameLen,
          NSDataErrorMsg -> nameContainsDots
        )
        .map(Fqdn.apply)
        .map(NSData.apply)
  }

  case object PTRSerializer extends ValidationSerializer[PTRData] {
    override def fromJson(js: JValue): ValidatedNel[String, PTRData] =
      (js \ "ptrdname")
        .required[String](MissingPTRNameMsg)
        .check(
          PTRNameValidationMsg -> checkDomainNameLen
        )
        .map(Fqdn.apply)
        .map(PTRData.apply)
  }

  case object SOASerializer extends ValidationSerializer[SOAData] {
    override def fromJson(js: JValue): ValidatedNel[String, SOAData] =
      (
        (js \ "mname")
          .required[String](MissingSOAMNameMsg)
          .check(
            SOAMNameValidationMsg -> checkDomainNameLen
          )
          .map(Fqdn.apply),
        (js \ "rname")
          .required[String](MissingSOARNameMsg)
          .check(
            SOARNameValidationMsg -> checkDomainNameLen
          )
          .map(removeWhitespace),
        (js \ "serial")
          .required[Long](MissingSOASerialMsg)
          .check(
            SOASerialValidationMsg -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "refresh")
          .required[Long](MissingSOARefreshMsg)
          .check(
            SOARefreshValidationMsg -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "retry")
          .required[Long](MissingSOARetryMsg)
          .check(
            SOARetryValidationMsg -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "expire")
          .required[Long](MissingSOAExpireMsg)
          .check(
            SOAExpireValidationMsg -> (i => i <= 4294967295L && i >= 0)
          ),
        (js \ "minimum")
          .required[Long](MissingSOAMinimumMsg)
          .check(
            SOAMinimumValidationMsg -> (i => i <= 4294967295L && i >= 0)
          )
        ).mapN(SOAData.apply)
  }

  case object SPFSerializer extends ValidationSerializer[SPFData] {
    override def fromJson(js: JValue): ValidatedNel[String, SPFData] =
      (js \ "text")
        .required[String](MissingSPFTextMsg)
        .check(
          SPFValidationMsg -> (_.length < 64764)
        )
        .map(SPFData.apply)
  }

  case object SRVSerializer extends ValidationSerializer[SRVData] {
    override def fromJson(js: JValue): ValidatedNel[String, SRVData] =
      (
        (js \ "priority")
          .required[Integer](MissingSRVPriorityMsg)
          .check(
            SRVPriorityValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "weight")
          .required[Integer](MissingSRVWeightMsg)
          .check(
            SRVWeightValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "port")
          .required[Integer](MissingSRVPortMsg)
          .check(
            SRVPortValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "target")
          .required[String](MissingSRVTargetMsg)
          .check(
            SRVTargetValidationMsg -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(SRVData.apply)
  }

  case object NAPTRSerializer extends ValidationSerializer[NAPTRData] {
    override def fromJson(js: JValue): ValidatedNel[String, NAPTRData] =
      (
        (js \ "order")
          .required[Integer](MissingNAPTROrderMsg)
          .check(
            NAPTROrderValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "preference")
          .required[Integer](MissingNAPTRPreferenceMsg)
          .check(
            NAPTRPreferenceValidationMsg -> (i => i <= 65535 && i >= 0)
          ),
        (js \ "flags")
          .required[String](MissingNAPTRFlagsMsg)
          .check(
            NAPTRFlagsValidationMsg -> validateNaptrFlag
          ),
        (js \ "service")
          .required[String](MissingNAPTRServiceMsg)
          .check(
            NAPTRServiceValidationMsg -> checkDomainNameLen
          ),
        (js \ "regexp")
          .required[String](MissingNAPTRRegexMsg)
          .check(
            NAPTRRegexValidationMsg -> validateNaptrRegexp
          ),

        (js \ "replacement")
          .required[String](MissingNAPTRReplacementMsg)
          .check(
            NAPTRReplacementValidationMsg -> checkDomainNameLen
          )
          .map(Fqdn.apply)
        ).mapN(NAPTRData.apply)
  }

  case object SSHFPSerializer extends ValidationSerializer[SSHFPData] {
    override def fromJson(js: JValue): ValidatedNel[String, SSHFPData] =
      (
        (js \ "algorithm")
          .required[Integer](MissingSSHFPAlgorithmMsg)
          .check(
            SSHFPAlgorithmValidationMsg -> (i => i <= 255 && i >= 0)
          ),
        (js \ "type")
          .required[Integer](MissingSSHFPTypeMsg)
          .check(
            SSHFPTypeValidationMsg -> (i => i <= 255 && i >= 0)
          ),
        (js \ "fingerprint").required[String](MissingSSHFPFingerprintMsg)
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
          .required[Integer](MissingDSKeytagMsg)
          .check(DSKeytagValidationMsg -> (i => i <= 65535 && i >= 0)),
        (js \ "algorithm")
          .required[Integer](MissingDSAlgorithmMsg)
          .map(DnsSecAlgorithm(_))
          .andThen {
            case DnsSecAlgorithm.UnknownAlgorithm(x) =>
              UnsupportedDNSSECMsg.format(x).invalidNel
            case supported => supported.validNel
          },
        (js \ "digesttype")
          .required[Integer](MissingDSDigestTypeMsg)
          .map(DigestType(_))
          .andThen {
            case DigestType.UnknownDigestType(x) =>
              UnsupportedDigestTypeMsg.format(x).invalidNel
            case supported => supported.validNel
          },
        (js \ "digest")
          .required[String](MissingDSDigestMsg)
          .map(ByteVector.fromHex(_))
          .andThen {
            case Some(v) => v.validNel
            case None => DigestConvertMsg.invalidNel
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
        .required[String](MissingTXTTextMsg)
        .check(
          TXTRecordValidationMsg -> (_.length < 64764)
        )
        .map(TXTData.apply)
  }

  case object FqdnSerializer extends ValidationSerializer[Fqdn] {
    override def toJson(fqdn: Fqdn): JValue = Extraction.decompose(fqdn.fqdn)
  }
}
