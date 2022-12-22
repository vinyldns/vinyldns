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

package vinyldns.mysql.queue

import cats.data._
import cats.effect._
import cats.implicits._
import java.time.Instant
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.health.HealthCheck._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._
import vinyldns.core.route.Monitored
import vinyldns.mysql.queue.MessageType.{
  BatchChangeMessageType,
  RecordChangeMessageType,
  ZoneChangeMessageType
}
import java.io.{PrintWriter, StringWriter}
import vinyldns.proto.VinylDNSProto
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

object MySqlMessageQueue {
  final case class InvalidMessageHandle(msg: String) extends Throwable(msg)
  final case class MessageAttemptsExceeded(msg: String) extends Throwable(msg)
  final case class InvalidMessageTimeout(timeout: Int)
      extends Throwable(s"Invalid message timeout $timeout")
  final val QUEUE_CONNECTION_NAME = 'queue
}

class MySqlMessageQueue(maxRetries: Int)
    extends MessageQueue
    with Monitored
    with ProtobufConversions {
  import MySqlMessageQueue._

  private val logger = LoggerFactory.getLogger(classOf[MySqlMessageQueue])

  private val INSERT_MESSAGE =
    sql"""
      |INSERT INTO message_queue(id, message_type, in_flight, data, created, updated, timeout_seconds, attempts)
      |     VALUES ({id}, {messageType}, {inFlight}, {data}, {created}, NOW(), {timeoutSeconds}, {attempts})
      |ON DUPLICATE KEY UPDATE created={created}
    """.stripMargin

  private val REQUEUE_MESSAGE =
    sql"""
      |UPDATE message_queue
      |   SET in_flight = 0
      | WHERE id = ?
    """.stripMargin

  private val CHANGE_TIMEOUT =
    sql"""
      |UPDATE message_queue
      |   SET timeout_seconds = ?
      | WHERE id = ?
    """.stripMargin

  private val FETCH_UNCLAIMED =
    sql"""
      |SELECT id, message_type, data, attempts, timeout_seconds
      |  FROM message_queue
      | WHERE in_flight = 0
      |    OR updated < DATE_SUB(NOW(),INTERVAL timeout_seconds SECOND)
      | LIMIT ?
    """.stripMargin

  private val DELETE_MESSAGES =
    sql"""
      |DELETE FROM message_queue WHERE id IN (?)
    """.stripMargin

  // Important!  Make sure to update the updated timestamp to current, and increment the attempts!
  private val CLAIM_MESSAGES =
    sql"""
      |UPDATE message_queue
      |   SET in_flight=1, updated=NOW(), attempts=attempts+1
      | WHERE id in (?)
    """.stripMargin

  private final val HEALTH_CHECK =
    sql"""
      |SELECT 1
      |  FROM DUAL
     """.stripMargin

  /* Parses a message from fields, returning the message id on failure, otherwise a good CommandMessage */
  def parseMessage(
      id: MessageId,
      typ: Int,
      data: Array[Byte],
      attempts: Int,
      timeoutSeconds: Int
  ): Either[(Throwable, MessageId), MySqlMessage] = {
    // parse the type, if it cannot parse we fail with the message id, same with the data
    for {
      messageType <- MessageType.fromInt(typ)
      cmd <- Either.catchNonFatal {
        messageType match {
          case ZoneChangeMessageType => fromPB(VinylDNSProto.ZoneChange.parseFrom(data))
          case RecordChangeMessageType => fromPB(VinylDNSProto.RecordSetChange.parseFrom(data))
          case BatchChangeMessageType => BatchChangeCommand(new String(data))
        }
      }
      _ <- Either.cond(
        attempts < maxRetries,
        (),
        MessageAttemptsExceeded(id.value)
      ) // mark as error if too many attempts
      _ <- Either.cond(
        timeoutSeconds > 0,
        (),
        MySqlMessageQueue.InvalidMessageTimeout(timeoutSeconds)
      )
    } yield MySqlMessage(id, attempts, timeoutSeconds.seconds, cmd)
  }.leftMap { e =>
    (e, id)
  }

  def fetchUnclaimed(
      numMessages: Int
  )(implicit s: DBSession): List[Either[(Throwable, MessageId), MySqlMessage]] =
    FETCH_UNCLAIMED
      .bind(numMessages)
      .map { rs =>
        val id = MessageId(rs.string(1))
        val typ = rs.int(2)
        val data = rs.bytes(3)
        val attempts = rs.int(4)
        val timeoutSeconds = rs.int(5)
        parseMessage(id, typ, data, attempts, timeoutSeconds)
      }
      .list()
      .apply()

  def deleteMessages(ids: List[MessageId])(implicit s: DBSession): Int = {
    val messages = ids.map(_.value).mkString(",")
    DELETE_MESSAGES.bind(messages).update().apply()
  }

  def claimMessages(ids: List[MessageId])(implicit s: DBSession): Unit = {
    val messages = ids.map(_.value).mkString(",")
    CLAIM_MESSAGES.bind(messages).update().apply()
  }

  def getBytes(cmd: ZoneCommand): Array[Byte] = cmd match {
    case zc: ZoneChange => toPB(zc).toByteArray
    case rc: RecordSetChange => toPB(rc).toByteArray
    case bcc: BatchChangeCommand => bcc.id.getBytes
  }

  /* Generate params for insertion of messages */
  def insertParams(commands: NonEmptyList[ZoneCommand]): Seq[Seq[(Symbol, Any)]] =
    commands.toList.map(insertParams)

  def insertParams(cmd: ZoneCommand): Seq[(Symbol, Any)] = {
    val ts = Instant.now.truncatedTo(ChronoUnit.MILLIS)
    Seq(
      'id -> cmd.id,
      'messageType -> MessageType.fromCommand(cmd).value,
      'inFlight -> 0,
      'attempts -> 0,
      'data -> getBytes(cmd),
      'created -> ts,
      'timeoutSeconds -> 30 // TODO: This needs to be configuration driven
    )
  }

  /**
    * Algorithm is a little interesting.  All of this is in the same transaction
    *
    * 1. Fetch unclaimed messages from the database.  It is possible that they are in a bad disposition.
    * - if we cannot parse the message
    * - if the message type is unknown
    * - if the number of retries is exceeded
    *
    * 2. If there are any errors stemming from step 1, delete them
    *
    * 3. For all the good messages, mark them as in-flight, and increment the attempts
    */
  def receive(count: MessageCount): IO[List[CommandMessage]] =
    monitor("queue.JDBC.receive") {
      IO {
        // Need a max count so the user doesn't try to take 1MM messages, 10 is sufficient
        val limit = Math.min(10, count.value)

        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          // get unclaimed messages, note these could fail during retrieval
          val claimed = fetchUnclaimed(limit)

          // Errors could not be deserialized, have an invalid type, or exceeded retries
          val errors = claimed.collect {
            case Left((e, id)) =>
              val errorMessage = new StringWriter
              e.printStackTrace(new PrintWriter(errorMessage))
              logger.error(s"Encountered error for message with id $id. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
              id
          }

          // Successes are those that were properly serialized to a CommandMessage
          val successes = claimed.collect { case Right(ok) => ok }

          // Remove the errors from the database
          deleteMessages(errors)

          // Mark the ones we got successfully
          claimMessages(successes.map(_.id))
          successes
        }
      }
    }

  def requeue(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.requeue") {
      IO {
        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          REQUEUE_MESSAGE.bind(message.id.value).update().apply()
        }
      }
    }.as(())

  def remove(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.remove") {
      IO {
        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          deleteMessages(List(message.id))
        }
      }.as(())
    }

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("queue.JDBC.changeMessageTimeout") {
      IO {
        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          CHANGE_TIMEOUT.bind(duration.toSeconds, message.id.value).update().apply()
        }
      }.as(())
    }

  def sendBatch[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult[A]] =
    monitor("queue.JDBC.sendBatch") {
      IO {
        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          // Note, these really cannot fail, they all succeed or all fail
          // Other note, not doing a size check on the messages, but we should chunk these,
          // assuming a small number for right now which is not ideal
          INSERT_MESSAGE.batchByName(insertParams(messages): _*).apply()
          SendBatchResult(messages.toList, Nil)
        }
      }
    }

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitor("queue.JDBC.send") {
      IO {
        NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
          INSERT_MESSAGE.bindByName(insertParams(command): _*).update().apply()
        }
      }
    }

  def healthCheck(): HealthCheck =
    IO {
      NamedDB(QUEUE_CONNECTION_NAME).readOnly { implicit s =>
        HEALTH_CHECK.map(_ => ()).first.apply()
      }
    }.attempt.asHealthCheck(classOf[MySqlMessageQueue])
}
