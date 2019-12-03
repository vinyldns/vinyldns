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

package vinyldns.api.domain.batch

import java.net.InetAddress
import java.util.UUID

import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.ReverseZoneHelpers
import vinyldns.api.domain.batch.BatchChangeInterfaces.ValidatedBatch
import vinyldns.api.domain.batch.BatchTransformations.LogicalChangeType.LogicalChangeType
import vinyldns.api.domain.dns.DnsConversions.getIPv6FullReverseName
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.{AAAAData, RecordData, RecordSet, RecordSetChange}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.zone.Zone
import vinyldns.core.domain.record.RecordType.RecordType

object SupportedBatchChangeRecordTypes {
  val supportedTypes = Set(A, AAAA, CNAME, PTR, TXT, MX)
  def get: Set[RecordType] = supportedTypes
}

/* Helper types for intermediate transformations of the batch data */
// ALL of these are subject to change as implementation needs
object BatchTransformations {

  final case class ExistingZones(zones: Set[Zone]) {
    val zoneMap: Map[String, Zone] = zones.map(z => (z.name.toLowerCase(), z)).toMap

    def getById(id: String): Boolean = zones.exists(zn => zn.id.equals(id))

    def getByName(name: String): Option[Zone] = zoneMap.get(name.toLowerCase())

    def getipv4PTRMatches(ipv4: String): List[Zone] =
      zones.filter { zn =>
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zn, ipv4)
      }.toList

