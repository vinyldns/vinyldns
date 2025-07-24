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

import cats.effect._
import scalikejdbc._
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.mysql.repository.MySqlRecordSetRepository.{fromRecordType, toFQDN}
import vinyldns.proto.VinylDNSProto

class MySqlRecordChangeRepository
    extends RecordChangeRepository
    with Monitored
    with ProtobufConversions {
  import MySqlRecordChangeRepository._

  private val LIST_CHANGES_WITH_START =
    sql"""
      |SELECT data
      | FROM record_change
      | WHERE zone_id = {zoneId}
      | ORDER BY created DESC
      | LIMIT {limit} OFFSET {startFrom}
    """.stripMargin

  private val LIST_CHANGES_WITH_START_FQDN_TYPE =
    sql"""
       |SELECT data
       | FROM record_change
       | WHERE fqdn = {fqdn} AND record_type = {type}
       | ORDER BY created DESC
       | LIMIT {limit} OFFSET {startFrom}
    """.stripMargin

  private val LIST_CHANGES_WITHOUT_START_FQDN_TYPE =
    sql"""
         |SELECT data
         | FROM record_change
         | WHERE fqdn = {fqdn} AND record_type = {type}
         | ORDER BY created DESC
         | LIMIT {limit}
    """.stripMargin

  private val LIST_RECORD_CHANGES =
    sql"""
         |SELECT data
         |  FROM record_change
         |  WHERE zone_id = {zoneId}
         |  ORDER BY created DESC
    """.stripMargin

  private val LIST_CHANGES_NO_START =
    sql"""
      |SELECT data
      | FROM record_change
      | WHERE zone_id = {zoneId}
      | ORDER BY created DESC
      | LIMIT {limit}
    """.stripMargin

  private val GET_CHANGE =
    sql"""
      |SELECT data
      |  FROM record_change
      | WHERE id = {id}
    """.stripMargin

  private val INSERT_CHANGES =
    sql"INSERT IGNORE INTO record_change (id, zone_id, created, type, fqdn, record_type, data) VALUES (?, ?, ?, ?, ?, ?, ?)"

  /**
    * We have the same issue with changes as record sets, namely we may have to save millions of them
    * We do not need to distinguish between create, update, delete so this is simpler
    */
  def save(db: DB, changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordChange.save") {
      IO {
        db.withinTx { implicit session =>
          changeSet.changes
            .grouped(1000)
            .map { changes =>
              changes.map { change =>
                Seq(
                  change.id,
                  change.zoneId,
                  change.created.toEpochMilli,
                  fromChangeType(change.changeType),
                  toFQDN(change.zone.name, change.recordSet.name),
                  fromRecordType(change.recordSet.typ),
                  toPB(change).toByteArray,
                )
              }
            }
            .foreach { group =>
              INSERT_CHANGES.batch(group: _*).apply()
            }
        }
      }.as(changeSet)
    }

  def listRecordSetChanges(
      zoneId: Option[String],
      startFrom: Option[Int],
      maxItems: Int,
      fqdn: Option[String],
      recordType: Option[RecordType]
  ): IO[ListRecordSetChangesResults] =
    monitor("repo.RecordChange.listRecordSetChanges") {
      IO {
        DB.readOnly { implicit s =>
          val changes = if(startFrom.isDefined && fqdn.isDefined && recordType.isDefined){
          LIST_CHANGES_WITH_START_FQDN_TYPE
            .bindByName('fqdn -> fqdn.get, 'type -> fromRecordType(recordType.get), 'startFrom -> startFrom.get, 'limit -> (maxItems + 1))
            .map(toRecordSetChange)
            .list()
            .apply()
          } else if(fqdn.isDefined && recordType.isDefined){
            LIST_CHANGES_WITHOUT_START_FQDN_TYPE
              .bindByName('fqdn -> fqdn.get, 'type -> fromRecordType(recordType.get), 'limit -> (maxItems + 1))
              .map(toRecordSetChange)
              .list()
              .apply()
          } else if(startFrom.isDefined){
            LIST_CHANGES_WITH_START
              .bindByName('zoneId -> zoneId.get, 'startFrom -> startFrom.get, 'limit -> (maxItems + 1))
              .map(toRecordSetChange)
              .list()
              .apply()
          } else {
            LIST_CHANGES_NO_START
              .bindByName('zoneId -> zoneId.get, 'limit -> (maxItems + 1))
              .map(toRecordSetChange)
              .list()
              .apply()
          }

          val maxQueries = changes.take(maxItems)
          val startValue = startFrom.getOrElse(0)

          // earlier maxItems was incremented, if the (maxItems + 1) size is not reached then pages are exhausted
          val nextId = changes match {
            case _ if changes.size <= maxItems | changes.isEmpty => None
            case _ => Some(startValue + maxItems)
          }

          ListRecordSetChangesResults(
            maxQueries,
            nextId,
            startFrom,
            maxItems
          )
        }
      }
    }

  def listFailedRecordSetChanges(zoneId: Option[String], maxItems: Int, startFrom: Int): IO[ListFailedRecordSetChangesResults] =
    monitor("repo.RecordChange.listFailedRecordSetChanges") {
      IO {
        DB.readOnly { implicit s =>
          val queryResult = LIST_RECORD_CHANGES
            .bindByName('zoneId -> zoneId.get)
            .map(toRecordSetChange)
            .list()
            .apply()
          val failedRecordSetChanges = queryResult.filter(rc => rc.status == RecordSetChangeStatus.Failed).drop(startFrom).take(maxItems)
          val nextId = if (failedRecordSetChanges.size < maxItems) 0 else startFrom + maxItems + 1
          ListFailedRecordSetChangesResults(failedRecordSetChanges,nextId,startFrom,maxItems)
        }
      }
    }

  def getRecordSetChange(zoneId: String, changeId: String): IO[Option[RecordSetChange]] =
    monitor("repo.RecordChange.listRecordSetChanges") {
      IO {
        DB.readOnly { implicit s =>
          GET_CHANGE.bindByName('id -> changeId).map(toRecordSetChange).single().apply()
        }
      }
    }
}

object MySqlRecordChangeRepository extends ProtobufConversions {
  def fromChangeType(ct: RecordSetChangeType): Int = ct match {
    case RecordSetChangeType.Create => 1
    case RecordSetChangeType.Delete => 2
    case RecordSetChangeType.Update => 3
    case RecordSetChangeType.Sync => 4
  }

  def toRecordSetChange(ws: WrappedResultSet): RecordSetChange =
    fromPB(VinylDNSProto.RecordSetChange.parseFrom(ws.bytes(1)))
}
