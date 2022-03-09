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
          RsData(
            db,
            oldRs.id,
            oldRs.zoneId,
            toFQDN(change.zone.name, oldRs.name),
            toFQDN(change.zone.name, oldRs.name).reverse,
            oldRs.typ.toString,
            oldRs.records.toString,
            "update",
            toPB(oldRs).toByteArray,
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
        RsData(
          db,
          i.recordSet.id,
          i.recordSet.records.toString,
          i.recordSet.typ.toString,
          i.recordSet.zoneId,
          toFQDN(i.zone.name, i.recordSet.name),
          toFQDN(i.zone.name, i.recordSet.name).reverse,
          "insert",
          toPB(i.recordSet).toByteArray,
        ))
    }
    completeUpdates.map { u =>
      Seq[Any](
        RsData(
          db,
          u.recordSet.id,
          u.recordSet.records.toString,
          u.recordSet.typ.toString,
          u.recordSet.zoneId,
          toFQDN(u.zone.name, u.recordSet.name),
          toFQDN(u.zone.name, u.recordSet.name).reverse,
          "update",
          toPB(u.recordSet).toByteArray,
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

  def RsData(
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
       * insert the rsdata first, as if recordset are created
       */
      case "insert" => rsDataSave(db, recordID, recordData, recordType, zoneId, FQDN, reverseFQDN, data)
      case "update" =>
        db.withinTx { implicit session =>
          DELETE_RECORDSETDATA
            .bind(recordID)
            .update()
            .apply()
        }
        rsDataSave(db, recordID, recordData, recordType, zoneId, FQDN, reverseFQDN, data)
    }

  def rsDataSave(
                  db: DB,
                  recordId: String,
                  recordData: String,
                  recordType: String,
                  zoneId: String,
                  FQDN: String,
                  reverseFQDN: String,
                  data: Array[Byte]
                ): Unit = {
    recordType match {
      case "DS" => for (ipString<- recordData.split(Pattern.quote("),")).map(_.trim).toList) {
        insertRecordSetData(db, recordId, zoneId, FQDN, reverseFQDN, recordType, ipString, data)}
      case _ => for (ipString <- recordData.split(",").map(_.trim).toList) {
        insertRecordSetData(db,recordId,zoneId,FQDN,reverseFQDN,recordType,ipString,data)}
    }}

  def insertRecordSetData(
              db: DB,
              recordId: String,
              zoneId: String,
              FQDN: String,
              reverseFQDN: String,
              recordType: String,
              ipString: String,
              data: Array[Byte]
            ): Unit = {
    var parseIp: String = null
    var records: String = null
    parseIp = parseIP(ipString, recordType)
    records = ipString.replace("List(", "")
    records = records.replace(")", "")
    records = extractRecordSetDataString(records, recordType)

  /**
   * insert the rsdata first, as if recordset are created/updated
   */

  db.withinTx { implicit session =>
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

  def getRecordSetDataList(zoneId: String, typ: RecordType): IO[List[RecordSet]] =
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

  def extractRecordSetDataString(
               recordSetData: String,
               recordType: String
             ): String = {
    var records : String = null
    recordType match {

      /**
       * Append the textual representation of the record data.
       */

      case "A"|"AAAA" =>   records = "address:\"".concat(recordSetData+"\"")
      case "CNAME" =>  records = "cname:\"".concat(recordSetData+"\"")
      case "SOA" => val rs=recordSetData.split(" ")
        records=
          "mname:\"".concat(rs(0)+"\"").
            concat("  rname:\""+rs(1)+"\"").
            concat("  serial:"+rs(2)).
            concat("  refresh:"+rs(3)).
            concat("  retry:"+rs(4)).
            concat("  expire:"+rs(5)).
            concat("  minimum:"+rs(6))
      case "DS" => val rs=recordSetData.split(" ")
        rs(5)=rs(5).replace("))","")
        records=
          "keyTag:".concat(rs(0)).
            concat("  algorithm:"+rs(1)).
            concat("  digestType:"+rs(2)).
            concat("  digest:\""+rs(5)+"\"")
      case "MX" => val rs=recordSetData.split(" ")
        records=
          "preference:".concat(rs(0)).
            concat("  exchange:\""+rs(1)+"\"")
      case "NS" =>  records= "nsdname:\"".concat(recordSetData+"\"")
      case "PTR" => records= "ptrdname:\"".concat(recordSetData+"\"")
      case "SPF" => records= "text:\"".concat(recordSetData+"\"")
      case "SRV" => val rs=recordSetData.split(" ")
        records=
          "priority:".concat(rs(0)).
            concat("  weight:"+rs(1)).
            concat("  port:"+rs(2)).
            concat("  target:\""+rs(3)+"\"")
      case "NAPTR" => val rs=recordSetData.split(" ")
        records=
          "order:".concat(rs(0)).
            concat("  preference:"+rs(1)).
            concat("  flags:\""+rs(2)+"\"").
            concat("  service:\""+rs(3)+"\"").
            concat("  regexp:\""+rs(4)+"\"").
            concat("  replacement:\""+rs(5)+"\"")
      case "SSHFP" =>
        val rs=recordSetData.split(" ")
        records=
          "algorithm:".concat(rs(0)).
            concat("  typ:"+rs(1)).
            concat("  fingerPrint:\""+rs(2)+"\"")
      case "TXT" => records= "text:\"".concat(recordSetData+"\"")
      case "UNKNOWN" => records= "UnknownRecordType:\"".concat(recordSetData+"\"")
      case _ => records= "null"
  }
    records
  }

  def toRecordSetData(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1))).copy(fqdn = rs.stringOpt(2))


}