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
import inet.ipaddr.IPAddressString
import vinyldns.core.domain.record.NameSort.{ASC, NameSort}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.mysql.repository.MySqlRecordSetRepository.{PagingKey, fromRecordType, toFQDN}
import vinyldns.proto.VinylDNSProto
import java.io.{PrintWriter, StringWriter}
import scala.util.{Failure, Success, Try}

class MySqlRecordSetCacheRepository
  extends RecordSetCacheRepository
    with Monitored
    with ProtobufConversions {

  private val INSERT_RECORDSETDATA =
    sql"INSERT INTO recordset_data(recordset_id, zone_id, fqdn, reverse_fqdn, type, record_data, ip) VALUES ({recordset_id}, {zone_id}, {fqdn}, {reverse_fqdn}, {type}, {record_data}, INET6_ATON({ip}))"

  private val DELETE_RECORDSETDATA =
    sql"DELETE FROM recordset_data WHERE recordset_id = ?"

  private val DELETE_RECORDSETDATA_IN_ZONE =
    sql"DELETE FROM recordset_data WHERE zone_id = ?"

  private val COUNT_RECORDSETDATA_IN_ZONE =
    sql"""
         |SELECT count(*)
         |  FROM recordset_data
         | WHERE zone_id = {zone_id}
    """.stripMargin

  private val FIND_BY_ID =
    sql"""
         |SELECT  recordset.data, recordset_data.fqdn
         |  FROM recordset_data
         |    INNER JOIN recordset ON recordset.id=recordset_data.recordset_id
         | WHERE recordset_data.recordset_id = {recordset_id}
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
          updateRecordDataList(
            db,
            oldRs.id,
            oldRs.records,
            oldRs.typ,
            oldRs.zoneId,
            toFQDN(change.zone.name, oldRs.name)
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
        insertRecordDataList(
          db,
          i.recordSet.id,
          i.recordSet.records,
          i.recordSet.typ,
          i.recordSet.zoneId,
          toFQDN(i.zone.name, i.recordSet.name)
        ))
    }
    completeUpdates.map { u =>
      Seq[Any](
        updateRecordDataList(
          db,
          u.recordSet.id,
          u.recordSet.records,
          u.recordSet.typ,
          u.recordSet.zoneId,
          toFQDN(u.zone.name, u.recordSet.name),
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

  def deleteRecordSetDataInZone(db: DB, zone_id: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetDataInZone") {
      IO {
        val numDeleted = db.withinTx { implicit session =>
          DELETE_RECORDSETDATA_IN_ZONE
            .bind(zone_id)
            .update()
            .apply()
        }
        logger.info(s"Deleted $numDeleted records from zone $zoneName (zone id: $zone_id)")
      }.handleErrorWith { error =>
        val errorMessage = new StringWriter
        error.printStackTrace(new PrintWriter(errorMessage))
        logger.error(s"Failed deleting records from zone $zoneName (zone id: $zone_id). Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
        IO.raiseError(error)
      }
    }

  def insertRecordDataList(db: DB,
                           recordID: String,
                           recordData: List[RecordData],
                           recordType: RecordType,
                           zoneId: String,
                           fqdn: String): Unit = storeRecordDataList(db, recordID, recordData, recordType, zoneId, fqdn)

  def updateRecordDataList(db: DB,
                           recordID: String,
                           recordData: List[RecordData],
                           recordType: RecordType,
                           zoneId: String,
                           fqdn: String): Unit = {
    db.withinTx { implicit session =>
      DELETE_RECORDSETDATA
        .bind(recordID)
        .update()
        .apply()
      storeRecordDataList(db, recordID, recordData, recordType, zoneId, fqdn)
    }
  }

  private def storeRecordDataList(db: DB,
                                  recordId: String,
                                  recordData: List[RecordData],
                                  recordType: RecordType,
                                  zoneId: String,
                                  fqdn: String): Unit = {
    recordData.foreach(record => saveRecordSetData(db, recordId, zoneId, fqdn, recordType, record))
  }

  /**
   * Inserts data into the RecordSet Data table
   *
   * @param db         The database connection
   * @param recordId   The record identifier
   * @param zoneId     The zone identifier
   * @param fqdn       The fully qualified domain name
   * @param recordType The record type
   * @param recordData The record data
   */
  private def saveRecordSetData(db: DB,
                                recordId: String,
                                zoneId: String,
                                fqdn: String,
                                recordType: RecordType,
                                recordData: RecordData,
                               ): Unit = {
    // We want to get the protobuf string format of the record data. This provides
    // slightly more information when doing RData searches.
    // Example:
    //  An SOA record may contain the following
    //    mname:"auth.vinyldns.io."  rname:"admin.vinyldns.io."  serial:14  refresh:7200  retry:3600  expire:1209600  minimum:900
    // This allows us to potentially search for SOA records with "refresh:7200"
    val recordDataString = raw"""([a-z]+): ("|\d)""".r.replaceAllIn(recordDataToPB(recordData).toString.trim, "$1:$2")

    // Extract the IP address from the forward or reverse record
    val parsedIp = recordType match {
      case RecordType.PTR => parseIP(fqdn)
      case RecordType.A | RecordType.AAAA => parseIP(recordDataString)
      case _ => None
    }

    db.withinTx { implicit session =>
      INSERT_RECORDSETDATA
        .bindByName(
          'recordset_id -> recordId,
          'zone_id -> zoneId,
          'fqdn -> fqdn,
          'reverse_fqdn -> fqdn.reverse,
          'type -> recordType.toString,
          'record_data -> recordDataString,
          'ip -> parsedIp.orNull
        )
        .update()
        .apply()
    }}

  /**
    * Retrieves recordset data for records with the given {@code recordId} in the recordset
    * In MySql we do not need the zone id, since can hit the key directly
    *
    * @param recordSetId The identifier for the recordset
    * @return A list of {@link RecordSet} matching the criteria
    */
  def getRecordSetData(recordSetId: String): IO[Option[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetData") {
      IO {
        DB.readOnly { implicit s =>
          FIND_BY_ID.bindByName('recordset_id -> recordSetId).map(toRecordSetData).single().apply()
        }
      }
    }

  /**
    * Retrieves no. of recordset data counts for records with the given {@code zoneId} in the recordset
    *
    * @param zoneId The identifier for the zone
    * @return A no. of recordset data counts of {@link RecordSet} matching the criteria
    */
  def getRecordSetDataCount(zoneId: String): IO[Int] =
    monitor("repo.RecordSet.getRecordSetDataCount") {
      IO {
        DB.readOnly { implicit s =>
          // this is a count query, so should always return a value.  However, scalikejdbc doesn't have this,
          // so we have to default to 0.  it is literally impossible to not return a value
          COUNT_RECORDSETDATA_IN_ZONE
            .bindByName('zone_id -> zoneId)
            .map(_.int(1))
            .single
            .apply()
            .getOrElse(0)
        }
      }
    }

  /**
    * Retrieves recordset data for records with the given {@code recordNameFilter} in the zone given by {@code zoneId} of the
    * record type given by {@code recordTypeFilter} given by {@code recordOwnerGroupFilter}
    *
    * @param zoneId The identifier for the zone
    * @param startFrom The Limits for Start from records
    * @param maxItems The Limits for maximum records(INT)
    * @param recordNameFilter The name of the record
    * @param recordTypeFilter The {@link RecordType} to include in the results
    * @param recordOwnerGroupFilter The OwnerGroup of the record
    * @param nameSort The Sort of record Name
    * @return A list of {@link RecordSet} matching the criteria
    */
  def listRecordSetData(
                         zoneId: Option[String],
                         startFrom: Option[String],
                         maxItems: Option[Int],
                         recordNameFilter: Option[String],
                         recordTypeFilter: Option[Set[RecordType]],
                         recordOwnerGroupFilter: Option[String],
                         nameSort: NameSort
                       ): IO[ListRecordSetResults] =
    monitor("repo.RecordSet.listRecordSetData") {
      IO {
        val maxPlusOne = maxItems.map(_ + 1)
        val wildcardStart = raw"^\s*[*%](.+[^*%])\s*$$".r

        // setup optional filters
        val zoneAndNameFilters = (zoneId, recordNameFilter) match {
          case (Some(zId), Some(rName)) =>
            Some(sqls"recordset.zone_id = $zId AND recordset.name LIKE ${rName.replace('*', '%')} ")
          case (None, Some(fqdn)) => fqdn match {
            case fqdn if wildcardStart.pattern.matcher(fqdn).matches() =>
              // If we have a wildcard at the beginning only, then use the reverse_fqdn DB index
              Some(sqls"recordset_data.reverse_fqdn LIKE ${wildcardStart.replaceAllIn(fqdn, "$1").reverse.replace('*', '%') + "%"} ")
            case _ =>
              // By default, just use a LIKE query
              Some(sqls"recordset.fqdn LIKE ${fqdn.replace('*', '%')} ")
          }
          case (Some(zId), None) => Some(sqls"recordset.zone_id = $zId ")
          case _ => None
        }

        val searchByZone = zoneId.fold[Boolean](false)(_ => true)
        val pagingKey = PagingKey(startFrom)

        // sort by name or fqdn in given order
        val sortBy = (searchByZone, nameSort) match {
          case (true, NameSort.DESC) =>
            pagingKey.as(
              sqls"((recordset.name <= ${pagingKey.map(pk => pk.recordName)} AND recordset.type > ${pagingKey.map(pk => pk.recordType)}) OR recordset.name < ${pagingKey.map(pk => pk.recordName)})"
            )
          case (false, NameSort.ASC) =>
            pagingKey.as(
              sqls"((recordset.fqdn >= ${pagingKey.map(pk => pk.recordName)} AND recordset.type > ${pagingKey.map(pk => pk.recordType)}) OR recordset.fqdn > ${pagingKey.map(pk => pk.recordName)})"
            )
          case (false, NameSort.DESC) =>
            pagingKey.as(
              sqls"((recordset.fqdn <= ${pagingKey.map(pk => pk.recordName)} AND recordset.type > ${pagingKey.map(pk => pk.recordType)}) OR recordset.fqdn < ${pagingKey.map(pk => pk.recordName)})"
            )
          case _ =>
            pagingKey.as(
              sqls"((recordset.name >= ${pagingKey.map(pk => pk.recordName)} AND recordset.type > ${pagingKey.map(pk => pk.recordType)}) OR recordset.name > ${pagingKey.map(pk => pk.recordName)})"
            )
        }

        val typeFilter = recordTypeFilter.map { t =>
          val list = t.map(fromRecordType)
          sqls"recordset.type IN ($list)"
        }

        val ownerGroupFilter =
          recordOwnerGroupFilter.map(owner => sqls"recordset.owner_group_id = $owner ")

        val opts =
          (zoneAndNameFilters ++ sortBy ++ typeFilter ++ ownerGroupFilter).toList

        val qualifiers = if (nameSort == ASC) {
          sqls"ORDER BY recordset.fqdn ASC, recordset.type ASC "
        }
        else {
          sqls"ORDER BY recordset.fqdn DESC, recordset.type ASC "
        }

        val recordLimit = maxPlusOne match {
          case Some(limit) => sqls"LIMIT $limit"
          case None => sqls""
        }

        val finalQualifiers = qualifiers.append(recordLimit)

        // Construct query. We include the MySQL MAX_EXECUTION_TIME directive here to limit the maximum amount of time
        // this query can execute. The API should timeout before we reach 20s - this is just to avoid user-generated
        // long-running queries leading to performance degradation
        val initialQuery = sqls"SELECT /*+ MAX_EXECUTION_TIME(20000) */ recordset.data, recordset.fqdn FROM recordset_data "

        // Join query for data column from recordset table
        val recordsetDataJoin = sqls"RIGHT JOIN recordset ON recordset.id=recordset_data.recordset_id "
        val recordsetDataJoinQuery = initialQuery.append(recordsetDataJoin)

        val appendOpts = if (opts.nonEmpty) {
          val setDelimiter = SQLSyntax.join(opts, sqls"AND")
          val addWhere = sqls"WHERE"
          addWhere.append(setDelimiter)
        } else sqls""

        val appendQueries = recordsetDataJoinQuery.append(appendOpts)

        val finalQuery = appendQueries.append(finalQualifiers)
        DB.readOnly { implicit s =>
          val results = sql"$finalQuery"
            .map(toRecordSetData)
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
            recordTypeSort = RecordTypeSort.NONE)
        }
      }
    }


  private val IPV4_ARPA = ".in-addr.arpa."
  private val IPV6_ARPA = ".ip6.arpa."
  private val innerRecordRegex = "(?i).*?\"((?:[0-9a-f]+[:.]+)+[0-9a-f]+)\".*".r

  /**
   * parseIP address from given record data.
   *
   * @param ipAsString The IP address to parse
   * @return The parsed IP address
   */
  def parseIP(ipAsString: String): Option[String] = {

    def reverse4(ipv4: String) = ipv4.split('.').reverse.mkString(".")

    def reverse6(ipv6: String) = ipv6.split('.').reverse.grouped(4).toArray.map(ip => ip.mkString("")).mkString(":")

    val extractedIp = ipAsString match {
      case addr if addr.endsWith(IPV4_ARPA) => reverse4(addr.replace(IPV4_ARPA, ""))
      case addr if addr.endsWith(IPV6_ARPA) => reverse6(addr.replace(IPV6_ARPA, ""))
      case addr => innerRecordRegex.replaceAllIn(addr, "$1")
    }

    // Create a canonical address
    Try {
      new IPAddressString(extractedIp).toAddress
    } match {
      case Success(v) => Some(v.toCanonicalString)
      case Failure(e) =>
        logger.warn(s"error parsing IP address $extractedIp", e)
        None
    }
  }

  def toRecordSetData(rs: WrappedResultSet): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(rs.bytes(1))).copy(fqdn = rs.stringOpt(2))
}
