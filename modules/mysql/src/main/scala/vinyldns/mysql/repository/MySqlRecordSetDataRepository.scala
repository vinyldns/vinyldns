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

  def save(db: DB,changeSet: ChangeSet): IO[ChangeSet] =
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
            db,
            i.recordSet.records.toString,
            i.recordSet.typ.toString,
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
            db,
            u.recordSet.records.toString,
            u.recordSet.typ.toString,
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
        db.withinTx { implicit session =>
          deletes.grouped(1000).foreach { group =>
            DELETE_RECORDSETDATA.batch(group: _*).apply()
          }
        }
      }.as(changeSet)

    }

  def deleteRecordSetsInZone(db: DB, zoneId: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetsInZone") {
      IO {
        val numDeleted = db.withinTx { implicit session =>
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
              db: DB,
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
      case "insert" => rsDataSave(db,recordData, recordType, recordID, zoneId, FQDN, reverseFDQN)
      case "update" =>
        /**
      for update delete the rsdata first, as if recordset are updated
          */
        db.withinTx { implicit session =>
          DELETE_RECORDSETDATA
            .bind(recordID)
            .update()
            .apply()
        }
        rsDataSave(db,recordData, recordType, recordID, zoneId, FQDN, reverseFDQN)
    }
  def rsDataSave(
                  db: DB,
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
      db.withinTx { implicit session =>
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
    val IPADDRESS_IPV4_PATTERN = {
      "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    }
    val IPADDRESS_IPV6_PATTERN = {
      "(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))"
    }

    /**
    parse ip address from the recordset data
      */
    val patternipv4: Pattern = Pattern.compile(IPADDRESS_IPV4_PATTERN)
    val patternipv6: Pattern = Pattern.compile(IPADDRESS_IPV6_PATTERN)
    val matcheripv4: Matcher = patternipv4.matcher(ipString)
    val matcheripv6: Matcher = patternipv6.matcher(ipString)

    if (matcheripv4.find() && (recordType == "A" || recordType == "AAAA" || recordType == "PTR")) {
      ipAddress = matcheripv4.group().mkString
    } else if (matcheripv6
        .find() && (recordType == "A" || recordType == "AAAA" || recordType == "PTR")) {
      ipAddress = matcheripv6.group().mkString
    } else ipAddress = null
    ipAddress
  }

}
