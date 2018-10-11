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

import java.sql.Connection

import cats.effect._
import cats.implicits._
import org.mariadb.jdbc.MariaDbBlob
import scalikejdbc._
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

class MySqlRecordChangeRepository
    extends RecordChangeRepository
    with Monitored
    with ProtobufConversions {
  import MySqlRecordChangeRepository._

  // Important!  MySQL 5.6 does not have sorted indexes, so this maybe slow
  private val LIST_CHANGES_WITH_START =
    sql"""
      |SELECT data
      |  FROM record_change
      | WHERE zone_id = ?
      |   AND created < ?
      |  ORDER BY created DESC
      |  LIMIT ?
    """.stripMargin

  private val LIST_CHANGES_NO_START =
    sql"""
         |SELECT data
         |  FROM record_change
         | WHERE zone_id = ?
         |  ORDER BY created DESC
         |  LIMIT ?
    """.stripMargin

  private val GET_CHANGE =
    sql"""
      |SELECT data
      |  FROM record_change
      | WHERE id = ?
    """.stripMargin

  private def insert(records: Seq[RecordSetChange], conn: Connection): Seq[Int] = {
    // Important!  We must do INSERT IGNORE here as we cannot do ON DUPLICATE KEY UPDATE
    // with this mysql bulk insert.  To maintain idempotency, we must handle the possibility
    // of the same insert happening multiple times.  IGNORE will ignore all errors unfortunately,
    // but I fear we have no choice in the matter
    val ps = conn.prepareStatement(
      "INSERT IGNORE INTO record_change (id, zone_id, created, type, data) VALUES (?, ?, ?, ?, ?)")
    records.foreach { r =>
      ps.setString(1, r.id)
      ps.setString(2, r.zoneId)
      ps.setLong(3, r.created.getMillis)
      ps.setInt(4, fromChangeType(r.changeType))
      ps.setBlob(5, new MariaDbBlob(toPB(r).toByteArray))
      ps.addBatch()
    }
    ps.executeBatch().toSeq
  }

  /**
    * We have the same issue with changes as record sets, namely we may have to save millions of them
    *
    * We do not need to distinguish between create, update, delete so this is simpler
    */
  def save(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordChange.save") {
      IO {
        DB.localTx { implicit s =>
          changeSet.changes.grouped(1000).foreach { group =>
            insert(group, s.connection)
          }
        }
      }.as(changeSet)
    }

  def listRecordSetChanges(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Int): IO[ListRecordSetChangesResults] =
    monitor("repo.RecordChange.listRecordSetChanges") {
      IO {
        DB.readOnly { implicit s =>
          val changes = startFrom match {
            case Some(start) =>
              LIST_CHANGES_WITH_START
                .bind(zoneId, start.toLong, maxItems)
                .map(toRecordSetChange)
                .list()
                .apply()
            case None =>
              LIST_CHANGES_NO_START.bind(zoneId, maxItems).map(toRecordSetChange).list().apply()
          }

          val nextId =
            if (changes.size < maxItems) None
            else changes.lastOption.map(_.created.getMillis.toString)

          ListRecordSetChangesResults(
            changes,
            nextId,
            startFrom,
            maxItems
          )
        }
      }
    }

  def getRecordSetChange(zoneId: String, changeId: String): IO[Option[RecordSetChange]] =
    monitor("repo.RecordChange.listRecordSetChanges") {
      IO {
        DB.readOnly { implicit s =>
          GET_CHANGE.bind(changeId).map(toRecordSetChange).single().apply()
        }
      }
    }
}

object MySqlRecordChangeRepository extends ProtobufConversions {
  val changeTypeLookup: Map[RecordSetChangeType, Int] = Map(
    RecordSetChangeType.Create -> 1,
    RecordSetChangeType.Delete -> 2,
    RecordSetChangeType.Update -> 3
  )

  // This can throw an exception if the change type is not in the lookup map
  def fromChangeType(ct: RecordSetChangeType): Int = changeTypeLookup(ct)

  def toRecordSetChange(ws: WrappedResultSet): RecordSetChange =
    fromPB(VinylDNSProto.RecordSetChange.parseFrom(ws.bytes(1)))
}
