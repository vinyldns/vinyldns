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

import java.sql.Timestamp
import cats.data._
import cats.effect._

import java.time.Instant
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.batch.BatchChangeApprovalStatus.BatchChangeApprovalStatus
import vinyldns.core.domain.batch._
import vinyldns.core.protobuf.{BatchChangeProtobufConversions, SingleChangeType}
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

/**
  * MySqlBatchChangeRepository implements the JDBC queries that support the APIs defined in BatchChangeRepository.scala
  * BatchChange and SingleChange are stored in RDS as two tables.
  * The batch_change_id foreign key on single_change table helps to ensure single change records
  * only exist as part of a batch change.
  * The entire single change data is encoded in protobuf format and stored as the BLOB
  * qin data column of single change table.
  * There're indexes on the tables to provide easy query by single_change_id, batch_change_id, user_id
  */
class MySqlBatchChangeRepository
    extends BatchChangeRepository
    with BatchChangeProtobufConversions
    with Monitored {

  private final val logger = LoggerFactory.getLogger(classOf[MySqlBatchChangeRepository])

  private final val PUT_BATCH_CHANGE =
    sql"""
       |            INSERT INTO batch_change(id, user_id, user_name, created_time, comments, owner_group_id,
       |                                     approval_status, reviewer_id, review_comment, review_timestamp,
       |                                     scheduled_time, cancelled_timestamp)
       |                 VALUES ({id}, {userId}, {userName}, {createdTime}, {comments}, {ownerGroupId}, {approvalStatus},
       |                 {reviewerId}, {reviewComment}, {reviewTimestamp}, {scheduledTime}, {cancelledTimestamp})
       |ON DUPLICATE KEY UPDATE comments={comments}, owner_group_id={ownerGroupId}, approval_status={approvalStatus},
       |                        reviewer_id={reviewerId}, review_comment={reviewComment}, review_timestamp={reviewTimestamp},
       |                        scheduled_time={scheduledTime}, cancelled_timestamp={cancelledTimestamp}
       """.stripMargin

  private final val PUT_SINGLE_CHANGE =
    sql"""
       |            INSERT INTO single_change(id, seq_num, input_name, change_type, data, status, batch_change_id,
       |                        record_set_change_id, record_set_id, zone_id)
       |                 VALUES ({id}, {seqNum}, {inputName}, {changeType}, {data}, {status}, {batchChangeId},
       |                        {recordSetChangeId}, {recordSetId}, {zoneId})
       |ON DUPLICATE KEY UPDATE input_name={inputName}, change_type={changeType}, data={data}, status={status},
       |                        record_set_change_id={recordSetChangeId}, record_set_id={recordSetId}, zone_id={zoneId}
       """.stripMargin

  private final val GET_BATCH_CHANGE_METADATA =
    sql"""
         |SELECT user_id, user_name, created_time, comments, owner_group_id,
         |       approval_status, reviewer_id, review_comment, review_timestamp, scheduled_time, cancelled_timestamp
         |  FROM batch_change bc
         | WHERE bc.id = ?
        """.stripMargin

  private final val GET_BATCH_CHANGE_METADATA_FROM_SINGLE_CHANGE =
    sql"""
         |SELECT bc.id, bc.user_id, bc.user_name, bc.created_time, bc.comments, bc.owner_group_id, bc.approval_status,
         |       bc.reviewer_id, bc.review_comment, bc.review_timestamp, bc.scheduled_time, bc.cancelled_timestamp
         |  FROM batch_change bc
         |  JOIN (SELECT id, batch_change_id from single_change where id = ?) sc
         |    ON sc.batch_change_id = bc.id
        """.stripMargin

  private final val GET_BATCH_CHANGE_SUMMARY_BASE =
    """
      |SELECT batch_change_page.id, user_id, user_name, created_time, comments, owner_group_id, approval_status, reviewer_id,
      |       review_comment, review_timestamp, scheduled_time, cancelled_timestamp,
      |       SUM(CASE WHEN sc.status LIKE 'Failed' OR sc.status LIKE 'Rejected' THEN 1 ELSE 0 END) AS fail_count,
      |       SUM(CASE WHEN sc.status LIKE 'Pending' OR sc.status LIKE 'NeedsReview' THEN 1 ELSE 0 END) AS pending_count,
      |       SUM(CASE sc.status WHEN 'Complete' THEN 1 ELSE 0 END) AS complete_count,
      |       SUM(CASE sc.status WHEN 'Cancelled' THEN 1 ELSE 0 END) AS cancelled_count
      |              FROM (SELECT bc.id, bc.user_id, bc.user_name, bc.created_time, bc.comments, bc.owner_group_id, bc.approval_status,
      |                           bc.reviewer_id, bc.review_comment, bc.review_timestamp, bc.scheduled_time, bc.cancelled_timestamp
      |                    FROM batch_change bc
        """.stripMargin

  private final val GET_BATCH_CHANGE_SUMMARY_END =
    """
      |                    ORDER BY created_time DESC
      |                    LIMIT {maxItems} OFFSET {startFrom}) batch_change_page
      |                    JOIN single_change sc ON batch_change_page.id = sc.batch_change_id
      |GROUP BY batch_change_page.id
      |ORDER BY created_time DESC
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
      IO {
        DB.localTx { implicit s =>
          saveBatchChange(batch)
        }
      }
    }

  /* get a batchchange with its singlechanges from the batchchange and singlechange tables */
  def getBatchChange(batchChangeId: String): IO[Option[BatchChange]] =
    monitor("repo.BatchChangeJDBC.getBatchChange") {
      val batchChangeFuture = for {
        batchChangeMeta <- OptionT[IO, BatchChange](getBatchChangeMetadata(batchChangeId))
        singleChanges <- OptionT.liftF[IO, List[SingleChange]](
          getSingleChangesByBatchChangeId(batchChangeId)
        )
      } yield {
        batchChangeMeta.copy(changes = singleChanges)
      }
      batchChangeFuture.value
    }

  def updateSingleChanges(singleChanges: List[SingleChange]): IO[Option[BatchChange]] = {
    def convertSingleChangeToParams(singleChange: SingleChange): Seq[(Symbol, AnyRef)] =
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

    def getBatchFromSingleChangeId(
                                    singleChangeId: String
                                  )(implicit s: DBSession): Option[BatchChange] =
      GET_BATCH_CHANGE_METADATA_FROM_SINGLE_CHANGE
        .bind(singleChangeId)
        .map(extractBatchChange(None))
        .first
        .apply()
        .map { batchMeta =>
          val changes = GET_SINGLE_CHANGES_BY_BCID
            .bind(batchMeta.id)
            .map(extractSingleChange(1))
            .list()
            .apply()
          batchMeta.copy(changes = changes)
        }
    monitor("repo.BatchChangeJDBC.updateSingleChanges") {
      IO {
        logger.info(s"Updating single change status: ${singleChanges.map(ch => (ch.id, ch.status))}")
        DB.localTx { implicit s =>
          for {
            headChange <- singleChanges.headOption
            batchParams = singleChanges.map(convertSingleChangeToParams)
            _ = UPDATE_SINGLE_CHANGE.batchByName(batchParams: _*).apply()
            batchChange <- getBatchFromSingleChangeId(headChange.id)
          } yield batchChange
        }}
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
            // log 1st 5; we shouldn't need all, and if there's a ton it could get long
            logger.error(
              s"!!! Could not find all SingleChangeIds in getSingleChanges call; missing IDs: ${notFound
                .take(5)} !!!"
            )
          }
          inDbChanges
        }
      }
    }

  def getBatchChangeSummaries(
      userId: Option[String],
      userName: Option[String] = None,
      dateTimeStartRange: Option[String] = None,
      dateTimeEndRange: Option[String] = None,
      startFrom: Option[Int] = None,
      maxItems: Int = 100,
      approvalStatus: Option[BatchChangeApprovalStatus]
  ): IO[BatchChangeSummaryList] =
    monitor("repo.BatchChangeJDBC.getBatchChangeSummaries") {
      IO {
        DB.readOnly { implicit s =>
          val startValue = startFrom.getOrElse(0)
          val sb = new StringBuilder
          sb.append(GET_BATCH_CHANGE_SUMMARY_BASE)

          val uid = userId.map(u => s"bc.user_id = '$u'")
          val as = approvalStatus.map(a => s"bc.approval_status = '${fromApprovalStatus(a)}'")
          val uname = userName.map(uname => s"bc.user_name = '$uname'")
          val dtRange = if(dateTimeStartRange.isDefined && dateTimeEndRange.isDefined) {
            Some(s"(bc.created_time >= '${dateTimeStartRange.get}' AND bc.created_time <= '${dateTimeEndRange.get}')")
          } else {
            None
          }
          val opts = uid ++ as ++ uname ++ dtRange

          if (opts.nonEmpty) sb.append("WHERE ").append(opts.mkString(" AND "))

          sb.append(GET_BATCH_CHANGE_SUMMARY_END)
          val query = sb.toString()

          val queryResult =
            SQL(query)
              .bindByName('startFrom -> startValue, 'maxItems -> (maxItems + 1))
              .map { res =>
                val pending = res.int("pending_count")
                val failed = res.int("fail_count")
                val complete = res.int("complete_count")
                val cancelled = res.int("cancelled_count")
                val approvalStatus = toApprovalStatus(res.intOpt("approval_status"))
                val schedTime =
                  res.timestampOpt("scheduled_time").map(st => st.toInstant)
                val cancelledTimestamp =
                  res.timestampOpt("cancelled_timestamp").map(st => st.toInstant)
                BatchChangeSummary(
                  res.string("user_id"),
                  res.string("user_name"),
                  Option(res.string("comments")),
                  res.timestamp("created_time").toInstant,
                  pending + failed + complete + cancelled,
                  BatchChangeStatus
                    .calculateBatchStatus(
                      approvalStatus,
                      pending > 0,
                      failed > 0,
                      complete > 0,
                      schedTime.isDefined
                    ),
                  Option(res.string("owner_group_id")),
                  res.string("id"),
                  None,
                  approvalStatus,
                  res.stringOpt("reviewer_id"),
                  None,
                  res.stringOpt("review_comment"),
                  res.timestampOpt("review_timestamp").map(st => st.toInstant),
                  schedTime,
                  cancelledTimestamp
                )
              }
              .list()
              .apply()
          val maxQueries = queryResult.take(maxItems)
          val nextId = if (queryResult.size <= maxItems) None else Some(startValue + maxItems)
          val ignoreAccess = userId.isEmpty
          BatchChangeSummaryList(
            maxQueries,
            startFrom,
            nextId,
            maxItems,
            ignoreAccess,
            approvalStatus
          )
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
            .map(extractBatchChange(Some(batchChangeId)))
            .first
            .apply()
        }
      }
    }

  private def extractBatchChange(batchChangeId: Option[String]): WrappedResultSet => BatchChange = {
    result =>
      BatchChange(
        result.string("user_id"),
        result.string("user_name"),
        result.stringOpt("comments"),
        toDateTime(result.timestamp("created_time")),
        Nil,
        result.stringOpt("owner_group_id"),
        toApprovalStatus(result.intOpt("approval_status")),
        result.stringOpt("reviewer_id"),
        result.stringOpt("review_comment"),
        result.timestampOpt("review_timestamp").map(toDateTime),
        batchChangeId.getOrElse(result.string("id")),
        result.timestampOpt("scheduled_time").map(toDateTime),
        result.timestampOpt("cancelled_timestamp").map(toDateTime)
      )
  }

  private def saveBatchChange(
      batchChange: BatchChange
  )(implicit session: DBSession): BatchChange = {
    PUT_BATCH_CHANGE
      .bindByName(
        Seq(
          'id -> batchChange.id,
          'userId -> batchChange.userId,
          'userName -> batchChange.userName,
          'createdTime -> batchChange.createdTimestamp,
          'comments -> batchChange.comments,
          'ownerGroupId -> batchChange.ownerGroupId,
          'approvalStatus -> fromApprovalStatus(batchChange.approvalStatus),
          'reviewerId -> batchChange.reviewerId,
          'reviewComment -> batchChange.reviewComment,
          'reviewTimestamp -> batchChange.reviewTimestamp,
          'scheduledTime -> batchChange.scheduledTime,
          'cancelledTimestamp -> batchChange.cancelledTimestamp
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

  def fromApprovalStatus(typ: BatchChangeApprovalStatus): Int =
    typ match {
      case BatchChangeApprovalStatus.AutoApproved => 1
      case BatchChangeApprovalStatus.PendingReview => 2
      case BatchChangeApprovalStatus.ManuallyApproved => 3
      case BatchChangeApprovalStatus.ManuallyRejected => 4
      case BatchChangeApprovalStatus.Cancelled => 5
    }

  def toApprovalStatus(key: Option[Int]): BatchChangeApprovalStatus =
    key match {
      case Some(1) => BatchChangeApprovalStatus.AutoApproved
      case Some(2) => BatchChangeApprovalStatus.PendingReview
      case Some(3) => BatchChangeApprovalStatus.ManuallyApproved
      case Some(4) => BatchChangeApprovalStatus.ManuallyRejected
      case Some(5) => BatchChangeApprovalStatus.Cancelled
      case _ => BatchChangeApprovalStatus.AutoApproved
    }

  def toDateTime(ts: Timestamp): Instant = ts.toInstant
}
