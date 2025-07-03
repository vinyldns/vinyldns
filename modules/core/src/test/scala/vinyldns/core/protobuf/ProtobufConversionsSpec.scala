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

package vinyldns.core.protobuf

import java.time.Instant
import org.scalatest.{Assertion, OptionValues}
import vinyldns.core.TestRecordSetData.ds
import vinyldns.core.domain.{Encrypted, Fqdn}
import vinyldns.core.domain.membership.UserChange.{CreateUser, UpdateUser}
import vinyldns.core.domain.membership.{LockStatus, User, UserChangeType}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.proto.VinylDNSProto
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection.JavaConverters._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.temporal.ChronoUnit

class ProtobufConversionsSpec
    extends AnyWordSpec
    with Matchers
    with ProtobufConversions
    with OptionValues {

  private val zoneConnection = ZoneConnection("name", "keyName", Encrypted("key"), "server")

  private val zoneId = "test.zone.id"

  private val userAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME)
  )

  private val groupAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    None,
    Some("group"),
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME)
  )

  private val zoneAcl = ZoneACL(Set(userAclRule, groupAclRule))

  private val zone = Zone(
    "test.zone.actor.zone",
    "test@test.com",
    connection = Some(ZoneConnection("connection.ok", "keyName", Encrypted("key"), "10.1.1.1")),
    transferConnection = Some(ZoneConnection("connection.ok", "keyName", Encrypted("key"), "10.1.1.2")),
    shared = true,
    id = zoneId,
    acl = zoneAcl,
    adminGroupId = "test-group-id"
  )
  private val zoneChange = ZoneChange(
    zone,
    "system",
    ZoneChangeType.Update,
    ZoneChangeStatus.Synced,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    Some("hello")
  )

  private val zoneGenerationResponse = ZoneGenerationResponse(Some(200),Some("bind"), Some(("response" -> "success"): JValue), GenerateZoneChangeType.Create)

  val bindProviderParams: Map[String, JValue] = Map(
    "nameservers" -> JArray(List(JString("bind_ns"))),
    "admin_email" -> JString("test@test.com"),
    "ttl" -> JInt(3600),
    "refresh" -> JInt(6048000),
    "retry" -> JInt(86400),
    "expire" -> JInt(24192000),
    "negative_cache_ttl" -> JInt(6048000)
  )

  private val generateBindZone = GenerateZone(
    "test.zone.actor.groupId",
    "test@test.com",
    "bind",
    "test.zone.actor.zone",
    providerParams = bindProviderParams,
    response=Some(zoneGenerationResponse)
  )
  private val aRs = RecordSet(
    "id",
    "test.rs",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
    List(AData("10.1.1.1"), AData("10.2.2.2"))
  )
  private val aaaa = RecordSet(
    zone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("10.1.1.1"))
  )
  private val cname = RecordSet(
    zone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("cname")))
  )
  private val mx = RecordSet(
    zone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(MXData(100, Fqdn("exchange")))
  )
  private val ns = RecordSet(
    zone.id,
    "ns",
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NSData(Fqdn("nsrecordname")))
  )
  private val ptr = RecordSet(
    zone.id,
    "ptr",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(PTRData(Fqdn("ptr")))
  )
  private val soa = RecordSet(
    zone.id,
    "soa",
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SOAData(Fqdn("name"), "name", 1, 2, 3, 4, 5))
  )
  private val spf = RecordSet(
    zone.id,
    "soa",
    RecordType.SPF,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SPFData("spf"))
  )
  private val srv = RecordSet(
    zone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SRVData(1, 2, 3, Fqdn("target")))
  )
  private val naptr = RecordSet(
    zone.id,
    "naptr",
    RecordType.NAPTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NAPTRData(1, 2, "U", "E2U+sip", "!.*!test.!", Fqdn("target")))
  )
  private val sshfp = RecordSet(
    zone.id,
    "sshfp",
    RecordType.SSHFP,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SSHFPData(1, 2, "fingerprint"))
  )
  private val txt = RecordSet(
    zone.id,
    "txt",
    RecordType.TXT,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("text"))
  )

  private val singleBatchChangeIds = List("batch1", "batch2")

  def rsChange(rs: RecordSet): RecordSetChange =
    RecordSetChange(
      zone,
      rs,
      "system",
      RecordSetChangeType.Update,
      RecordSetChangeStatus.Pending,
      Instant.now.truncatedTo(ChronoUnit.MILLIS),
      Some("hello"),
      Some(rs),
      singleBatchChangeIds = singleBatchChangeIds
    )

  def matchingRuleFor(pbRule: VinylDNSProto.ACLRule): ACLRule => Boolean = aclRule => {

    lazy val accessLevelsMatch = pbRule.getAccessLevel == aclRule.accessLevel.toString

    lazy val descriptionsMatch =
      aclRule.description.isEmpty && !pbRule.hasDescription ||
        aclRule.description.contains(pbRule.getDescription)

    lazy val userIdsMatch =
      aclRule.userId.isEmpty && !pbRule.hasUserId ||
        aclRule.userId.contains(pbRule.getUserId)

    lazy val groupIdsMatch =
      aclRule.groupId.isEmpty && !pbRule.hasGroupId ||
        aclRule.groupId.contains(pbRule.getGroupId)

    lazy val recordMasksMatch =
      aclRule.recordMask.isEmpty && !pbRule.hasRecordMask ||
        aclRule.recordMask.contains(pbRule.getRecordMask)

    lazy val recordTypesMatch =
      if (pbRule.getRecordTypesCount > 0) {
        pbRule.getRecordTypesList.asScala.map(RecordType.withName).toSet == aclRule.recordTypes
      } else {
        aclRule.recordTypes.isEmpty
      }

    accessLevelsMatch && descriptionsMatch && userIdsMatch && groupIdsMatch && recordMasksMatch && recordTypesMatch
  }

  def aclMatches(pb: VinylDNSProto.ZoneACL, acl: ZoneACL): Unit = {
    val pbRules = pb.getRulesList.asScala
    pbRules.length shouldBe acl.rules.size

    pbRules.foreach { pbRule =>
      val matchingAclRule = acl.rules.find(matchingRuleFor(pbRule))
      matchingAclRule should be('defined)
    }
  }

  def zoneMatches(pb: VinylDNSProto.Zone, zn: Zone): Unit = {
    pb.getName shouldBe zn.name
    pb.getEmail shouldBe zn.email
    pb.getCreated shouldBe zn.created.toEpochMilli
    pb.hasUpdated shouldBe false
    pb.getStatus shouldBe zn.status.toString
    pb.getId shouldBe zn.id
    pb.getShared shouldBe zn.shared
    pb.getAdminGroupId shouldBe zn.adminGroupId
    pb.hasLatestSync shouldBe false

    if (pb.hasConnection) {
      val pbconn = pb.getConnection
      val conn = zn.connection.get
      pbconn.getName shouldBe conn.name
      pbconn.getKey shouldBe conn.key.value
      pbconn.getKeyName shouldBe conn.keyName
      pbconn.getPrimaryServer shouldBe conn.primaryServer
    } else {
      zn.connection should not be defined
    }
    if (pb.hasTransferConnection) {
      val pbTransConn = pb.getTransferConnection
      val transConn = zn.transferConnection.get
      pbTransConn.getName shouldBe transConn.name
      pbTransConn.getKey shouldBe transConn.key.value
      pbTransConn.getKeyName shouldBe transConn.keyName
      pbTransConn.getPrimaryServer shouldBe transConn.primaryServer
    } else {
      zn.transferConnection should not be defined
    }

    if (pb.hasAcl) {
      aclMatches(pb.getAcl, zn.acl)
    } else {
      zn.acl.rules shouldBe empty
    }
  }

  def generateZoneMatches(pb: VinylDNSProto.GenerateZone, gzn: GenerateZone): Unit = {
    pb.getGroupId shouldBe gzn.groupId
    pb.getProvider shouldBe gzn.provider
    pb.getZoneName shouldBe gzn.zoneName
    pb.getStatus shouldBe gzn.status.toString
  }

  def rsMatches(pb: VinylDNSProto.RecordSet, rs: RecordSet): Assertion = {
    pb.getCreated shouldBe rs.created.toEpochMilli
    pb.getId shouldBe rs.id
    pb.getName shouldBe rs.name
    pb.getStatus shouldBe rs.status.toString
    pb.getTtl shouldBe rs.ttl
    pb.getTyp shouldBe rs.typ.toString
    pb.getZoneId shouldBe rs.zoneId

    pb.getRecordCount shouldBe rs.records.size
  }

  "ACLRule conversion" should {
    "convert from an ACL Rule" in {
      val pb = toPB(userAclRule)

      pb.getAccessLevel shouldBe userAclRule.accessLevel.toString
      pb.getDescription shouldBe userAclRule.description.get
      pb.getUserId shouldBe userAclRule.userId.get
      pb.hasGroupId shouldBe false
      pb.getRecordMask shouldBe userAclRule.recordMask.get
      pb.getRecordTypesList should contain theSameElementsAs userAclRule.recordTypes.map(_.toString)
    }

    "convert from protobuf to ACLRule" in {
      val pb = toPB(userAclRule)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe userAclRule
    }

    "convert for a group ACLRule" in {
      val pb = toPB(groupAclRule)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe groupAclRule
    }

    "convert for a rule with no record mask" in {
      val noRecordMask = userAclRule.copy(recordMask = None)
      val pb = toPB(noRecordMask)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe noRecordMask
    }

    "convert for a rule with no record types" in {
      val noRecordTypes = groupAclRule.copy(recordTypes = Set.empty)
      val pb = toPB(noRecordTypes)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe noRecordTypes
    }

    "convert for a rule with no description" in {
      val noDescription = groupAclRule.copy(description = None)
      val pb = toPB(noDescription)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe noDescription
    }

    "convert for a rule with both group and user id" in {
      val bothUserAndGroup = groupAclRule.copy(userId = Some("johnny"))
      val pb = toPB(bothUserAndGroup)
      val convertedRule = fromPB(pb)

      convertedRule shouldBe bothUserAndGroup
    }
  }

  "ZoneConnection conversion" should {
    "convert from ZoneConnection" in {
      val pb = toPB(zoneConnection)

      pb.getKey shouldBe zoneConnection.key.value
      pb.getKeyName shouldBe zoneConnection.keyName
      pb.getPrimaryServer shouldBe zoneConnection.primaryServer
      pb.getName shouldBe zoneConnection.name
    }

    "convert from protobuf to ZoneConnection" in {
      val pb = toPB(zoneConnection)
      val conn = fromPB(pb)

      conn shouldBe zoneConnection
    }
  }

  "Zone conversion" should {
    "convert to protobuf for a Zone including a connection and a transferConnection" in {
      val pb = toPB(zone)

      zoneMatches(pb, zone)
    }

    "convert to protobuf for a Zone without acl rules" in {
      val emptyACL = zone.copy(acl = ZoneACL())
      zoneMatches(toPB(emptyACL), emptyACL)
    }

    "default the status to Active if the zone state is Pending" in {
      val pb = VinylDNSProto.Zone
        .newBuilder()
        .setId(zone.id)
        .setName(zone.name)
        .setEmail(zone.email)
        .setCreated(zone.created.toEpochMilli)
        .setStatus("Pending")
        .setAccount(zone.account)
        .setShared(zone.shared)
        .setAdminGroupId(zone.adminGroupId)

      val converted = fromPB(pb.build)

      converted.status shouldBe ZoneStatus.Active
    }

    "default the status to Active if the zone state is PendingUpdate" in {
      val pb = VinylDNSProto.Zone
        .newBuilder()
        .setId(zone.id)
        .setName(zone.name)
        .setEmail(zone.email)
        .setCreated(zone.created.toEpochMilli)
        .setStatus("PendingUpdate")
        .setAccount(zone.account)
        .setShared(zone.shared)
        .setAdminGroupId(zone.adminGroupId)

      val converted = fromPB(pb.build)

      converted.status shouldBe ZoneStatus.Active
    }

    "convert from a protobuf with only required fields" in {
      // build a proto that does not have any optional fields
      val pb = VinylDNSProto.Zone
        .newBuilder()
        .setId(zone.id)
        .setName(zone.name)
        .setEmail(zone.email)
        .setCreated(zone.created.toEpochMilli)
        .setStatus(zone.status.toString)
        .setAccount(zone.account)

      val convertedNoOptional = fromPB(pb.build)

      convertedNoOptional.acl shouldBe ZoneACL()
      convertedNoOptional.adminGroupId shouldBe "system"
      convertedNoOptional.shared shouldBe false
      convertedNoOptional.isTest shouldBe false
      convertedNoOptional.connection should not be defined
      convertedNoOptional.transferConnection should not be defined
      convertedNoOptional.updated should not be defined
      convertedNoOptional.latestSync should not be defined
    }

    "convert from protobuf to Zone" in {
      val pb = toPB(zone)
      val z = fromPB(pb)

      z shouldBe zone
    }

    "convert to protobuf for a Zone with an update date" in {
      val z = zone.copy(updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
      val pb = toPB(z)

      pb.getUpdated shouldBe z.updated.get.toEpochMilli
      fromPB(pb).updated shouldBe defined
    }

    "convert to protobuf for a Zone with a latest sync date" in {
      val z = zone.copy(latestSync = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
      val pb = toPB(z)

      pb.getLatestSync shouldBe z.latestSync.get.toEpochMilli
      fromPB(pb).latestSync shouldBe defined
    }

    "convert to protobuf for a Zone with a backendId" in {
      val z = zone.copy(backendId = Some("test-backend-id"))
      val pb = toPB(z)

      pb.getBackendId shouldBe "test-backend-id"
      fromPB(pb).backendId shouldBe defined
    }
  }


  "Generation Zone conversion" should {
    "convert to protobuf for a generate Zone including a connection" in {
      val pb = toPB(generateBindZone)

      generateZoneMatches(pb, generateBindZone)
    }

    "convert from a protobuf with only required fields" in {
      // build a proto that does not have any optional fields
      val pb = VinylDNSProto.GenerateZone
        .newBuilder()
        .setId(generateBindZone.id)
        .setEmail(generateBindZone.email)
        .setGroupId(generateBindZone.groupId)
        .setProvider(generateBindZone.provider)
        .setZoneName(generateBindZone.zoneName)
        .setStatus(generateBindZone.status.toString)
        .setCreated(generateBindZone.created.toEpochMilli)

      val convertedNoOptional = fromPB(pb.build)

      convertedNoOptional.id shouldBe generateBindZone.id
      convertedNoOptional.groupId shouldBe generateBindZone.groupId
      convertedNoOptional.provider shouldBe "bind"
      convertedNoOptional.zoneName shouldBe generateBindZone.zoneName
      convertedNoOptional.status shouldBe generateBindZone.status

    }

    "convert from protobuf to Generate Zone" in {
      val pb = toPB(generateBindZone)
      val z = fromPB(pb)

      z shouldBe generateBindZone
    }
  }

  "Recordset conversion" should {
    "convert to protobuf for a recordset" in {
      val pb = toPB(aRs)

      pb.getCreated shouldBe aRs.created.toEpochMilli
      pb.getId shouldBe aRs.id
      pb.getName shouldBe aRs.name
      pb.getStatus shouldBe aRs.status.toString
      pb.getTtl shouldBe aRs.ttl
      pb.getTyp shouldBe aRs.typ.toString
      pb.getZoneId shouldBe aRs.zoneId
      pb.hasOwnerGroupId shouldBe false

      pb.getRecordCount shouldBe 2
    }

    "convert from protobuf for a recordset" in {
      val pb = toPB(aRs)
      val rs = fromPB(pb)

      rs shouldBe aRs
    }

    "convert to protobuf for a recordset with ownerGroupId defined" in {
      val rs = aRs.copy(ownerGroupId = Some("ownerGroupId"))
      val pb = toPB(rs)

      pb.hasOwnerGroupId shouldBe true
      Some(pb.getOwnerGroupId) shouldBe rs.ownerGroupId
    }

    "convert from protobuf for a recordset with ownerGroupId defined" in {
      val rs = aRs.copy(ownerGroupId = Some("ownerGroupId"))
      val pb = toPB(rs)

      fromPB(pb) shouldBe rs
    }

    "convert from protobuf for AAAA recordset" in {
      fromPB(toPB(aaaa)) shouldBe aaaa
    }

    "convert from protobuf for CNAME recordset" in {
      fromPB(toPB(cname)) shouldBe cname
    }

    "convert from protobuf for MX recordset" in {
      fromPB(toPB(mx)) shouldBe mx
    }

    "convert from protobuf for NS recordset" in {
      fromPB(toPB(ns)) shouldBe ns
    }

    "convert from protobuf for PTR recordset" in {
      fromPB(toPB(ptr)) shouldBe ptr
    }

    "convert from protobuf for SOA recordset" in {
      fromPB(toPB(soa)) shouldBe soa
    }

    "convert from protobuf for SPF recordset" in {
      fromPB(toPB(spf)) shouldBe spf
    }

    "convert from protobuf for SRV recordset" in {
      fromPB(toPB(srv)) shouldBe srv
    }

    "convert from protobuf for NAPTR recordset" in {
      fromPB(toPB(naptr)) shouldBe naptr
    }

    "convert from protobuf for SSHFP recordset" in {
      fromPB(toPB(sshfp)) shouldBe sshfp
    }

    "convert from protobuf for TXT recordset" in {
      fromPB(toPB(txt)) shouldBe txt
    }

    "convert from protobuf for DS recordset" in {
      fromPB(toPB(ds)) shouldBe ds
    }

    "convert to a protobuf for a recordset without an update date" in {
      val rs = aRs.copy(updated = None)
      val pb = toPB(rs)

      pb.hasUpdated shouldBe false
      fromPB(pb).updated should not be defined
    }

    "convert to protobuf for AAAA data" in {
      val pb = toPB(AAAAData("10.1.1.1"))
      pb.getAddress shouldBe "10.1.1.1"
    }

    "convert from protobuf for AAAA data" in {
      val pb = toPB(AAAAData("10.1.1.1"))
      val data = fromPB(pb)

      data.address shouldBe "10.1.1.1"
    }

    "convert to protobuf for CNAME data" in {
      val pb = toPB(CNAMEData(Fqdn("www.")))
      pb.getCname shouldBe "www."
    }

    "convert from protobuf for CNAME data" in {
      val pb = toPB(CNAMEData(Fqdn("www.")))
      val data = fromPB(pb)

      data.cname.fqdn shouldBe "www."
    }

    "convert to protobuf for MX data" in {
      val mxData = MXData(100, Fqdn("mx.test.com"))
      val pb = toPB(mxData)
      pb.getPreference shouldBe mxData.preference
      pb.getExchange shouldBe mxData.exchange.fqdn
    }

    "convert from protobuf for MX data" in {
      val mxData = MXData(100, Fqdn("mx.test.com"))
      val pb = toPB(mxData)
      val data = fromPB(pb)

      data shouldBe mxData
    }

    "convert to protobuf for NS data" in {
      val nsData = NSData(Fqdn("ns1.test.com"))
      val pb = toPB(nsData)
      pb.getNsdname shouldBe nsData.nsdname.fqdn
    }

    "convert from protobuf for NS data" in {
      val nsData = NSData(Fqdn("ns1.test.com"))
      val pb = toPB(nsData)
      val data = fromPB(pb)

      data shouldBe nsData
    }

    "convert to protobuf for PTR data" in {
      val from = PTRData(Fqdn("ns1.test.com"))
      val pb = toPB(from)
      pb.getPtrdname shouldBe from.ptrdname.fqdn
    }

    "convert from protobuf for PTR data" in {
      val from = PTRData(Fqdn("ns1.test.com"))
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for SOA data" in {
      val from = SOAData(Fqdn("name"), "name", 1, 2, 3, 4, 5)
      val pb = toPB(from)
      pb.getExpire shouldBe from.expire
      pb.getMinimum shouldBe from.minimum
      pb.getMname shouldBe from.mname.fqdn
      pb.getRefresh shouldBe from.refresh
      pb.getRetry shouldBe from.retry
      pb.getRname shouldBe from.rname
      pb.getSerial shouldBe from.serial
    }

    "convert from protobuf for SOA data" in {
      val from = SOAData(Fqdn("name"), "name", 1, 2, 3, 4, 5)
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for SPF data" in {
      val from = SPFData("spf")
      val pb = toPB(from)
      pb.getText shouldBe from.text
    }

    "convert from protobuf for SPF data" in {
      val from = SPFData("spf")
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for SRV data" in {
      val from = SRVData(1, 2, 3, Fqdn("target"))
      val pb = toPB(from)
      pb.getPort shouldBe from.port
      pb.getPriority shouldBe from.priority
      pb.getTarget shouldBe from.target.fqdn
      pb.getWeight shouldBe from.weight
    }

    "convert from protobuf for SRV data" in {
      val from = SRVData(1, 2, 3, Fqdn("target"))
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for NAPTR data" in {
      val from = NAPTRData(1, 2, "U", "E2U+sip", "!.*!test.!", Fqdn("target"))
      val pb = toPB(from)
      pb.getOrder shouldBe from.order
      pb.getPreference shouldBe from.preference
      pb.getFlags shouldBe from.flags
      pb.getService shouldBe from.service
      pb.getRegexp shouldBe from.regexp
      pb.getReplacement shouldBe from.replacement.fqdn
    }

    "convert from protobuf for NAPTR data" in {
      val from = NAPTRData(1, 2, "U", "E2U+sip", "!.*!test.!", Fqdn("target"))
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for SSHFP data" in {
      val from = SSHFPData(1, 2, "fingerprint")
      val pb = toPB(from)
      pb.getAlgorithm shouldBe from.algorithm
      pb.getFingerPrint shouldBe from.fingerprint
      pb.getTyp shouldBe from.typ
    }

    "convert from protobuf for SSHFP data" in {
      val from = SSHFPData(1, 2, "fingerprint")
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for TXT data" in {
      val from = TXTData("text")
      val pb = toPB(from)
      pb.getText shouldBe from.text
    }

    "convert from protobuf for TXT data" in {
      val from = TXTData("text")
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }
  }

  "ZoneChange conversion" should {
    "convert to protobuf from ZoneChange" in {
      val pb = toPB(zoneChange)
      pb.getCreated shouldBe zoneChange.created.toEpochMilli
      pb.getId shouldBe zoneChange.id
      pb.getStatus shouldBe zoneChange.status.toString
      pb.getSystemMessage shouldBe zoneChange.systemMessage.get.toString

      zoneMatches(pb.getZone, zoneChange.zone)
    }

    "convert to protobuf from ZoneChange without system message" in {
      val pb = toPB(zoneChange.copy(systemMessage = None))
      pb.hasSystemMessage shouldBe false
      fromPB(pb).systemMessage should not be defined
    }

    "convert from protobuf to ZoneChange" in {
      fromPB(toPB(zoneChange)) shouldBe zoneChange
    }
  }

  "RecordSetChange conversion" should {
    "convert to protobuf from RecordSetChange" in {
      val chg = rsChange(aRs)
      val pb = toPB(chg)

      pb.getCreated shouldBe chg.created.toEpochMilli
      pb.getId shouldBe chg.id
      pb.getStatus shouldBe chg.status.toString
      pb.getSystemMessage shouldBe chg.systemMessage.get
      pb.getTyp shouldBe chg.changeType.toString
      pb.hasUpdates shouldBe true
      pb.getUserId shouldBe chg.userId
      pb.hasZone shouldBe true
      pb.getSingleBatchChangeIdsList.asScala.toList shouldBe singleBatchChangeIds

      zoneMatches(pb.getZone, chg.zone)
      rsMatches(pb.getRecordSet, chg.recordSet)
      rsMatches(pb.getUpdates, chg.updates.get)
    }

    "convert from protobuf to RecordSetChange" in {
      val chg = rsChange(aRs)
      fromPB(toPB(chg)) shouldBe chg
    }

    "convert to protobuf without system message for RecordSetChange" in {
      val chg = rsChange(soa).copy(systemMessage = None)
      toPB(chg).hasSystemMessage shouldBe false
      fromPB(toPB(chg)).systemMessage should not be defined
    }

    "convert to protobuf without updates for RecordSetChange" in {
      val chg = rsChange(sshfp).copy(updates = None)
      toPB(chg).hasUpdates shouldBe false
      fromPB(toPB(chg)).updates should not be defined
    }
  }

  "User conversion" should {
    "convert to/from protobuf with user defaults" in {
      val user = User("testName", "testAccess", Encrypted("testSecret"))
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      pb.hasFirstName shouldBe false
      pb.hasLastName shouldBe false
      pb.hasEmail shouldBe false
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe user.isSuper
      pb.getLockStatus shouldBe "Unlocked"
      pb.getIsSupport shouldBe false
      pb.getIsTest shouldBe false

      fromPB(pb) shouldBe user
    }

    "convert to/from protobuf with firstname, lastname, email, and isSupport" in {
      val user = User(
        "testName",
        "testAccess",
        Encrypted("testSecret"),
        firstName = Some("testFirstName"),
        lastName = Some("testLastName"),
        email = Some("testEmail"),
        isSupport = true
      )
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      Some(pb.getFirstName) shouldBe user.firstName
      Some(pb.getLastName) shouldBe user.lastName
      Some(pb.getEmail) shouldBe user.email
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe user.isSuper
      pb.getLockStatus shouldBe "Unlocked"
      pb.getIsSupport shouldBe true
      pb.getIsTest shouldBe false

      fromPB(pb) shouldBe user
    }

    "convert to/from protobuf with superUser true" in {
      val user = User("testName", "testAccess", Encrypted("testSecret"), isSuper = true)
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      pb.hasFirstName shouldBe false
      pb.hasLastName shouldBe false
      pb.hasEmail shouldBe false
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe user.isSuper
      pb.getLockStatus shouldBe "Unlocked"
      pb.getIsTest shouldBe false

      fromPB(pb) shouldBe user
    }

    "convert to/from protobuf with locked user" in {
      val user = User("testName", "testAccess", Encrypted("testSecret"), lockStatus = LockStatus.Locked)
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      pb.hasFirstName shouldBe false
      pb.hasLastName shouldBe false
      pb.hasEmail shouldBe false
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe user.isSuper
      pb.getLockStatus shouldBe "Locked"
      pb.getIsTest shouldBe false

      fromPB(pb) shouldBe user
    }

    "convert to/from protobuf with test user" in {
      val user = User("testName", "testAccess", Encrypted("testSecret"), isTest = true)
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      pb.hasFirstName shouldBe false
      pb.hasLastName shouldBe false
      pb.hasEmail shouldBe false
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe false
      pb.getLockStatus shouldBe "Unlocked"
      pb.getIsTest shouldBe true

      fromPB(pb) shouldBe user
    }

    "convert to/from protobuf with supportAdmin true" in {
      val user = User("testName", "testAccess", Encrypted("testSecret"), isSupport = true)
      val pb = toPB(user)

      pb.getUserName shouldBe user.userName
      pb.getAccessKey shouldBe user.accessKey
      pb.getSecretKey shouldBe user.secretKey.value
      pb.hasFirstName shouldBe false
      pb.hasLastName shouldBe false
      pb.hasEmail shouldBe false
      pb.getCreated shouldBe user.created.toEpochMilli
      pb.getId shouldBe user.id
      pb.getIsSuper shouldBe user.isSuper
      pb.getLockStatus shouldBe "Unlocked"
      pb.getIsSupport shouldBe true

      fromPB(pb) shouldBe user
    }
  }

  "User change conversion" should {
    "convert to/from protobuf for CreateUser" in {
      val user = User("createUser", "createUserAccess", Encrypted("createUserSecret"))
      val createChange = CreateUser(user, "createUserId", user.created)
      val pb = toPb(createChange)

      pb.getChangeType shouldBe UserChangeType.Create.value

      new User(
        pb.getNewUser.getUserName,
        pb.getNewUser.getAccessKey,
        Encrypted(pb.getNewUser.getSecretKey),
        created = user.created,
        id = user.id
      ) shouldBe user

      pb.getMadeByUserId shouldBe createChange.madeByUserId
      pb.getCreated shouldBe createChange.created.toEpochMilli
      pb.getId shouldBe createChange.id

      fromPb(pb) shouldBe createChange
    }

    "convert to/from protobuf for UpdateUser" in {
      val oldUser = User("updateUser", "updateUserAccess", Encrypted("updateUserSecret"))
      val newUser = oldUser.copy(userName = "updateUserNewName")
      val updateChange = UpdateUser(newUser, "createUserId", newUser.created, oldUser)
      val pb = toPb(updateChange)

      pb.getChangeType shouldBe UserChangeType.Update.value

      new User(
        pb.getNewUser.getUserName,
        pb.getNewUser.getAccessKey,
        Encrypted(pb.getNewUser.getSecretKey),
        created = newUser.created,
        id = newUser.id
      ) shouldBe newUser

      pb.getMadeByUserId shouldBe updateChange.madeByUserId
      pb.getCreated shouldBe updateChange.created.toEpochMilli

      new User(
        pb.getOldUser.getUserName,
        pb.getOldUser.getAccessKey,
        Encrypted(pb.getOldUser.getSecretKey),
        created = oldUser.created,
        id = oldUser.id
      ) shouldBe oldUser

      pb.getId shouldBe updateChange.id

      fromPb(pb) shouldBe updateChange
    }
  }
}
