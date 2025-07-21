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
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto
import java.io.{PrintWriter, StringWriter}
import scala.util.Try

class MySqlRecordSetRepository extends RecordSetRepository with Monitored {
  import MySqlRecordSetRepository._

  private val FIND_BY_ZONEID_NAME_TYPE =
    sql"""
         |SELECT data, fqdn
         |  FROM recordset
         | WHERE zone_id = {zoneId} AND name = {name} AND type = {type}
    """.stripMargin

  private val FIND_BY_ZONEID_NAME =
    sql"""
         |SELECT data, fqdn
         |  FROM recordset
         | WHERE zone_id = {zoneId} AND name = {name}
    """.stripMargin

  private val FIND_BY_ID =
    sql"""
         |SELECT data, fqdn
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
    sql"INSERT IGNORE INTO recordset(id, zone_id, name, type, data, fqdn, owner_group_id) VALUES (?, ?, ?, ?, ?, ?, ?)"

  private val UPDATE_RECORDSET =
    sql"UPDATE recordset SET zone_id = ?, name = ?, type = ?, data = ?, fqdn = ?, owner_group_id = ? WHERE id = ?"

  private val DELETE_RECORDSET =
    sql"DELETE FROM recordset WHERE id = ?"

  private val DELETE_RECORDSETS_IN_ZONE =
    sql"DELETE FROM recordset WHERE zone_id = ?"

  private val BASE_FIND_RECORDSETS_BY_FQDNS =
    """
      |SELECT data, fqdn
      |  FROM recordset
      | WHERE fqdn
    """.stripMargin

  private val GET_RECORDSET_BY_OWNERID =
    sql"""
         |SELECT id
         |  FROM recordset
         |WHERE owner_group_id = {ownerGroupId}
         |LIMIT 1
    """.stripMargin

  private final val logger = LoggerFactory.getLogger(classOf[MySqlRecordSetRepository])

  def apply(db: DB, changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordSet.apply") {

      // identify failed changes
      val byStatus = changeSet.changes.groupBy(_.status)

      // address failed changes
      val failedChanges = byStatus.getOrElse(RecordSetChangeStatus.Failed, Seq())
      val (failedCreates, failedUpdatesOrDeletes) =
        failedChanges.partition(_.changeType == RecordSetChangeType.Create)

      val reversionDeletes = failedCreates.map(d => Seq[Any](d.recordSet.id))
      val reversionUpdates = failedUpdatesOrDeletes.flatMap { change =>
        change.updates.map { oldRs =>
          Seq[Any](
            oldRs.zoneId,
            oldRs.name,
            fromRecordType(oldRs.typ),
            toPB(oldRs).toByteArray,
            toFQDN(change.zone.name, oldRs.name),
            oldRs.ownerGroupId,
            oldRs.id
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
      val inserts: Seq[Seq[Any]] =
        (completeCreates ++ pendingChanges).map { i =>
          Seq[Any](
            i.recordSet.id,
            i.recordSet.zoneId,
            i.recordSet.name,
            fromRecordType(i.recordSet.typ),
            toPB(i.recordSet).toByteArray,
            toFQDN(i.zone.name, i.recordSet.name),
            i.recordSet.ownerGroupId,
          )
        }

      val updates: Seq[Seq[Any]] =
        completeUpdates.map { u =>
          Seq[Any](
            u.zoneId,
            u.recordSet.name,
            fromRecordType(u.recordSet.typ),
            toPB(u.recordSet).toByteArray,
            toFQDN(u.zone.name, u.recordSet.name),
            u.recordSet.ownerGroupId,
            u.recordSet.id
          )
        }

      val deletes: Seq[Seq[Any]] = completeDeletes.map(d => Seq[Any](d.recordSet.id))
        IO {
          db.withinTx { implicit session =>
            // sql batch groups should preferably be smaller rather than larger for performance purposes
            // to reduce contention on the table.  1000 worked well in performance tests
            inserts.grouped(1000).foreach { group =>
              INSERT_RECORDSET.batch(group: _*).apply()
            }
            (updates ++ reversionUpdates).grouped(1000).foreach { group =>
              UPDATE_RECORDSET.batch(group: _*).apply()
            }
            (deletes ++ reversionDeletes).grouped(1000).foreach { group =>
              DELETE_RECORDSET.batch(group: _*).apply()
            }
          }
      }.as(changeSet)
    }

  def listRecordSets(
                      zoneId: Option[String],
                      startFrom: Option[String],
                      maxItems: Option[Int],
                      recordNameFilter: Option[String],
                      recordTypeFilter: Option[Set[RecordType]],
                      recordOwnerGroupFilter: Option[String],
                      nameSort: NameSort,
                      recordTypeSort: RecordTypeSort
                    ): IO[ListRecordSetResults] =
    monitor("repo.RecordSet.listRecordSets") {
      IO {
        DB.readOnly { implicit s =>
          val maxPlusOne = maxItems.map(_ + 1)
          // setup optional filters
          val zoneAndNameFilters = (zoneId, recordNameFilter) match {
            case (Some(zId), Some(rName)) =>
              Some(sqls"zone_id = $zId AND name LIKE ${rName.replace('*', '%').replace('.', '%')} ")
            case (None, Some(fqdn)) => Some(sqls"fqdn LIKE ${fqdn.replace('*', '%')} ")
            case (Some(zId), None) => Some(sqls"zone_id = $zId ")
            case _ => None
          }
          val searchByZone = zoneId.fold[Boolean](false)(_ => true)
          val pagingKey = PagingKey(startFrom)

          // sort by name or fqdn in given order
          val sortBy = (searchByZone, nameSort) match {
            case (true, NameSort.DESC) =>
              pagingKey.as(
                sqls"((name <= ${pagingKey.map(pk => pk.recordName)} AND type > ${pagingKey.map(pk => pk.recordType)}) OR name < ${pagingKey.map(pk => pk.recordName)})"
              )
            case (false, NameSort.ASC) =>
              pagingKey.as(
                sqls"((fqdn >= ${pagingKey.map(pk => pk.recordName)} AND type > ${pagingKey.map(pk => pk.recordType)}) OR fqdn > ${pagingKey.map(pk => pk.recordName)})"
              )
            case (false, NameSort.DESC) =>
              pagingKey.as(
                sqls"((fqdn <= ${pagingKey.map(pk => pk.recordName)} AND type > ${pagingKey.map(pk => pk.recordType)}) OR fqdn < ${pagingKey.map(pk => pk.recordName)})"
              )
            case _ =>
              pagingKey.as(
                sqls"((name >= ${pagingKey.map(pk => pk.recordName)} AND type > ${pagingKey.map(pk => pk.recordType)}) OR name > ${pagingKey.map(pk => pk.recordName)})"
              )
          }

          val typeFilter = recordTypeFilter.map { t =>
            val list = t.map(fromRecordType)
            sqls"type IN ($list)"
          }

          val ownerGroupFilter =
            recordOwnerGroupFilter.map(owner => sqls"owner_group_id = $owner ")

          val opts =
            (zoneAndNameFilters ++ sortBy ++ typeFilter ++ ownerGroupFilter).toList

          val nameSortQualifiers = nameSort match {
            case NameSort.ASC => sqls"ORDER BY fqdn ASC, type ASC "
            case NameSort.DESC => sqls"ORDER BY fqdn DESC, type ASC "
          }

          val recordTypeSortQualifiers = recordTypeSort match {
            case RecordTypeSort.ASC => sqls"ORDER BY type ASC"
            case RecordTypeSort.DESC => sqls"ORDER BY type DESC"
            case RecordTypeSort.NONE => nameSortQualifiers

          }

          val recordLimit = maxPlusOne match {
            case Some(limit) => sqls"LIMIT $limit"
            case None => sqls""
          }

          val finalQualifiers = recordTypeSortQualifiers.append(recordLimit)

          // construct query
          val initialQuery = sqls"SELECT data, fqdn FROM recordset "

          val appendOpts = if (opts.nonEmpty){
            val setDelimiter = SQLSyntax.join(opts, sqls"AND")
            val addWhere = sqls"WHERE"
            addWhere.append(setDelimiter)
          } else sqls""

          val appendQueries = initialQuery.append(appendOpts)

          val finalQuery = appendQueries.append(finalQualifiers)


          val results = sql"$finalQuery"
            .map(toRecordSet)
            .list()
            .apply()

          val newResults = if (maxPlusOne.contains(results.size)) {
            results.dropRight(1)
          } else {
            results
          }

          // if size of results is less than the maxItems plus one, we don't have a next id
          // if maxItems is None, we don't have a next id

          val nextId = maxPlusOne
            .filter(_ == results.size)
            .flatMap(_ => newResults.lastOption.map(PagingKey.toNextId(_, searchByZone)))

          ListRecordSetResults(
            recordSets = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            recordNameFilter = recordNameFilter,
            recordTypeFilter = recordTypeFilter,
            nameSort = nameSort,
            recordTypeSort = recordTypeSort
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
  def getRecordSet(recordSetId: String): IO[Option[RecordSet]] =
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

  def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetsByFQDNs") {
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

  def getFirstOwnedRecordByGroup(ownerGroupId: String): IO[Option[String]] =
    monitor("repo.RecordSet.getFirstOwnedRecordByGroup") {
      IO {
        DB.readOnly { implicit s =>
          GET_RECORDSET_BY_OWNERID
            .bindByName('ownerGroupId -> ownerGroupId)
            .map(_.string(1))
            .single
            .apply()
        }
      }
    }

  def deleteRecordSetsInZone(db: DB, zoneId: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetsInZone") {
      IO {
        val numDeleted = db.withinTx { implicit session =>
        DELETE_RECORDSETS_IN_ZONE
            .bind(zoneId)
            .update()
            .apply()
        }
        logger.debug(s"Deleted $numDeleted records from zone $zoneName (zone id: $zoneId)")
      }.handleErrorWith { error =>
        val errorMessage = new StringWriter
        error.printStackTrace(new PrintWriter(errorMessage))
        logger.error(s"Failed deleting records from zone $zoneName (zone id: $zoneId). Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
        IO.raiseError(error)
      }
    }
}

object MySqlRecordSetRepository extends ProtobufConversions {
  val unknownRecordType: Int = 100

  def toRecordSet(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1))).copy(fqdn = rs.stringOpt(2))

  def fromRecordType(typ: RecordType): Int =
    typ match {
      case RecordType.A => 1
      case RecordType.AAAA => 2
      case RecordType.CNAME => 3
      case RecordType.MX => 4
      case RecordType.NS => 5
      case RecordType.PTR => 6
      case RecordType.SPF => 7
      case RecordType.SRV => 8
      case RecordType.SSHFP => 9
      case RecordType.TXT => 10
      case RecordType.SOA => 11
      case RecordType.DS => 12
      case RecordType.NAPTR => 13
      case RecordType.UNKNOWN => unknownRecordType
    }

  def toFQDN(zoneName: String, recordSetName: String): String = {
    // note: records with name @ are already converted to the zone name before they are added to any db
    val absoluteRecordSetName =
      if (recordSetName.endsWith(".")) recordSetName
      else recordSetName + "."

    val absoluteZoneName =
      if (zoneName.endsWith(".")) zoneName
      else zoneName + "."

    if (absoluteRecordSetName.equals(absoluteZoneName)) absoluteZoneName
    else absoluteRecordSetName + absoluteZoneName
  }

  case class PagingKey(recordName: String, recordType: Int)

  object PagingKey {
    val delimiterRegex = "<\\.\\.\\.\\.>"
    val delimiter = "<....>"

    def apply(startFrom: Option[String]): Option[PagingKey] =
      for {
        sf <- startFrom
        tokens = sf.split(delimiterRegex)
        recordName <- tokens.headOption
        recordType <- Try(tokens(1).toInt).toOption
      } yield PagingKey(recordName, recordType)

    def toNextId(rs: RecordSet, searchByZone: Boolean): String = {
      val nextIdName = if (searchByZone) rs.name else rs.fqdn.getOrElse("")
      val nextIdType = MySqlRecordSetRepository.fromRecordType(rs.typ)

      s"$nextIdName$delimiter$nextIdType"
    }
  }
}