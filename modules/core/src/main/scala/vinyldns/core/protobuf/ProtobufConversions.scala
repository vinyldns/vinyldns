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

import java.time.Instant
import org.slf4j.{Logger, LoggerFactory}
import scodec.bits.ByteVector
import vinyldns.core.domain.membership.UserChange.{CreateUser, UpdateUser}
import vinyldns.core.domain.membership.{LockStatus, User, UserChange, UserChangeType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.domain.{Encrypted, Fqdn, record, zone}
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.util.Try
import org.json4s.jackson.JsonMethods._
import org.json4s.{JValue, JString}
import org.json4s.JsonAST._

trait ProtobufConversions {

  val protoLogger: Logger = LoggerFactory.getLogger("vinyldns.core.protobuf.ProtobufConversions")

  // Must match the numbers from the Vinyldns.proto file
  def fromPB(algorithm: VinylDNSProto.Algorithm): Algorithm = algorithm.getNumber match {
    case 0 => Algorithm.HMAC_MD5
    case 1 => Algorithm.HMAC_SHA1
    case 2 => Algorithm.HMAC_SHA224
    case 3 => Algorithm.HMAC_SHA256
    case 4 => Algorithm.HMAC_SHA384
    case 5 => Algorithm.HMAC_SHA512
    case _ => Algorithm.HMAC_MD5 // default
  }

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
      created = Instant.ofEpochMilli(chg.getCreated),
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
      created = Instant.ofEpochMilli(rs.getCreated),
      updated = if (rs.hasUpdated) Some(Instant.ofEpochMilli(rs.getUpdated)) else None,
      id = rs.getId,
      records =
        rs.getRecordList.asScala.map(rd => fromPB(rd, RecordType.withName(rs.getTyp))).toList,
      account = rs.getAccount,
      ownerGroupId = if (rs.hasOwnerGroupId) Some(rs.getOwnerGroupId) else None ,
      recordSetGroupChange = if (rs.hasRecordSetGroupChange) Some(fromPB(rs.getRecordSetGroupChange)) else None,
    )

  def fromPB(rsa: VinylDNSProto.ownershipTransfer): OwnershipTransfer =
    record.OwnershipTransfer(
      ownershipTransferStatus = OwnershipTransferStatus.withName(rsa.getOwnershipTransferStatus),
      requestedOwnerGroupId = if (rsa.hasRequestedOwnerGroupId) Some(rsa.getRequestedOwnerGroupId) else None)

  def toPB(rsa: OwnershipTransfer): VinylDNSProto.ownershipTransfer = {
    val builder = VinylDNSProto.ownershipTransfer
      .newBuilder()
      .setOwnershipTransferStatus(rsa.ownershipTransferStatus.toString)
    rsa.requestedOwnerGroupId.foreach(id => builder.setRequestedOwnerGroupId(id))
    builder.build()
  }

  def fromPB(zn: VinylDNSProto.Zone): Zone = {
    val pbStatus = zn.getStatus
    val status =
      if (pbStatus.startsWith("Pending")) ZoneStatus.Active
      else ZoneStatus.withName(pbStatus)

    zone.Zone(
      name = zn.getName,
      email = zn.getEmail,
      status = status,
      created = Instant.ofEpochMilli(zn.getCreated),
      updated = if (zn.hasUpdated) Some(Instant.ofEpochMilli(zn.getUpdated)) else None,
      id = zn.getId,
      connection = if (zn.hasConnection) Some(fromPB(zn.getConnection)) else None,
      transferConnection =
        if (zn.hasTransferConnection) Some(fromPB(zn.getTransferConnection)) else None,
      account = zn.getAccount,
      shared = zn.getShared,
      acl = if (zn.hasAcl) fromPB(zn.getAcl) else ZoneACL(),
      adminGroupId = zn.getAdminGroupId,
      latestSync = if (zn.hasLatestSync) Some(Instant.ofEpochMilli(zn.getLatestSync)) else None,
      isTest = zn.getIsTest,
      backendId = if (zn.hasBackendId) Some(zn.getBackendId) else None,
      recurrenceSchedule = if (zn.hasRecurrenceSchedule) Some(zn.getRecurrenceSchedule) else None,
      scheduleRequestor = if (zn.hasScheduleRequestor) Some(zn.getScheduleRequestor) else None
    )
  }

  def fromPB(zc: VinylDNSProto.ZoneConnection): ZoneConnection =
    ZoneConnection(
      zc.getName,
      zc.getKeyName,
      Encrypted(zc.getKey),
      zc.getPrimaryServer,
      fromPB(zc.getAlgorithm)
    )

  def fromPB(chg: VinylDNSProto.ZoneChange): ZoneChange =
    ZoneChange(
      zone = fromPB(chg.getZone),
      userId = chg.getUserId,
      changeType = ZoneChangeType.withName(chg.getTyp),
      status = ZoneChangeStatus.withName(chg.getStatus),
      created = Instant.ofEpochMilli(chg.getCreated),
      systemMessage = if (chg.hasSystemMessage) Option(chg.getSystemMessage) else None,
      id = chg.getId
    )

  def toPB(chg: RecordSetChange): VinylDNSProto.RecordSetChange = {
    val builder = VinylDNSProto.RecordSetChange
      .newBuilder()
      .setCreated(chg.created.toEpochMilli)
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

  def fromPB(zn: VinylDNSProto.GenerateZone): GenerateZone = {
    // status conversion, is this necessary?
    val pbStatus = zn.getStatus
    val status =
      if (pbStatus.startsWith("Pending")) GenerateZoneStatus.Active
      else GenerateZoneStatus.withName(pbStatus)

    // convert the providerParams map from protobuf Map[String, String] to scala Map[String, JValue]
    val providerParams: Map[String, JValue] = zn.getProviderParamsMap.asScala
      .map { case (k, v) => k -> parseParamValue(v) } // convert the JSON string into a json4s JValue
      .toMap

    zone.GenerateZone(
      groupId = zn.getGroupId,
      email = zn.getEmail,
      provider = zn.getProvider,
      zoneName = zn.getZoneName,
      status = status,
      providerParams = providerParams,
      id = zn.getId,
      response = if (zn.hasResponse) Some(fromPB(zn.getResponse)) else None,
      created = Instant.ofEpochMilli(zn.getCreated),
      updated = if (zn.hasUpdated) Some(Instant.ofEpochMilli(zn.getUpdated)) else None
    )
  }

  private def parseParamValue(value: String): JValue =
    try {
      parse(value) // Try parsing as JSON
    } catch {
      case _: Exception => JString(value) // Fallback to string
    }

  def fromPB(zgr: VinylDNSProto.ZoneGenerationResponse): ZoneGenerationResponse =
    ZoneGenerationResponse(
      if (zgr.hasResponseCode) Some(zgr.getResponseCode.toInt) else None,
      if (zgr.hasStatus) Some(zgr.getStatus) else None,
      if (Option(zgr.getMessage).exists(_.trim.nonEmpty)) {
        try Some(parse(zgr.getMessage))
        catch {case _: Throwable => Some(JString(zgr.getMessage))}
      } else None,
      GenerateZoneChangeType.withName(zgr.getChangeType)
    )

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

  def fromPB(data: VinylDNSProto.CNAMEData): CNAMEData = CNAMEData(Fqdn(data.getCname))

  def fromPB(data: VinylDNSProto.DSData): DSData =
    DSData(
      data.getKeyTag,
      DnsSecAlgorithm(data.getAlgorithm),
      DigestType(data.getDigestType),
      ByteVector.apply(data.getDigest.asReadOnlyByteBuffer())
    )

  def fromPB(data: VinylDNSProto.MXData): MXData =
    MXData(data.getPreference, Fqdn(data.getExchange))

  def fromPB(data: VinylDNSProto.NSData): NSData = NSData(Fqdn(data.getNsdname))

  def fromPB(data: VinylDNSProto.PTRData): PTRData = PTRData(Fqdn(data.getPtrdname))

  def fromPB(data: VinylDNSProto.SOAData): SOAData =
    SOAData(
      Fqdn(data.getMname),
      data.getRname,
      data.getSerial,
      data.getRefresh,
      data.getRetry,
      data.getExpire,
      data.getMinimum
    )

  def fromPB(data: VinylDNSProto.SPFData): SPFData = SPFData(data.getText)

  def fromPB(data: VinylDNSProto.SRVData): SRVData =
    SRVData(data.getPriority, data.getWeight, data.getPort, Fqdn(data.getTarget))

  def fromPB(data: VinylDNSProto.NAPTRData): NAPTRData =
    NAPTRData(
      data.getOrder,
      data.getPreference,
      data.getFlags,
      data.getService,
      data.getRegexp,
      Fqdn(data.getReplacement)
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

  def toPB(data: AData): VinylDNSProto.AData = {
    VinylDNSProto.AData.newBuilder().setAddress(data.address).build()
  }

  def toPB(data: AAAAData): VinylDNSProto.AAAAData =
    VinylDNSProto.AAAAData.newBuilder().setAddress(data.address).build()

  def toPB(data: CNAMEData): VinylDNSProto.CNAMEData =
    VinylDNSProto.CNAMEData.newBuilder().setCname(data.cname.fqdn).build()

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
      .setExchange(data.exchange.fqdn)
      .build()

  def toPB(data: PTRData): VinylDNSProto.PTRData =
    VinylDNSProto.PTRData.newBuilder().setPtrdname(data.ptrdname.fqdn).build()

  def toPB(data: NSData): VinylDNSProto.NSData =
    VinylDNSProto.NSData.newBuilder().setNsdname(data.nsdname.fqdn).build()

  def toPB(data: SOAData): VinylDNSProto.SOAData =
    VinylDNSProto.SOAData
      .newBuilder()
      .setRname(data.rname)
      .setMname(data.mname.fqdn)
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
      .setTarget(data.target.fqdn)
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
      .setReplacement(data.replacement.fqdn)
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

  /**
   * Converts record data into protobuf form
   *
   * @param data the record data to convert
   * @return The record data in protobuf form
   */
  def recordDataToPB(data: RecordData) = data match {
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

  /* This cannot be called toPB because RecordData is the base type for things like AData, cannot overload */
  def toRecordData(data: RecordData): VinylDNSProto.RecordData = {
    VinylDNSProto.RecordData.newBuilder().setData(recordDataToPB(data).toByteString).build()
  }

  def toPB(rs: RecordSet): VinylDNSProto.RecordSet = {
    val builder = VinylDNSProto.RecordSet
      .newBuilder()
      .setCreated(rs.created.toEpochMilli)
      .setId(rs.id)
      .setName(rs.name)
      .setStatus(rs.status.toString)
      .setTyp(rs.typ.toString)
      .setTtl(rs.ttl)
      .setZoneId(rs.zoneId)
      .setAccount(rs.account)

    rs.updated.foreach(dt => builder.setUpdated(dt.toEpochMilli))
    rs.ownerGroupId.foreach(id => builder.setOwnerGroupId(id))
    rs.recordSetGroupChange.foreach(rsg => builder.setRecordSetGroupChange(toPB(rsg)))

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
      .setCreated(zone.created.toEpochMilli)
      .setStatus(zone.status.toString)
      .setAccount(zone.account)
      .setShared(zone.shared)
      .setAcl(toPB(zone.acl))
      .setAdminGroupId(zone.adminGroupId)
      .setIsTest(zone.isTest)

    zone.updated.foreach(dt => builder.setUpdated(dt.toEpochMilli))
    zone.connection.foreach(cn => builder.setConnection(toPB(cn)))
    zone.transferConnection.foreach(cn => builder.setTransferConnection(toPB(cn)))
    zone.latestSync.foreach(dt => builder.setLatestSync(dt.toEpochMilli))
    zone.backendId.foreach(bid => builder.setBackendId(bid))
    zone.recurrenceSchedule.foreach(rs => builder.setRecurrenceSchedule(rs))
    zone.scheduleRequestor.foreach(rs => builder.setScheduleRequestor(rs))
    builder.build()
  }

  def toPB(algorithm: Algorithm): VinylDNSProto.Algorithm = algorithm match {
    case Algorithm.HMAC_MD5 => VinylDNSProto.Algorithm.HMAC_MD5
    case Algorithm.HMAC_SHA1 => VinylDNSProto.Algorithm.HMAC_SHA1
    case Algorithm.HMAC_SHA224 => VinylDNSProto.Algorithm.HMAC_SHA224
    case Algorithm.HMAC_SHA256 => VinylDNSProto.Algorithm.HMAC_SHA256
    case Algorithm.HMAC_SHA384 => VinylDNSProto.Algorithm.HMAC_SHA384
    case Algorithm.HMAC_SHA512 => VinylDNSProto.Algorithm.HMAC_SHA512
  }

  def toPB(conn: ZoneConnection): VinylDNSProto.ZoneConnection =
    VinylDNSProto.ZoneConnection
      .newBuilder()
      .setName(conn.name)
      .setKeyName(conn.keyName)
      .setKey(conn.key.value)
      .setPrimaryServer(conn.primaryServer)
      .setAlgorithm(toPB(conn.algorithm))
      .build()

  def toPB(zoneChange: ZoneChange): VinylDNSProto.ZoneChange = {
    val builder = VinylDNSProto.ZoneChange
      .newBuilder()
      .setId(zoneChange.id)
      .setCreated(zoneChange.created.toEpochMilli)
      .setStatus(zoneChange.status.toString)
      .setTyp(zoneChange.changeType.toString)
      .setUserId(zoneChange.userId)
      .setZone(toPB(zoneChange.zone))

    zoneChange.systemMessage.map(builder.setSystemMessage)

    builder.build()
  }


  def toPB(generateZone: GenerateZone): VinylDNSProto.GenerateZone = {
    val builder = VinylDNSProto.GenerateZone
      .newBuilder()
      .setId(generateZone.id)
      .setGroupId(generateZone.groupId)
      .setEmail(generateZone.email)
      .setCreated(generateZone.created.toEpochMilli)
      .setProvider(generateZone.provider)
      .setZoneName(generateZone.zoneName)
      .setStatus(generateZone.status.toString)

    // Handle optional standard fields
    generateZone.response.foreach(gz => builder.setResponse(toPB(gz)))
    generateZone.updated.foreach(dt => builder.setUpdated(dt.toEpochMilli))

    // Convert providerParams map to Protobuf map
    generateZone.providerParams.foreach {
      case (key, value) =>
        val strValue = compact(render(value))  // Convert JValue to String
        builder.putProviderParams(key, strValue)
    }

    builder.build()
  }

  def toPB(zgr: ZoneGenerationResponse): VinylDNSProto.ZoneGenerationResponse = {
    val builder = VinylDNSProto.ZoneGenerationResponse
      .newBuilder()
      .setChangeType(zgr.changeType.toString)
    zgr.responseCode.foreach(rc => builder.setResponseCode(rc.toLong))
    zgr.status.foreach(st => builder.setStatus(st))
    zgr.message.foreach {
      case JNothing =>
      case JString(str) => builder.setMessage(str)
      case other => builder.setMessage(compact(render(other)))
    }
    builder.build()
  }

  def fromPB(data: VinylDNSProto.User): User =
    User(
      data.getUserName,
      data.getAccessKey,
      Encrypted(data.getSecretKey),
      if (data.hasFirstName) Some(data.getFirstName) else None,
      if (data.hasLastName) Some(data.getLastName) else None,
      if (data.hasEmail) Some(data.getEmail) else None,
      Instant.ofEpochMilli(data.getCreated),
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
      .setSecretKey(user.secretKey.value)
      .setCreated(user.created.toEpochMilli)
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
        Instant.ofEpochMilli(data.getCreated),
        data.getId
      )
    } else {
      UpdateUser(
        fromPB(data.getNewUser),
        data.getMadeByUserId,
        Instant.ofEpochMilli(data.getCreated),
        fromPB(data.getOldUser),
        data.getId
      )
    }

  def toPb(userChange: UserChange): VinylDNSProto.UserChange = {
    val builder = VinylDNSProto.UserChange
      .newBuilder()
      .setNewUser(toPB(userChange.newUser))
      .setCreated(userChange.created.toEpochMilli)
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
