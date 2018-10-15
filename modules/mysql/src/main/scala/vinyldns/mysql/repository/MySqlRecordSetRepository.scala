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

  /**
    * This is going to be tricky.  We need to generate INSERT INTO IGNORE VALUES
    * using bulk insert syntax.  We have to group these into batches of some arbitrary size
    * and loop through until we are done.
    *
    * This is going to be especially painful for large zones (millions of records)
    *
    * The size of each group needs to be profiled.  Articles point to being able to do bulk
    * insert of 200,000 rows per second (for small rows of 26 bytes).  Our rows will be considerably
    * larger than that (maybe 500 bytes).  Attempting to do batches of 1000 and seeing where
    * we can go from there.  This will be adjusted as part of benchmark, we may opt to make it
    * a configuration setting
    */
  def apply(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.MySql.apply") {
      val byChangeType = changeSet.changes.groupBy(_.changeType)
      val inserts: Seq[Seq[Any]] = byChangeType.getOrElse(RecordSetChangeType.Create, Nil).map {
        i =>
          Seq[Any](
            i.recordSet.id,
            i.zoneId,
            i.recordSet.name,
            FQDN(i.recordSet.name, i.zone.name),
            fromRecordType(i.recordSet.typ),
            toPB(i.recordSet).toByteArray
          )
      }

      val updates: Seq[Seq[Any]] = byChangeType.getOrElse(RecordSetChangeType.Update, Nil).map {
        u =>
          Seq[Any](
            u.zoneId,
            u.recordSet.name,
            FQDN(u.recordSet.name, u.zone.name),
            fromRecordType(u.recordSet.typ),
            toPB(u.recordSet).toByteArray,
            u.recordSet.id)
      }

      // deletes are just the record set id
      val deletes: Seq[Seq[Any]] =
        byChangeType.getOrElse(RecordSetChangeType.Delete, Nil).map(d => Seq[Any](d.recordSet.id))

      // Totally don't know if this is right or not, are effectively doing the entire operation in a single transaction
      // and from what I have read that is the correct way to do it
      // Most zones won't have many changes on a sync once the zone is in vinyldns
      // However, for initially loading a zone, we could have millions of inserts.  Need to fine tune
      // both the INSERT SQL as well as how we are using scalikejdbc
      IO {
        DB.localTx { implicit s =>
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
    * maxItems is an option here because we use this to load all record sets in a zone, which could be very large
    * (millions of records).  This could cause memory issues for the application.
    *
    * For DynamoDB, we can only get 100 at a time.  We do not know what the "limit" is right now,
    * so we will attempt to load all and tune this during benchmark
    */
  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): IO[ListRecordSetResults] =
    monitor("repo.MySql.listRecordSets") {
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

  // TODO: Refactor this, need "getRecordSetsByNameAndType"
  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]] =
    monitor("repo.MySql.getRecordSets") {
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

  // Note: In MySql we do not need the zone id, we can hit the key directly
  def getRecordSet(zoneId: String, recordSetId: String): IO[Option[RecordSet]] =
    monitor("repo.MySql.getRecordSet") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ID.bindByName('id -> recordSetId).map(toRecordSet).single().apply()
        }
      }
    }

  def getRecordSetCount(zoneId: String): IO[Int] =
    monitor("repo.MySql.getRecordSetCount") {
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
    monitor("repo.MySql.getRecordSetsByName") {
      IO {
        DB.readOnly { implicit s =>
          // this is a count query, so should always return a value.  However, scalikejdbc doesn't have this,
          // so we have to default to 0.  it is literally impossible to not return a value
          FIND_BY_ZONEID_NAME
            .bindByName('zoneId -> zoneId, 'name -> name)
            .map(toRecordSet)
            .list()
            .apply()
        }
      }
    }

  def getRecordSetsByFQDN(fqdns: List[FQDN]): IO[List[RecordSet]] =
    monitor("repo.MySql.getRecordSetByFQDN") {
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
  final case class InvalidRecordType(value: Int)
      extends Throwable(s"Invalid record type value $value")

  val unknownRecordType: Int = 100
  val recordTypeLookup: Map[Int, RecordType] = Map(
    1 -> RecordType.A,
    2 -> RecordType.AAAA,
    3 -> RecordType.CNAME,
    4 -> RecordType.MX,
    5 -> RecordType.NS,
    6 -> RecordType.PTR,
    7 -> RecordType.SPF,
    8 -> RecordType.SRV,
    9 -> RecordType.SSHFP,
    10 -> RecordType.TXT,
    unknownRecordType -> RecordType.UNKNOWN
  )
  val inverseRecordTypeLookup: Map[RecordType, Int] = recordTypeLookup.map { case (i, t) => t -> i }

  def toRecordSet(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1)))

  def toRecordType(i: Int): Either[InvalidRecordType, RecordType] =
    recordTypeLookup.get(i).map(t => Right(t)).getOrElse(Left(InvalidRecordType(i)))

  def fromRecordType(typ: RecordType): Int =
    inverseRecordTypeLookup.getOrElse(typ, unknownRecordType)
}
