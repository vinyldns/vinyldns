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
import cats.implicits._
import scalikejdbc._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

class MySqlRecordSetRepository extends RecordSetRepository with Monitored {
  import MySqlRecordSetRepository._

  private val FIND_BY_ZONEID_NAME_TYPE =
    sql"""
      |SELECT data
      |  FROM recordset
      | WHERE zone_id = {zoneId} AND name = {name} AND type = {type}
    """.stripMargin

  private val FIND_BY_ZONEID_NAME =
    sql"""
         |SELECT data
         |  FROM recordset
         | WHERE zone_id = {zoneId} AND name = {name}
    """.stripMargin

  private val FIND_BY_ID =
    sql"""
         |SELECT data
         |  FROM recordset
         | WHERE id = {id}
    """.stripMargin

  private val COUNT_RECORDSETS_IN_ZONE =
    sql"""
         |SELECT count(*)
         |  FROM recordset
         | WHERE zone_id = {zoneId}
    """.stripMargin

  private val INSERT_RECORDSET =
    sql"INSERT IGNORE INTO recordset(id, zone_id, name, fqdn, type, data) VALUES (?, ?, ?, ?, ?, ?)"

  private val UPDATE_RECORDSET =
    sql"UPDATE recordset SET zone_id = ?, name = ?, fqdn = ?, type = ?, data = ? WHERE id = ?"

  private val DELETE_RECORDSET =
    sql"DELETE FROM recordset WHERE id = ?"

  private val FIND_BY_FQDN =
    """
         |SELECT data
         |  FROM recordset
         | WHERE fqdn
    """.stripMargin

  def apply(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordSet.apply") {
      val byChangeType = changeSet.changes.groupBy(_.changeType)
      val inserts: Seq[Seq[Any]] = byChangeType.getOrElse(RecordSetChangeType.Create, Nil).map {
        i =>
          Seq[Any](
            i.recordSet.id,
            i.recordSet.zoneId,
            i.recordSet.name,
            FQDN(i.recordSet.name, i.zone.name).value,
            fromRecordType(i.recordSet.typ),
            toPB(i.recordSet).toByteArray
          )
      }

      val updates: Seq[Seq[Any]] = byChangeType.getOrElse(RecordSetChangeType.Update, Nil).map {
        u =>
          Seq[Any](
            u.zoneId,
            u.recordSet.name,
            FQDN(u.recordSet.name, u.zone.name).value,
            fromRecordType(u.recordSet.typ),
            toPB(u.recordSet).toByteArray,
            u.recordSet.id)
      }

      // deletes are just the record set id
      val deletes: Seq[Seq[Any]] =
        byChangeType.getOrElse(RecordSetChangeType.Delete, Nil).map(d => Seq[Any](d.recordSet.id))

      IO {
        DB.localTx { implicit s =>
          // sql batch groups should preferably be smaller rather than larger for performance purposes
          // to reduce contention on the table.  1000 worked well in performance tests
          inserts.grouped(1000).foreach { group =>
            INSERT_RECORDSET.batch(group: _*).apply()
          }
          updates.grouped(1000).foreach { group =>
            UPDATE_RECORDSET.batch(group: _*).apply()
          }
          deletes.grouped(1000).foreach { group =>
            DELETE_RECORDSET.batch(group: _*).apply()
          }
        }
      }.as(changeSet)
    }

  /**
    * TODO: There is a potential issue with the way we do this today.  We load all record sets eagerly, potentially
    * causing memory pressure for the app depending on the number of records in the zone.
    *
    * This needs to change in the future; however, for right now we just need to tune
    * the JVM as we have the same issue in the DynamoDB repository.  Until
    * we create a better sync and load process that is better for memory, this should
    * be the same as the other repo.
    */
  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): IO[ListRecordSetResults] =
    monitor("repo.RecordSet.listRecordSets") {
      IO {
        DB.readOnly { implicit s =>
          // make sure we sort ascending, so we can do the correct comparison later
          val opts = (startFrom.as("AND name > {startFrom}") ++
            recordNameFilter.as("AND name LIKE {nameFilter}") ++
            Some("ORDER BY name ASC") ++
            maxItems.as("LIMIT {maxItems}")).toList.mkString(" ")

          val params = (Some('zoneId -> zoneId) ++
            startFrom.map(n => 'startFrom -> n) ++
            recordNameFilter.map(f => 'nameFilter -> s"%$f%") ++
            maxItems.map(m => 'maxItems -> m)).toSeq

          val query = "SELECT data FROM recordset WHERE zone_id = {zoneId} " + opts

          val results = SQL(query)
            .bindByName(params: _*)
            .map(toRecordSet)
            .list()
            .apply()

          // if size of results is less than the number returned, we don't have a next id
          // if maxItems is None, we don't have a next id
          val nextId =
            maxItems.filter(_ == results.size).flatMap(_ => results.lastOption.map(_.name))

          ListRecordSetResults(
            recordSets = results,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            recordNameFilter = recordNameFilter
          )
        }
      }
    }

  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSets") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ZONEID_NAME_TYPE
            .bindByName('zoneId -> zoneId, 'name -> name, 'type -> fromRecordType(typ))
            .map(toRecordSet)
            .list()
            .apply()
        }
      }
    }

  // Note: In MySql we do not need the zone id, since can hit the key directly
  def getRecordSet(zoneId: String, recordSetId: String): IO[Option[RecordSet]] =
    monitor("repo.RecordSet.getRecordSet") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ID.bindByName('id -> recordSetId).map(toRecordSet).single().apply()
        }
      }
    }

  def getRecordSetCount(zoneId: String): IO[Int] =
    monitor("repo.RecordSet.getRecordSetCount") {
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

  def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetsByName") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ZONEID_NAME
            .bindByName('zoneId -> zoneId, 'name -> name)
            .map(toRecordSet)
            .list()
            .apply()
        }
      }
    }

  def getRecordSetsByFQDN(fqdns: List[FQDN]): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetsByFQDN") {
      IO {
        DB.readOnly { implicit s =>
          val inClause = " IN (" + fqdns.as("?").mkString(",") + ")"
          val query = FIND_BY_FQDN + inClause
          SQL(query).bind(fqdns.map(_.value): _*).map(toRecordSet).list().apply()
        }
      }
    }
}

object MySqlRecordSetRepository extends ProtobufConversions {
  val unknownRecordType: Int = 100
  val recordTypeLookup: Map[RecordType, Int] = Map(
    RecordType.A -> 1,
    RecordType.AAAA -> 2,
    RecordType.CNAME -> 3,
    RecordType.MX -> 4,
    RecordType.NS -> 5,
    RecordType.PTR -> 6,
    RecordType.SPF -> 7,
    RecordType.SRV -> 8,
    RecordType.SSHFP -> 9,
    RecordType.TXT -> 10,
    RecordType.UNKNOWN -> unknownRecordType
  )

  def toRecordSet(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1)))

  def fromRecordType(typ: RecordType): Int =
    recordTypeLookup.getOrElse(typ, unknownRecordType)
}
