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

import cats.implicits._
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import cats.effect.IO
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.mysql.repository.MySqlRecordSetRepository.{ toFQDN, toRecordSet}
import vinyldns.proto.VinylDNSProto

import java.util.regex.{Matcher, Pattern}

class MySqlRecordSetDataRepository
    extends RecordSetDataRepository
    with Monitored
    with ProtobufConversions {

  private val INSERT_RECORDSETDATA =
    sql"INSERT IGNORE INTO recordset_data(recordset_id, zone_id, fqdn,  reverse_fqdn, type, record_data, ip, data) VALUES ({recordset_id}, {zone_id}, {fqdn}, {reverse_fqdn}, {type}, {record_data}, {ip}, {data})"
  //sql"INSERT IGNORE INTO recordset_data(recordset_id, zone_id, fqdn,  reverse_fqdn, type, record_data, ip, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

  private val UPDATE_RECORDSETDATA =
    sql"UPDATE recordset_data SET zone_id = {zone_id}, fqdn =  {fqdn}, reverse_fqdn = {reverse_fqdn}, type = {type}, record_data = {record_data}, ip = {ip}, data =  {data} WHERE recordset_id ={recordset_id}"

  private val DELETE_RECORDSETDATA =
    sql"DELETE FROM recordset_data WHERE recordset_id = ?"

  private val DELETE_RECORDSETDATAS_IN_ZONE =
    sql"DELETE FROM recordset_data WHERE zone_id = ?"

  private val COUNT_RECORDSETS_IN_ZONE =
    sql"""
         |SELECT count(*)
         |  FROM recordset_data
         | WHERE zone_id = {zoneId}
    """.stripMargin

  private val BASE_FIND_RECORDSETS_BY_FQDNS =
    """
      |SELECT data, fqdn
      |  FROM recordset_data
      | WHERE fqdn
    """.stripMargin

  private val FIND_BY_ZONEID_TYPE =
    sql"""
         |SELECT data, fqdn
         |  FROM recordset_data
         | WHERE zone_id = {zoneId} AND type = {type}
    """.stripMargin

  private val FIND_BY_ID =
    sql"""
         |SELECT  data, fqdn
         |  FROM recordset_data
         | WHERE recordset_id = {recordset_id}
    """.stripMargin

  private val FIND_BY_IP =
    sql"""
         |SELECT data, fqdn
         |  FROM recordset_data
         | WHERE ip = {ip}
    """.stripMargin

  private final val logger = LoggerFactory.getLogger(classOf[MySqlRecordSetRepository])

  def save(db: DB, changeSet: ChangeSet): IO[ChangeSet] = {
    val byStatus = changeSet.changes.groupBy(_.status)
   val failedChanges = byStatus.getOrElse(RecordSetChangeStatus.Failed, Seq())
    val (failedCreates, failedUpdatesOrDeletes) =
      failedChanges.partition(_.changeType == RecordSetChangeType.Create)

   val reversionDeletes = failedCreates.map(d => Seq[Any](d.recordSet.id))
    failedUpdatesOrDeletes.flatMap { change =>
      change.updates.map { oldRs =>
        Seq[Any](
          rsDataSave(
            db,
            changeSet,
            oldRs.id,
            oldRs.zoneId,
            toFQDN(change.zone.name, oldRs.name),
            toFQDN(change.zone.name, oldRs.name).reverse,
            oldRs.typ.toString,
            oldRs.records.toString,
            toPB(oldRs).toByteArray,
            "update",
          )
        )
      }
    }
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
          rsDataSave(
            db,
            changeSet,
            i.recordSet.id,
            i.recordSet.records.toString,
            i.recordSet.typ.toString,
            i.recordSet.zoneId,
            toFQDN(i.zone.name, i.recordSet.name),
            toFQDN(i.zone.name, i.recordSet.name).reverse,
            toPB(i.recordSet).toByteArray,
            "insert",
        ))
      }
     completeUpdates.map { u =>
      Seq[Any](
        rsDataSave(
          db,
          changeSet,
          u.recordSet.id,
          u.recordSet.records.toString,
          u.recordSet.typ.toString,
          u.recordSet.zoneId,
          toFQDN(u.zone.name, u.recordSet.name),
          toFQDN(u.zone.name, u.recordSet.name).reverse,
          toPB(u.recordSet).toByteArray,
          "update",
      )
      )
    }
   val deletes: Seq[Seq[Any]] = completeDeletes.map(d => Seq[Any](d.recordSet.id))
    IO {
      db.withinTx { implicit session =>
       (deletes ++ reversionDeletes).grouped(1000).foreach { group =>
          DELETE_RECORDSETDATA.batch(group: _*).apply()
        }
      }
    }.as(changeSet)

  }

  def deleteRecordSetDatasInZone(db: DB, zoneId: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetDatasInZone") {
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

 /* def RsData(
              db: DB,
              recordID: String,
              recordData: String,
              recordType: String,
              zoneId: String,
              FQDN: String,
              reverseFQDN: String,
              rs: String,
              data: Array[Byte]

            ): Unit =
    rs match {

      /**
       *  insert the rsdata first, as if recordset are created
       */
      case "insert" => rsDataSave(db, rs, recordID, recordType, recordData, zoneId, FQDN, reverseFQDN, data)
      case "update" =>rsDataSave(db, rs, recordID, recordType, recordData, zoneId, FQDN, reverseFQDN, data)

    }*/

  def rsDataSave(
                  db: DB,
                  changeSet: ChangeSet,
                  recordId: String,
                  recordData: String,
                  recordType: String,
                  zoneId: String,
                  FQDN: String,
                  reverseFQDN: String,
                  data: Array[Byte],
                  rs: String

  ): Unit = {
    var parseIp: String = null
    var records: String = null
    for (ipString <- recordData.split(",").map(_.trim).toList) {
      parseIp = parseIP(ipString, recordType)
      records = recordData.replace("List(", "")
      records = records.replace(")", "")

      val byStatus = changeSet.changes.groupBy(_.status)
      val failedChanges = byStatus.getOrElse(RecordSetChangeStatus.Failed, Seq())
      val (failedCreates, failedUpdatesOrDeletes) =
        failedChanges.partition(_.changeType == RecordSetChangeType.Create)

      failedCreates.map(d => Seq[Any](d.recordSet.id))
      val completeChanges = byStatus.getOrElse(RecordSetChangeStatus.Complete, Seq())
      val completeChangesByType = completeChanges.groupBy(_.changeType)
      val completeCreates = completeChangesByType.getOrElse(RecordSetChangeType.Create, Seq())
      val completeUpdates = completeChangesByType.getOrElse(RecordSetChangeType.Update, Seq())
      completeChangesByType.getOrElse(RecordSetChangeType.Delete, Seq())

      val pendingChanges = byStatus.getOrElse(RecordSetChangeStatus.Pending, Seq())
      /**
       *  insert the rsdata first, as if recordset are created/updated
       */
      rs match {
        case "insert" =>
          val inserts: Seq[Seq[Any]] =
            (completeCreates ++ pendingChanges).map { i =>
              Seq[Any](
                  i.recordSet.id,
                  i.recordSet.records.toString,
                  i.recordSet.typ.toString,
                  i.recordSet.zoneId,
                  toFQDN(i.zone.name, i.recordSet.name),
                  toFQDN(i.zone.name, i.recordSet.name).reverse,
                  toPB(i.recordSet).toByteArray

              )
            }
          db.withinTx { implicit session =>
            inserts{
            INSERT_RECORDSETDATA
              .bindByName(
                'recordset_id -> recordId,
                'zone_id -> zoneId,
                'fqdn -> FQDN,
                'reverse_fqdn -> reverseFQDN,
                'type -> recordType,
                'record_data -> records,
                'ip -> parseIp,
                'data -> data
              )
              .update()
              .apply()
          }}
        case "update" =>
          val reversionUpdates = failedUpdatesOrDeletes.flatMap { change =>
            change.updates.map { oldRs =>
              Seq[Any](
                  db,
                  oldRs.id,
                  oldRs.zoneId,
                  toFQDN(change.zone.name, oldRs.name),
                  toFQDN(change.zone.name, oldRs.name).reverse,
                  oldRs.typ.toString,
                  oldRs.records.toString,
                  toPB(oldRs).toByteArray
              )
            }
          }
          val updates =
            completeUpdates.map { u =>
              Seq[Any](
                  db,
                  u.recordSet.id,
                  u.recordSet.records.toString,
                  u.recordSet.typ.toString,
                  u.recordSet.zoneId,
                  toFQDN(u.zone.name, u.recordSet.name),
                  toFQDN(u.zone.name, u.recordSet.name).reverse,
                  toPB(u.recordSet).toByteArray
              )
            }
          db.withinTx { implicit session =>
            (reversionUpdates++updates).map{_ =>
              UPDATE_RECORDSETDATA
              .bindByName(
                'zone_id -> zoneId,
                'fqdn -> FQDN,
                'reverse_fqdn -> reverseFQDN,
                'type -> recordType,
                'record_data -> records,
                'ip -> parseIp,
                'data -> data,
                'recordset_id -> recordId
              )
              .update()
              .apply()
          }
      }}
    }
  }

  def getRecordSetDatas(zoneId: String, typ: RecordType): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetDatas") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ZONEID_TYPE
            .bindByName(
              'zoneId -> zoneId,
              'type -> typ.toString )
            .map(toRecordSet)
            .list()
            .apply()
        }
      }
    }

  // Note: In MySql we do not need the zone id, since can hit the key directly
  def getRecordSetData(recordset_id: String): IO[Option[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetData") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ID.bindByName('recordset_id -> recordset_id).map(toRecordSetData).single().apply()
        }
      }
    }

  def getRecordSetDataByIP(recordSetIP: String): IO[Option[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetData") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_IP.bindByName('ip -> recordSetIP).map(toRecordSetData).single().apply()
        }
      }
    }

  def getRecordSetDatasByFQDNs(names: Set[String]): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetDatasByFQDNs") {
      IO {
        if (names.isEmpty)
          List[RecordSet]()
        else {
          DB.readOnly { implicit s =>
            val namesList = names.toList
            val inClause = " IN (" + namesList.as("?").mkString(",") + ")"
            val query = BASE_FIND_RECORDSETS_BY_FQDNS + inClause
            SQL(query)
              .bind(namesList: _*)
              .map(toRecordSet)
              .list()
              .apply()
          }
        }
      }
    }

  def getRecordSetDataCount(zoneId: String): IO[Int] =
    monitor("repo.RecordSet.getRecordSetDataCount") {
      IO {
        DB.readOnly { implicit s =>
          // this is a count query, so should always return a value.  However, scalikejdbc doesn't have this,
          // so we have to default to 0.  it is literally impossible to not return a value
          COUNT_RECORDSETS_IN_ZONE
            .bindByName('zoneId -> zoneId)
            .map(_.int(1))
            .single
            .apply()
            .getOrElse(0)
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
     *  parse ip address from the recordset data
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


  def toRecordSetData(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1))).copy(fqdn = rs.stringOpt(2))


}