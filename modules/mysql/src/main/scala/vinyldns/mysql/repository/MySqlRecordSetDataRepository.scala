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

package vinyldns.mysql.repository

import cats.effect.IO
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.mysql.repository.MySqlRecordSetRepository.toFQDN
import java.util.regex.{Matcher, Pattern}

class MySqlRecordSetDataRepository
    extends RecordSetDataRepository
    with Monitored
    with ProtobufConversions {

  private val INSERT_RECORDSETDATA =
    sql"INSERT IGNORE INTO recordset_data(recordset_id, zone_id, fqdn, reverse_fqdn, type, record_data, ip) VALUES ({recordset_id}, {zone_id}, {FDQN}, {reverseFDQN}, {type},{record_data}, {ip})"

  private val DELETE_RECORDSETDATA =
    sql"DELETE FROM recordset_data WHERE recordset_id = ?"

  private val DELETE_RECORDSETDATAS_IN_ZONE =
    sql"DELETE FROM recordset_data WHERE zone_id = ?"

  private final val logger = LoggerFactory.getLogger(classOf[MySqlRecordSetRepository])

  def save(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordSetData.save") {
      val byStatus = changeSet.changes.groupBy(_.status)

      // address successful and pending changes
      val completeChanges = byStatus.getOrElse(RecordSetChangeStatus.Complete, Seq())
      val completeChangesByType = completeChanges.groupBy(_.changeType)
      val completeCreates = completeChangesByType.getOrElse(RecordSetChangeType.Create, Seq())
      val completeUpdates = completeChangesByType.getOrElse(RecordSetChangeType.Update, Seq())
      val completeDeletes = completeChangesByType.getOrElse(RecordSetChangeType.Delete, Seq())

      val pendingChanges = byStatus.getOrElse(RecordSetChangeStatus.Pending, Seq())

      // all pending changes are saved as if they are creates
      (completeCreates ++ pendingChanges).map { i =>
        Seq[Any](
          RsData(
            i.recordSet.records.toString(),
            fromRecordType(i.recordSet.typ),
            i.recordSet.id,
            i.recordSet.zoneId,
            toFQDN(i.zone.name, i.recordSet.name),
            toFQDN(i.zone.name, i.recordSet.name).reverse,
            "insert"
          )
        )
      }
      completeUpdates.map { u =>
        Seq[Any](
          RsData(
            u.recordSet.records.toString(),
            fromRecordType(u.recordSet.typ),
            u.recordSet.id,
            u.recordSet.zoneId,
            toFQDN(u.zone.name, u.recordSet.name),
            toFQDN(u.zone.name, u.recordSet.name).reverse,
            "update"
          )
        )
      }
      val deletes: Seq[Seq[Any]] = completeDeletes.map(d => Seq[Any](d.recordSet.id))

      IO {
        DB.localTx { implicit s =>
          deletes.grouped(1000).foreach { group =>
            DELETE_RECORDSETDATA.batch(group: _*).apply()
          }
        }
      }.as(changeSet)

    }

  def deleteRecordSetsInZone(zoneId: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetsInZone") {
      IO {
        val numDeleted = DB.localTx { implicit s =>
          DELETE_RECORDSETDATAS_IN_ZONE
            .bind(zoneId)
            .update()
            .apply()
        }
        logger.info(s"Deleted $numDeleted records from zone $zoneName (zone id: $zoneId)")
      }.handleErrorWith { error =>
        logger.error(s"Failed deleting records from zone $zoneName (zone id: $zoneId)", error)
        IO.raiseError(error)
      }
    }

  def RsData(
      recordData: String,
      recordType: String,
      recordID: String,
      zoneId: String,
      FQDN: String,
      reverseFDQN: String,
      rs: String
  ): Unit =
    rs match {

      /**
    insert the rsdata first, as if recordset are created
        */
      case "insert" => rsDataSave(recordData, recordType, recordID, zoneId, FQDN, reverseFDQN)
      case "update" =>
        /**
      for update delete the rsdata first, as if recordset are updated
          */
        DB.localTx { implicit s =>
          DELETE_RECORDSETDATA
            .bind(recordID)
            .update()
            .apply()
        }
        rsDataSave(recordData, recordType, recordID, zoneId, FQDN, reverseFDQN)
    }
  def rsDataSave(
      recordData: String,
      recordType: String,
      recordID: String,
      zoneId: String,
      FQDN: String,
      reverseFDQN: String
  ): Unit = {
    var parseIp: String = null
    var records: String = null
    for (ipString <- recordData.split(",").map(_.trim).toList) {
      parseIp = parseIP(ipString, recordType)
      records = recordData.replace("List(", "")
      records = records.replace(")", "")

      /**
      insert the rsdata first, as if recordset are created/updated
        */
      DB.localTx { implicit s =>
        INSERT_RECORDSETDATA
          .bindByName(
            'recordset_id -> recordID,
            'zone_id -> zoneId,
            'FDQN -> FQDN,
            'reverseFDQN -> reverseFDQN,
            'type -> recordType,
            'record_data -> records,
            'ip -> parseIp
          )
          .update()
          .apply()
      }
    }
  }

  def parseIP(
      ipString: String,
      recordType: String
  ): String = {
    var ipAddress: String = null
    val IPADDRESS_PATTERN = {
      "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    }

    /**
    parse ip address from the recordset data
      */
    val pattern: Pattern = Pattern.compile(IPADDRESS_PATTERN)
    val matcher: Matcher = pattern.matcher(ipString)
    if (matcher.find() && (recordType == "A" || recordType == "AAAA" || recordType == "PTR")) {
      ipAddress = matcher.group().mkString
    } else ipAddress = null
    ipAddress
  }

  def fromRecordType(typ: RecordType): String =
    typ match {
      case RecordType.A => "A"
      case RecordType.AAAA => "AAAA"
      case RecordType.CNAME => "CNAME"
      case RecordType.MX => "MX"
      case RecordType.NS => "NS"
      case RecordType.PTR => "PTR"
      case RecordType.SPF => "SPF"
      case RecordType.SRV => "SRV"
      case RecordType.SSHFP => "SSHFP"
      case RecordType.TXT => "TXT"
      case RecordType.SOA => "SOA"
      case RecordType.DS => "DS"
      case RecordType.NAPTR => "NAPTR"
      case RecordType.UNKNOWN => "UNKNOWN"
    }

}
