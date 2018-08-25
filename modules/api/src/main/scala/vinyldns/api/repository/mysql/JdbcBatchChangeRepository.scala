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

package vinyldns.api.repository.mysql

import cats.data._
import cats.effect._
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.api.domain.batch._
import vinyldns.api.protobuf.{BatchChangeProtobufConversions, SingleChangeType}
import vinyldns.api.route.Monitored
import vinyldns.proto.VinylDNSProto

/**
  * JdbcBatchChangeRepository implements the JDBC queries that support the APIs defined in BatchChangeRepository.scala
  * BatchChange and SingleChange are stored in RDS as two tables.
  * The batch_change_id foreign key on single_change table helps to ensure single change records
  * only exist as part of a batch change.
  * The entire single change data is encoded in protobuf format and stored as the BLOB
  * qin data column of single change table.
  * There're indexes on the tables to provide easy query by single_change_id, batch_change_id, user_id
  */
class JdbcBatchChangeRepository
    extends BatchChangeRepository
    with BatchChangeProtobufConversions
    with Monitored {

  private final val logger = LoggerFactory.getLogger(classOf[JdbcBatchChangeRepository])

  private final val PUT_BATCH_CHANGE =
    sql"""
         |INSERT INTO batch_change(id, user_id, user_name, created_time, comments)
         |     VALUES ({id}, {userId}, {userName}, {createdTime}, {comments})
        """.stripMargin

  private final val PUT_SINGLE_CHANGE =
    sql"""
         |INSERT INTO single_change(id, seq_num, input_name, change_type, data, status, batch_change_id,
         |                                record_set_change_id, record_set_id, zone_id)
         |     VALUES ({id}, {seqNum}, {inputName}, {changeType}, {data}, {status}, {batchChangeId},
         |                                {recordSetChangeId}, {recordSetId}, {zoneId})
        """.stripMargin

  private final val GET_BATCH_CHANGE_METADATA =
    sql"""
         |SELECT user_id, user_name, created_time, comments
         |  FROM batch_change bc
         | WHERE bc.id = ?
        """.stripMargin

  private final val GET_BATCH_CHANGE_SUMMARY =
    sql"""
         |       SELECT bc.id, bc.user_id, bc.user_name, bc.created_time, bc.comments,
         |              SUM( case sc.status when 'Failed' then 1 else 0 end ) AS fail_count,
         |              SUM( case sc.status when 'Pending' then 1 else 0 end ) AS pending_count,
         |              SUM( case sc.status when 'Complete' then 1 else 0 end )  AS complete_count
         |         FROM single_change sc
         |         JOIN batch_change bc
         |           ON sc.batch_change_id = bc.id
         |        WHERE bc.user_id={userId}
         |        GROUP BY bc.id
         |        ORDER BY bc.created_time DESC
         |        LIMIT {maxItems}
         |        OFFSET {startFrom}
        """.stripMargin

  private final val GET_SINGLE_CHANGES_BY_BCID =
    sql"""
         |SELECT sc.data
         |  FROM single_change sc
         | WHERE sc.batch_change_id = ?
         | ORDER BY sc.seq_num ASC
        """.stripMargin

  private final val UPDATE_SINGLE_CHANGE =
    sql"""
         |UPDATE single_change
         |   SET input_name={inputName}, change_type={changeType},
         |       data={data}, status={status},
         |       record_set_change_id={recordSetChangeId},
         |       record_set_id={recordSetId}, zone_id={zoneId}
         | WHERE id={id}
        """.stripMargin

  def save(batch: BatchChange): IO[BatchChange] =
    monitor("repo.BatchChangeJDBC.save") {
      DB.localTx { implicit s =>
        saveBatchChange(batch)
      }
    }

  /* get a batchchange with its singlechanges from the batchchange and singlechange tables */
  def getBatchChange(batchChangeId: String): IO[Option[BatchChange]] =
    monitor("repo.BatchChangeJDBC.getBatchChange") {
      val batchChangeFuture = for {
        batchChangeMeta <- OptionT[IO, BatchChange](getBatchChangeMetadata(batchChangeId))
        singleChanges <- OptionT.liftF[IO, List[SingleChange]](
          getSingleChangesByBatchChangeId(batchChangeId))
      } yield {
        batchChangeMeta.copy(changes = singleChanges)
      }
      batchChangeFuture.value
    }

  def updateSingleChanges(singleChanges: List[SingleChange]): IO[List[SingleChange]] =
    monitor("repo.BatchChangeJDBC.updateSingleChanges") {
      logger.info(
        s"Updating single change statuses: ${singleChanges.map(ch => (ch.id, ch.status))}")
      IO {
        DB.localTx { implicit s =>
          val batchParams = singleChanges.map { singleChange =>
            toPB(singleChange) match {
              case Right(data) =>
                val changeType = SingleChangeType.from(singleChange)
                Seq(
                  'inputName -> singleChange.inputName,
                  'changeType -> changeType.toString,
                  'data -> data.toByteArray,
                  'status -> singleChange.status.toString,
                  'recordSetChangeId -> singleChange.recordChangeId,
                  'recordSetId -> singleChange.recordSetId,
                  'zoneId -> singleChange.zoneId,
                  'id -> singleChange.id
                )
              case Left(e) => throw e
            }
          }
          UPDATE_SINGLE_CHANGE.batchByName(batchParams: _*).apply()
          singleChanges
        }
      }
    }

  def getSingleChanges(singleChangeIds: List[String]): IO[List[SingleChange]] =
    if (singleChangeIds.isEmpty) {
      IO.pure(List())
    } else {
      monitor("repo.BatchChangeJDBC.getSingleChanges") {
        val dbCall = IO {
          DB.readOnly { implicit s =>
            sql"""
                 |SELECT sc.data
                 |  FROM single_change sc
                 | WHERE sc.id IN ($singleChangeIds)
                 | ORDER BY sc.seq_num ASC
           """.stripMargin
              .map(extractSingleChange(1))
              .list()
              .apply()
          }
        }
        dbCall.map { inDbChanges =>
          val notFound = singleChangeIds.toSet -- inDbChanges.map(_.id).toSet
          if (notFound.nonEmpty) {
            // log 1st 5; we shouldnt need all, and if theres a ton it could get long
            logger.error(
              s"!!! Could not find all SingleChangeIds in getSingleChanges call; missing IDs: ${notFound
                .take(5)} !!!")
          }
          inDbChanges
        }
      }
    }

  def getBatchChangeSummariesByUserId(
      userId: String,
      startFrom: Option[Int] = None,
      maxItems: Int = 100): IO[BatchChangeSummaryList] =
    monitor("repo.BatchChangeJDBC.getBatchChangeSummariesByUserId") {
      IO {
        DB.readOnly { implicit s =>
          val startValue = startFrom.getOrElse(0)
          val queryResult = GET_BATCH_CHANGE_SUMMARY
            .bindByName('userId -> userId, 'startFrom -> startValue, 'maxItems -> (maxItems + 1))
            .map { res =>
              val pending = res.int("pending_count")
              val failed = res.int("fail_count")
              val complete = res.int("complete_count")
              BatchChangeSummary(
                userId,
                res.string("user_name"),
                Option(res.string("comments")),
                new org.joda.time.DateTime(res.timestamp("created_time")),
                pending + failed + complete,
                BatchChangeStatus.fromSingleStatuses(pending > 0, failed > 0, complete > 0),
                res.string("id")
              )
            }
            .list()
            .apply()
          val maxQueries = queryResult.take(maxItems)
          val nextId = if (queryResult.size <= maxItems) None else Some(startValue + maxItems)
          BatchChangeSummaryList(maxQueries, startFrom, nextId, maxItems)
        }
      }
    }

  /* getBatchChangeMetadata loads the batch change metadata from the database. It doesn't load the single changes */
  private def getBatchChangeMetadata(batchChangeId: String): IO[Option[BatchChange]] =
    monitor("repo.BatchChangeJDBC.getBatchChangeMetadata") {
      IO {
        DB.readOnly { implicit s =>
          GET_BATCH_CHANGE_METADATA
            .bind(batchChangeId)
            .map { result =>
              BatchChange(
                result.string("user_id"),
                result.string("user_name"),
                Option(result.string("comments")),
                new org.joda.time.DateTime(result.timestamp("created_time")),
                Nil,
                batchChangeId
              )
            }
            .first
            .apply()
        }
      }
    }

  private def saveBatchChange(batchChange: BatchChange)(
      implicit session: DBSession): IO[BatchChange] =
    IO {
      PUT_BATCH_CHANGE
        .bindByName(
          Seq(
            'id -> batchChange.id,
            'userId -> batchChange.userId,
            'userName -> batchChange.userName,
            'createdTime -> batchChange.createdTimestamp,
            'comments -> batchChange.comments
          ): _*
        )
        .update()
        .apply()

      val singleChangesParams = batchChange.changes.zipWithIndex.map {
        case (singleChange, seqNum) =>
          toPB(singleChange) match {
            case Right(data) =>
              val changeType = SingleChangeType.from(singleChange)
              Seq(
                'id -> singleChange.id,
                'seqNum -> seqNum,
                'inputName -> singleChange.inputName,
                'changeType -> changeType.toString,
                'data -> data.toByteArray,
                'status -> singleChange.status.toString,
                'batchChangeId -> batchChange.id,
                'recordSetChangeId -> singleChange.recordChangeId,
                'recordSetId -> singleChange.recordSetId,
                'zoneId -> singleChange.zoneId
              )
            case Left(e) => throw e
          }
      }

      PUT_SINGLE_CHANGE.batchByName(singleChangesParams: _*).apply()
      batchChange
    }

  private def getSingleChangesByBatchChangeId(bcId: String): IO[List[SingleChange]] =
    monitor("repo.BatchChangeJDBC.getSingleChangesByBatchChangeId") {
      IO {
        DB.readOnly { implicit s =>
          GET_SINGLE_CHANGES_BY_BCID
            .bind(bcId)
            .map(extractSingleChange(1))
            .list()
            .apply()
        }
      }
    }

  private def extractSingleChange(columnIndex: Int): WrappedResultSet => SingleChange = res => {
    val result = fromPB(VinylDNSProto.SingleChange.parseFrom(res.bytes(columnIndex)))
    result match {
      case Right(ok) => ok
      case Left(e) => throw e
    }
  }
}