    def getipv6PTRMatches(ipv6: String): List[Zone] = {
      val fullReverseZone = getIPv6FullReverseName(ipv6)
      fullReverseZone.toList.flatMap { fqdn =>
        zones.filter(zn => fqdn.endsWith(zn.name))
      }
    }
  }

  final case class ExistingRecordSets(recordSets: List[RecordSet]) {
    val recordSetMap: Map[(String, String), List[RecordSet]] =
      recordSets.groupBy(rs => (rs.zoneId, rs.name.toLowerCase))

    def get(zoneId: String, name: String, recordType: RecordType): Option[RecordSet] =
      recordSetMap.getOrElse((zoneId, name.toLowerCase), List()).find(_.typ == recordType)

    def get(recordKey: RecordKey): Option[RecordSet] =
      get(recordKey.zoneId, recordKey.recordName, recordKey.recordType)

    def getRecordSetMatch(zoneId: String, name: String): List[RecordSet] =
      recordSetMap.getOrElse((zoneId, name.toLowerCase), List())
  }

  sealed trait ChangeForValidation {
    val zone: Zone
    val recordName: String
    val inputChange: ChangeInput
    val recordKey = RecordKey(zone.id, recordName, inputChange.typ)
    def asStoredChange(changeId: Option[String] = None): SingleChange
    def isAddChangeForValidation: Boolean
  }

  object ChangeForValidation {
    def apply(zone: Zone, recordName: String, changeInput: ChangeInput): ChangeForValidation =
      changeInput match {
        case a: AddChangeInput => AddChangeForValidation(zone, recordName, a)
        case d: DeleteRRSetChangeInput =>
          DeleteRRSetChangeForValidation(zone, recordName, d)
      }
  }

  final case class AddChangeForValidation(
      zone: Zone,
      recordName: String,
      inputChange: AddChangeInput,
      existingRecordTtl: Option[Long] = None
  ) extends ChangeForValidation {
    def asStoredChange(changeId: Option[String] = None): SingleChange = {

      val ttl = inputChange.ttl.orElse(existingRecordTtl).getOrElse(VinylDNSConfig.defaultTtl)

      SingleAddChange(
        Some(zone.id),
        Some(zone.name),
        Some(recordName),
        inputChange.inputName,
        inputChange.typ,
        ttl,
        inputChange.record,
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        changeId.getOrElse(UUID.randomUUID().toString)
      )
    }

    def isAddChangeForValidation: Boolean = true
  }

  final case class DeleteRRSetChangeForValidation(
      zone: Zone,
      recordName: String,
      inputChange: DeleteRRSetChangeInput
  ) extends ChangeForValidation {
    def asStoredChange(changeId: Option[String] = None): SingleChange =
      SingleDeleteRRSetChange(
        Some(zone.id),
        Some(zone.name),
        Some(recordName),
        inputChange.inputName,
        inputChange.typ,
        inputChange.record,
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        changeId.getOrElse(UUID.randomUUID().toString)
      )

    def isAddChangeForValidation: Boolean = false
  }

  final case class BatchConversionOutput(
      batchChange: BatchChange,
      recordSetChanges: List[RecordSetChange]
  )

  final case class ChangeForValidationMap(
      changes: ValidatedBatch[ChangeForValidation],
      existingRecordSets: ExistingRecordSets
  ) {
    import BatchChangeInterfaces._

    val innerMap: Map[RecordKey, ValidationChanges] = {
      changes.getValid.groupBy(_.recordKey).map { keyChangesTuple =>
        val (recordKey, changeList) = keyChangesTuple
        recordKey -> ValidationChanges(changeList, existingRecordSets.get(recordKey))
      }
    }

    def getExistingRecordSet(recordKey: RecordKey): Option[RecordSet] =
      existingRecordSets.get(recordKey)

    def getProposedAdds(recordKey: RecordKey): Set[RecordData] =
      innerMap.get(recordKey).map(_.proposedAdds).toSet.flatten

    // The new, net record data factoring in existing records, deletes and adds
    // If record is not edited in batch, will fallback to look up record in existing
    // records
    def getProposedRecordData(recordKey: RecordKey): Set[RecordData] = {
      val batchRecordData = innerMap.get(recordKey).map(_.proposedRecordData)
      lazy val existingRecordData = existingRecordSets.get(recordKey).map(_.records.toSet)
      batchRecordData.getOrElse(existingRecordData.getOrElse(Set()))
    }

    def getLogicalChangeType(recordKey: RecordKey): Option[LogicalChangeType] =
      innerMap.get(recordKey).map(_.logicalChangeType)
  }

  object ValidationChanges {
    def matchRecordData(existingRecord: RecordData, recordData: String): Boolean =
      existingRecord match {
        case AAAAData(address) =>
          InetAddress.getByName(address).getHostName ==
            InetAddress.getByName(recordData).getHostName
        case _ => false
      }

    def apply(
        changes: List[ChangeForValidation],
        existingRecordSet: Option[RecordSet]
    ): ValidationChanges = {
      // Collect add DNS entries
      val addChangeRecordDataSet = changes.collect {
        case add: AddChangeForValidation => add.inputChange.record
      }.toSet

      val existingRecords = existingRecordSet.toList.flatMap(_.records).toSet

      // Collect delete DNS entries. This formulates all of the proposed delete entries, including
      // existing DNS entries in the event of DeleteRecordSet
      val deleteChangeSet = changes
        .collect {
          case DeleteRRSetChangeForValidation(
              _,
              _,
              DeleteRRSetChangeInput(_, AAAA, Some(AAAAData(address)))
              ) =>
            existingRecords.filter(r => matchRecordData(r, address))
          case DeleteRRSetChangeForValidation(
              _,
              _,
              DeleteRRSetChangeInput(_, _, Some(recordData))
              ) =>
            Set(recordData)
          case _: DeleteRRSetChangeForValidation => existingRecords
        }
        .toSet
        .flatten

      // New proposed record data (assuming all validations pass)
      val proposedRecordData = existingRecords -- deleteChangeSet ++ addChangeRecordDataSet

      // Note: "Update" where an Add and DeleteRecordSet is provided for a DNS record that does not exist will be
      // treated as a logical Add since the delete validation will fail (on record does not exist)
      val logicalChangeType = (addChangeRecordDataSet.nonEmpty, deleteChangeSet.nonEmpty) match {
        case (true, true) => LogicalChangeType.Update
        case (true, false) => LogicalChangeType.Add
        case (false, true) =>
          if ((existingRecords -- deleteChangeSet).isEmpty) {
            LogicalChangeType.FullDelete
          } else {
            LogicalChangeType.Update
          }
        case (false, false) => LogicalChangeType.NotEditedInBatch
      }

      new ValidationChanges(addChangeRecordDataSet, proposedRecordData, logicalChangeType)
    }
  }

  final case class ValidationChanges(
      proposedAdds: Set[RecordData],
      proposedRecordData: Set[RecordData],
      logicalChangeType: LogicalChangeType
  )

  final case class BatchValidationFlowOutput(
      validatedChanges: ValidatedBatch[ChangeForValidation],
      existingZones: ExistingZones,
      groupedChanges: ChangeForValidationMap
  )

  object LogicalChangeType extends Enumeration {
    type LogicalChangeType = Value
    val Add, FullDelete, Update, NotEditedInBatch = Value
  }
}
