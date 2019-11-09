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

import com.google.protobuf.ByteString
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import scodec.bits.ByteVector
import vinyldns.core.domain.membership.UserChange.{CreateUser, UpdateUser}
import vinyldns.core.domain.membership.{LockStatus, User, UserChange, UserChangeType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.domain.{record, zone}
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.util.Try

trait ProtobufConversions {

  val protoLogger: Logger = LoggerFactory.getLogger("vinyldns.core.protobuf.ProtobufConversions")

  def fromPB(rule: VinylDNSProto.ACLRule): ACLRule =
    ACLRule(
      accessLevel = AccessLevel.withName(rule.getAccessLevel),
      description = if (rule.hasDescription) Option(rule.getDescription) else None,
      userId = if (rule.hasUserId) Option(rule.getUserId) else None,
      groupId = if (rule.hasGroupId) Option(rule.getGroupId) else None,
      recordMask = if (rule.hasRecordMask) Option(rule.getRecordMask) else None,
      recordTypes =
        if (rule.getRecordTypesCount > 0)
          rule.getRecordTypesList.asScala.map(RecordType.withName).toSet
        else Set.empty[RecordType]
    )

  def fromPB(acl: VinylDNSProto.ZoneACL): ZoneACL =
    if (acl.getRulesCount > 0) ZoneACL(acl.getRulesList.asScala.map(fromPB).toSet)
    else ZoneACL()

  def fromPB(chg: VinylDNSProto.RecordSetChange): RecordSetChange = {
    val status = Try(RecordSetChangeStatus.withName(chg.getStatus))
      .getOrElse {
        protoLogger.error(
          s"Encountered unexpected status in RecordSetChange.fromPB: ${chg.getStatus}"
        )
        // deprecated Submitted, Validated, Applied, Verified -- setting all to "Pending"
        RecordSetChangeStatus.Pending
      }
    record.RecordSetChange(
      zone = fromPB(chg.getZone),
      recordSet = fromPB(chg.getRecordSet),
      userId = chg.getUserId,
      changeType = RecordSetChangeType.withName(chg.getTyp),
      status = status,
      created = new DateTime(chg.getCreated),
      systemMessage = if (chg.hasSystemMessage) Option(chg.getSystemMessage) else None,
      updates = if (chg.hasUpdates) Option(fromPB(chg.getUpdates)) else None,
      id = chg.getId,
      singleBatchChangeIds = chg.getSingleBatchChangeIdsList.asScala.toList
    )
  }

  def fromPB(rs: VinylDNSProto.RecordSet): RecordSet =
    record.RecordSet(
      zoneId = rs.getZoneId,
      name = rs.getName,
      typ = RecordType.withName(rs.getTyp),
      ttl = rs.getTtl,
      status = RecordSetStatus.withName(rs.getStatus),
      created = new DateTime(rs.getCreated),
      updated = if (rs.hasUpdated) Some(new DateTime(rs.getUpdated)) else None,
      id = rs.getId,
      records =
        rs.getRecordList.asScala.map(rd => fromPB(rd, RecordType.withName(rs.getTyp))).toList,
      account = rs.getAccount,
      ownerGroupId = if (rs.hasOwnerGroupId) Some(rs.getOwnerGroupId) else None
    )

  def fromPB(zn: VinylDNSProto.Zone): Zone = {
    val pbStatus = zn.getStatus
    val status =
      if (pbStatus.startsWith("Pending")) ZoneStatus.Active
      else ZoneStatus.withName(pbStatus)

    zone.Zone(
      name = zn.getName,
      email = zn.getEmail,
      status = status,
      created = new DateTime(zn.getCreated),
      updated = if (zn.hasUpdated) Some(new DateTime(zn.getUpdated)) else None,
      id = zn.getId,
      connection = if (zn.hasConnection) Some(fromPB(zn.getConnection)) else None,
      transferConnection =
        if (zn.hasTransferConnection) Some(fromPB(zn.getTransferConnection)) else None,
      account = zn.getAccount,
      shared = zn.getShared,
      acl = if (zn.hasAcl) fromPB(zn.getAcl) else ZoneACL(),
      adminGroupId = zn.getAdminGroupId,
      latestSync = if (zn.hasLatestSync) Some(new DateTime(zn.getLatestSync)) else None,
      isTest = zn.getIsTest,
      backendId = if (zn.hasBackendId) Some(zn.getBackendId) else None
    )
  }

  def fromPB(zc: VinylDNSProto.ZoneConnection): ZoneConnection =
    ZoneConnection(
      zc.getName,
      zc.getKeyName,
      zc.getKey,
      zc.getPrimaryServer
    )

  def fromPB(chg: VinylDNSProto.ZoneChange): ZoneChange =
    ZoneChange(
      zone = fromPB(chg.getZone),
      userId = chg.getUserId,
      changeType = ZoneChangeType.withName(chg.getTyp),
      status = ZoneChangeStatus.withName(chg.getStatus),
      created = new DateTime(chg.getCreated),
      systemMessage = if (chg.hasSystemMessage) Option(chg.getSystemMessage) else None,
      id = chg.getId
    )

  def toPB(chg: RecordSetChange): VinylDNSProto.RecordSetChange = {
    val builder = VinylDNSProto.RecordSetChange
      .newBuilder()
      .setCreated(chg.created.getMillis)
      .setId(chg.id)
      .setRecordSet(toPB(chg.recordSet))
      .setStatus(chg.status.toString)
      .setTyp(chg.changeType.toString)
      .setUserId(chg.userId)
      .setZone(toPB(chg.zone))

    chg.updates.map(toPB).foreach(builder.setUpdates)
    chg.systemMessage.foreach(builder.setSystemMessage)
    chg.singleBatchChangeIds.foreach(builder.addSingleBatchChangeIds)

    builder.build()
  }

  def fromPB(rd: VinylDNSProto.RecordData, rt: RecordType): RecordData =
    rt match {
      case RecordType.A => fromPB(VinylDNSProto.AData.parseFrom(rd.getData))
      case RecordType.AAAA => fromPB(VinylDNSProto.AAAAData.parseFrom(rd.getData))
      case RecordType.CNAME => fromPB(VinylDNSProto.CNAMEData.parseFrom(rd.getData))
      case RecordType.DS => fromPB(VinylDNSProto.DSData.parseFrom(rd.getData))
      case RecordType.MX => fromPB(VinylDNSProto.MXData.parseFrom(rd.getData))
      case RecordType.NS => fromPB(VinylDNSProto.NSData.parseFrom(rd.getData))
      case RecordType.PTR => fromPB(VinylDNSProto.PTRData.parseFrom(rd.getData))
      case RecordType.SOA => fromPB(VinylDNSProto.SOAData.parseFrom(rd.getData))
      case RecordType.SPF => fromPB(VinylDNSProto.SPFData.parseFrom(rd.getData))
      case RecordType.SRV => fromPB(VinylDNSProto.SRVData.parseFrom(rd.getData))
      case RecordType.NAPTR => fromPB(VinylDNSProto.NAPTRData.parseFrom(rd.getData))
      case RecordType.SSHFP => fromPB(VinylDNSProto.SSHFPData.parseFrom(rd.getData))
      case RecordType.TXT => fromPB(VinylDNSProto.TXTData.parseFrom(rd.getData))
    }

  def fromPB(data: VinylDNSProto.AData): AData = AData(data.getAddress)

  def fromPB(data: VinylDNSProto.AAAAData): AAAAData = AAAAData(data.getAddress)

  def fromPB(data: VinylDNSProto.CNAMEData): CNAMEData = CNAMEData(data.getCname)

  def fromPB(data: VinylDNSProto.DSData): DSData =
    DSData(
      data.getKeyTag,
      DnsSecAlgorithm(data.getAlgorithm),
      DigestType(data.getDigestType),
      ByteVector.apply(data.getDigest.asReadOnlyByteBuffer())
    )

  def fromPB(data: VinylDNSProto.MXData): MXData = MXData(data.getPreference, data.getExchange)

  def fromPB(data: VinylDNSProto.NSData): NSData = NSData(data.getNsdname)

  def fromPB(data: VinylDNSProto.PTRData): PTRData = PTRData(data.getPtrdname)

  def fromPB(data: VinylDNSProto.SOAData): SOAData =
    SOAData(
      data.getMname,
      data.getRname,
      data.getSerial,
      data.getRefresh,
      data.getRetry,
      data.getExpire,
      data.getMinimum
    )

  def fromPB(data: VinylDNSProto.SPFData): SPFData = SPFData(data.getText)

  def fromPB(data: VinylDNSProto.SRVData): SRVData =
    SRVData(data.getPriority, data.getWeight, data.getPort, data.getTarget)

  def fromPB(data: VinylDNSProto.NAPTRData): NAPTRData =
    NAPTRData(
      data.getOrder,
      data.getPreference,
      data.getFlags,
      data.getService,
      data.getRegexp,
      data.getReplacement
    )

  def fromPB(data: VinylDNSProto.SSHFPData): SSHFPData =
    SSHFPData(data.getAlgorithm, data.getTyp, data.getFingerPrint)

  def fromPB(data: VinylDNSProto.TXTData): TXTData = TXTData(data.getText)

  def toPB(rule: ACLRule): VinylDNSProto.ACLRule = {
    val builder = VinylDNSProto.ACLRule
      .newBuilder()
      .setAccessLevel(rule.accessLevel.toString)

    rule.recordTypes.foreach(typ => builder.addRecordTypes(typ.toString))
    rule.description.foreach(builder.setDescription)
    rule.userId.foreach(builder.setUserId)
    rule.groupId.foreach(builder.setGroupId)
    rule.recordMask.foreach(builder.setRecordMask)

    builder.build()
  }

  def toPB(acl: ZoneACL): VinylDNSProto.ZoneACL =
    VinylDNSProto.ZoneACL
      .newBuilder()
      .addAllRules(acl.rules.map(toPB).asJava)
      .build()

  def toPB(data: AData): VinylDNSProto.AData =
    VinylDNSProto.AData.newBuilder().setAddress(data.address).build()

  def toPB(data: AAAAData): VinylDNSProto.AAAAData =
    VinylDNSProto.AAAAData.newBuilder().setAddress(data.address).build()

  def toPB(data: CNAMEData): VinylDNSProto.CNAMEData =
    VinylDNSProto.CNAMEData.newBuilder().setCname(data.cname).build()

  def toPB(data: DSData): VinylDNSProto.DSData =
    VinylDNSProto.DSData
      .newBuilder()
      .setKeyTag(data.keyTag)
      .setAlgorithm(data.algorithm.value)
      .setDigestType(data.digestType.value)
      .setDigest(ByteString.copyFrom(data.digest.toByteBuffer))
      .build()

  def toPB(data: MXData): VinylDNSProto.MXData =
    VinylDNSProto.MXData
      .newBuilder()
      .setPreference(data.preference)
      .setExchange(data.exchange)
      .build()

  def toPB(data: PTRData): VinylDNSProto.PTRData =
    VinylDNSProto.PTRData.newBuilder().setPtrdname(data.ptrdname).build()

  def toPB(data: NSData): VinylDNSProto.NSData =
    VinylDNSProto.NSData.newBuilder().setNsdname(data.nsdname).build()

  def toPB(data: SOAData): VinylDNSProto.SOAData =
    VinylDNSProto.SOAData
      .newBuilder()
      .setRname(data.rname)
      .setMname(data.mname)
      .setExpire(data.expire)
      .setMinimum(data.minimum)
      .setRefresh(data.refresh)
      .setRetry(data.retry)
      .setSerial(data.serial)
      .build()

  def toPB(data: SPFData): VinylDNSProto.SPFData =
    VinylDNSProto.SPFData.newBuilder().setText(data.text).build()

  def toPB(data: SRVData): VinylDNSProto.SRVData =
    VinylDNSProto.SRVData
      .newBuilder()
      .setPort(data.port)
      .setPriority(data.priority)
      .setTarget(data.target)
      .setWeight(data.weight)
      .build()

  def toPB(data: NAPTRData): VinylDNSProto.NAPTRData =
    VinylDNSProto.NAPTRData
      .newBuilder()
      .setOrder(data.order)
      .setPreference(data.preference)
      .setFlags(data.flags)
      .setService(data.service)
      .setRegexp(data.regexp)
      .setReplacement(data.replacement)
      .build()

  def toPB(data: SSHFPData): VinylDNSProto.SSHFPData =
    VinylDNSProto.SSHFPData
      .newBuilder()
      .setAlgorithm(data.algorithm)
      .setFingerPrint(data.fingerprint)
      .setTyp(data.typ)
      .build()

  def toPB(data: TXTData): VinylDNSProto.TXTData =
    VinylDNSProto.TXTData.newBuilder().setText(data.text).build()

  /* This cannot be called toPB because RecordData is the base type for things like AData, cannot overload */
  def toRecordData(data: RecordData): VinylDNSProto.RecordData = {
    val d = data match {
      case x: AData => toPB(x)
      case x: AAAAData => toPB(x)
      case x: CNAMEData => toPB(x)
      case x: DSData => toPB(x)
      case x: MXData => toPB(x)
      case x: NSData => toPB(x)
      case x: PTRData => toPB(x)
      case x: SOAData => toPB(x)
      case x: SPFData => toPB(x)
      case x: SRVData => toPB(x)
      case x: NAPTRData => toPB(x)
      case x: SSHFPData => toPB(x)
      case x: TXTData => toPB(x)
    }
    VinylDNSProto.RecordData.newBuilder().setData(d.toByteString).build()
  }

  def toPB(rs: RecordSet): VinylDNSProto.RecordSet = {
    val builder = VinylDNSProto.RecordSet
      .newBuilder()
      .setCreated(rs.created.getMillis)
      .setId(rs.id)
      .setName(rs.name)
      .setStatus(rs.status.toString)
      .setTyp(rs.typ.toString)
      .setTtl(rs.ttl)
      .setZoneId(rs.zoneId)
      .setAccount(rs.account)

    rs.updated.foreach(dt => builder.setUpdated(dt.getMillis))
    rs.ownerGroupId.foreach(id => builder.setOwnerGroupId(id))

    // Map the records, first map to bytes, and then map the bytes to a record data instance
    rs.records.map(toRecordData).foreach(rd => builder.addRecord(rd))

    builder.build()
  }

  def toPB(zone: Zone): VinylDNSProto.Zone = {
    val builder = VinylDNSProto.Zone
      .newBuilder()
      .setId(zone.id)
      .setName(zone.name)
      .setEmail(zone.email)
      .setCreated(zone.created.getMillis)
      .setStatus(zone.status.toString)
      .setAccount(zone.account)
      .setShared(zone.shared)
      .setAcl(toPB(zone.acl))
      .setAdminGroupId(zone.adminGroupId)
      .setIsTest(zone.isTest)

    zone.updated.foreach(dt => builder.setUpdated(dt.getMillis))
    zone.connection.foreach(cn => builder.setConnection(toPB(cn)))
    zone.transferConnection.foreach(cn => builder.setTransferConnection(toPB(cn)))
    zone.latestSync.foreach(dt => builder.setLatestSync(dt.getMillis))
    zone.backendId.foreach(bid => builder.setBackendId(bid))
    builder.build()
  }

  def toPB(conn: ZoneConnection): VinylDNSProto.ZoneConnection =
    VinylDNSProto.ZoneConnection
      .newBuilder()
      .setName(conn.name)
      .setKeyName(conn.keyName)
      .setKey(conn.key)
      .setPrimaryServer(conn.primaryServer)
      .build()

  def toPB(zoneChange: ZoneChange): VinylDNSProto.ZoneChange = {
    val builder = VinylDNSProto.ZoneChange
      .newBuilder()
      .setId(zoneChange.id)
      .setCreated(zoneChange.created.getMillis)
      .setStatus(zoneChange.status.toString)
      .setTyp(zoneChange.changeType.toString)
      .setUserId(zoneChange.userId)
      .setZone(toPB(zoneChange.zone))

    zoneChange.systemMessage.map(builder.setSystemMessage)

    builder.build()
  }

  def fromPB(data: VinylDNSProto.User): User =
    User(
      data.getUserName,
      data.getAccessKey,
      data.getSecretKey,
      if (data.hasFirstName) Some(data.getFirstName) else None,
      if (data.hasLastName) Some(data.getLastName) else None,
      if (data.hasEmail) Some(data.getEmail) else None,
      new DateTime(data.getCreated),
      data.getId,
      data.getIsSuper,
      LockStatus.withName(data.getLockStatus),
      data.getIsSupport,
      data.getIsTest
    )

  def toPB(user: User): VinylDNSProto.User = {
    val builder = VinylDNSProto.User
      .newBuilder()
      .setUserName(user.userName)
      .setAccessKey(user.accessKey)
      .setSecretKey(user.secretKey)
      .setCreated(user.created.getMillis)
      .setId(user.id)
      .setIsSuper(user.isSuper)
      .setLockStatus(user.lockStatus.toString)
      .setIsSupport(user.isSupport)
      .setIsTest(user.isTest)

    user.firstName.foreach(fn => builder.setFirstName(fn))
    user.lastName.foreach(ln => builder.setLastName(ln))
    user.email.foreach(e => builder.setEmail(e))

    builder.build()
  }

  def fromPb(data: VinylDNSProto.UserChange): UserChange =
    if (data.getChangeType.equals(UserChangeType.Create.value)) {
      CreateUser(
        fromPB(data.getNewUser),
        data.getMadeByUserId,
        new DateTime(data.getCreated),
        data.getId
      )
    } else {
      UpdateUser(
        fromPB(data.getNewUser),
        data.getMadeByUserId,
        new DateTime(data.getCreated),
        fromPB(data.getOldUser),
        data.getId
      )
    }

  def toPb(userChange: UserChange): VinylDNSProto.UserChange = {
    val builder = VinylDNSProto.UserChange
      .newBuilder()
      .setNewUser(toPB(userChange.newUser))
      .setCreated(userChange.created.getMillis)
      .setId(userChange.id)
      .setMadeByUserId(userChange.madeByUserId)

    userChange match {
      case _: CreateUser =>
        builder
          .setChangeType(UserChangeType.Create.value)

      case u: UpdateUser =>
        builder
          .setChangeType(UserChangeType.Update.value)
          .setOldUser(toPB(u.oldUser))
    }

    builder.build()
  }
}
