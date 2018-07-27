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

package vinyldns.api.protobuf

import org.joda.time.DateTime
import org.scalatest.{Assertion, Matchers, OptionValues, WordSpec}
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone._
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._

class ProtobufConversionsSpec
    extends WordSpec
    with Matchers
    with ProtobufConversions
    with OptionValues {

  private val zoneConnection = ZoneConnection("name", "keyName", "key", "server")

  private val zoneId = "test.zone.id"

  private val userAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))

  private val groupAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    None,
    Some("group"),
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME))

  private val zoneAcl = ZoneACL(Set(userAclRule, groupAclRule))

  private val zone = Zone(
    "test.zone.actor.zone",
    "test@test.com",
    connection = Some(ZoneConnection("connection.ok", "keyName", "key", "10.1.1.1")),
    transferConnection = Some(ZoneConnection("connection.ok", "keyName", "key", "10.1.1.2")),
    shared = true,
    id = zoneId,
    acl = zoneAcl,
    adminGroupId = "test-group-id"
  )
  private val zoneChange = ZoneChange(
    zone,
    "system",
    ZoneChangeType.Update,
    ZoneChangeStatus.Complete,
    DateTime.now,
    Some("hello"))
  private val aRs = RecordSet(
    "id",
    "test.rs",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    Some(DateTime.now),
    List(AData("10.1.1.1"), AData("10.2.2.2")))
  private val aaaa = RecordSet(
    zone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AAAAData("10.1.1.1")))
  private val cname = RecordSet(
    zone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(CNAMEData("cname")))
  private val mx = RecordSet(
    zone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(MXData(100, "exchange")))
  private val ns = RecordSet(
    zone.id,
    "ns",
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData("nsrecordname")))
  private val ptr = RecordSet(
    zone.id,
    "ptr",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))
  private val soa = RecordSet(
    zone.id,
    "soa",
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SOAData("name", "name", 1, 2, 3, 4, 5)))
  private val spf = RecordSet(
    zone.id,
    "soa",
    RecordType.SPF,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SPFData("spf")))
  private val srv = RecordSet(
    zone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SRVData(1, 2, 3, "target")))
  private val sshfp = RecordSet(
    zone.id,
    "sshfp",
    RecordType.SSHFP,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SSHFPData(1, 2, "fingerprint")))
  private val txt = RecordSet(
    zone.id,
    "txt",
    RecordType.TXT,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(TXTData("text")))

  private val singleBatchChangeIds = List("batch1", "batch2")

  def rsChange(rs: RecordSet): RecordSetChange =
    RecordSetChange(
      zone,
      rs,
      "system",
      RecordSetChangeType.Update,
      RecordSetChangeStatus.Validated,
      DateTime.now,
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
    pb.getCreated shouldBe zn.created.getMillis
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
      pbconn.getKey shouldBe conn.key
      pbconn.getKeyName shouldBe conn.keyName
      pbconn.getPrimaryServer shouldBe conn.primaryServer
    } else {
      zn.connection should not be defined
    }
    if (pb.hasTransferConnection) {
      val pbTransConn = pb.getTransferConnection
      val transConn = zn.transferConnection.get
      pbTransConn.getName shouldBe transConn.name
      pbTransConn.getKey shouldBe transConn.key
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

  def rsMatches(pb: VinylDNSProto.RecordSet, rs: RecordSet): Assertion = {
    pb.getCreated shouldBe rs.created.getMillis
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

      pb.getKey shouldBe zoneConnection.key
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
        .setCreated(zone.created.getMillis)
        .setStatus("Pending")
        .setAccount(zone.account)
        .setShared(zone.shared)
        .setAdminGroupId(zone.adminGroupId)

      val converted = fromPB(pb.build)

      converted.status shouldBe ZoneStatus.Active
    }

    "convert from a protobuf that has no zone acl present" in {
      // build a proto that does not have a zone acl set (like current zones in the zone repo)
      val pb = VinylDNSProto.Zone
        .newBuilder()
        .setId(zone.id)
        .setName(zone.name)
        .setEmail(zone.email)
        .setCreated(zone.created.getMillis)
        .setStatus(zone.status.toString)
        .setAccount(zone.account)
        .setShared(zone.shared)
        .setAdminGroupId(zone.adminGroupId)

      val convertedFromNoACL = fromPB(pb.build)

      convertedFromNoACL.acl shouldBe ZoneACL()
    }

    "convert from a protobuf that has no adminGroupId present" in {
      // proto without adminGroupId should have default "system"
      val pb = VinylDNSProto.Zone
        .newBuilder()
        .setId(zone.id)
        .setName(zone.name)
        .setEmail(zone.email)
        .setCreated(zone.created.getMillis)
        .setStatus(zone.status.toString)
        .setAccount(zone.account)
        .setShared(zone.shared)

      val convertedFromNoAdminGroupId = fromPB(pb.build)

      convertedFromNoAdminGroupId.adminGroupId shouldBe "system"
    }

    "convert from protobuf to Zone" in {
      val pb = toPB(zone)
      val z = fromPB(pb)

      z shouldBe zone
    }

    "convert to protobuf for a Zone with an update date" in {
      val z = zone.copy(updated = Some(DateTime.now))
      val pb = toPB(z)

      pb.getUpdated shouldBe z.updated.get.getMillis
      fromPB(pb).updated shouldBe defined
    }

    "convert to protobuf for a Zone with a latest sync date" in {
      val z = zone.copy(latestSync = Some(DateTime.now))
      val pb = toPB(z)

      pb.getLatestSync shouldBe z.latestSync.get.getMillis
      fromPB(pb).latestSync shouldBe defined
    }

    "convert to protobuf for a Zone without a connection" in {
      val z = zone.copy(connection = None)
      val pb = toPB(z)

      pb.hasConnection shouldBe false
      fromPB(pb).connection should not be defined
    }

    "convert to protobuf for a Zone without a transferConnection" in {
      val z = zone.copy(transferConnection = None)
      val pb = toPB(z)

      pb.hasTransferConnection shouldBe false
      fromPB(pb).transferConnection should not be defined
    }
  }

  "Recordset conversion" should {
    "convert to protobuf for a recordset" in {
      val pb = toPB(aRs)

      pb.getCreated shouldBe aRs.created.getMillis
      pb.getId shouldBe aRs.id
      pb.getName shouldBe aRs.name
      pb.getStatus shouldBe aRs.status.toString
      pb.getTtl shouldBe aRs.ttl
      pb.getTyp shouldBe aRs.typ.toString
      pb.getZoneId shouldBe aRs.zoneId

      pb.getRecordCount shouldBe 2
    }

    "convert from protobuf for a recordset" in {
      val pb = toPB(aRs)
      val rs = fromPB(pb)

      rs shouldBe aRs
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

    "convert from protobuf for SSHFP recordset" in {
      fromPB(toPB(sshfp)) shouldBe sshfp
    }

    "convert from protobuf for TXT recordset" in {
      fromPB(toPB(txt)) shouldBe txt
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
      val pb = toPB(CNAMEData("www."))
      pb.getCname shouldBe "www."
    }

    "convert from protobuf for CNAME data" in {
      val pb = toPB(CNAMEData("www."))
      val data = fromPB(pb)

      data.cname shouldBe "www."
    }

    "convert to protobuf for MX data" in {
      val mxData = MXData(100, "mx.test.com")
      val pb = toPB(mxData)
      pb.getPreference shouldBe mxData.preference
      pb.getExchange shouldBe mxData.exchange
    }

    "convert from protobuf for MX data" in {
      val mxData = MXData(100, "mx.test.com")
      val pb = toPB(mxData)
      val data = fromPB(pb)

      data shouldBe mxData
    }

    "convert to protobuf for NS data" in {
      val nsData = NSData("ns1.test.com")
      val pb = toPB(nsData)
      pb.getNsdname shouldBe nsData.nsdname
    }

    "convert from protobuf for NS data" in {
      val nsData = NSData("ns1.test.com")
      val pb = toPB(nsData)
      val data = fromPB(pb)

      data shouldBe nsData
    }

    "convert to protobuf for PTR data" in {
      val from = PTRData("ns1.test.com")
      val pb = toPB(from)
      pb.getPtrdname shouldBe from.ptrdname
    }

    "convert from protobuf for PTR data" in {
      val from = PTRData("ns1.test.com")
      val pb = toPB(from)
      val data = fromPB(pb)

      data shouldBe from
    }

    "convert to protobuf for SOA data" in {
      val from = SOAData("name", "name", 1, 2, 3, 4, 5)
      val pb = toPB(from)
      pb.getExpire shouldBe from.expire
      pb.getMinimum shouldBe from.minimum
      pb.getMname shouldBe from.mname
      pb.getRefresh shouldBe from.refresh
      pb.getRetry shouldBe from.retry
      pb.getRname shouldBe from.rname
      pb.getSerial shouldBe from.serial
    }

    "convert from protobuf for SOA data" in {
      val from = SOAData("name", "name", 1, 2, 3, 4, 5)
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
      val from = SRVData(1, 2, 3, "target")
      val pb = toPB(from)
      pb.getPort shouldBe from.port
      pb.getPriority shouldBe from.priority
      pb.getTarget shouldBe from.target
      pb.getWeight shouldBe from.weight
    }

    "convert from protobuf for SRV data" in {
      val from = SRVData(1, 2, 3, "target")
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
      pb.getCreated shouldBe zoneChange.created.getMillis
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

      pb.getCreated shouldBe chg.created.getMillis
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
}
