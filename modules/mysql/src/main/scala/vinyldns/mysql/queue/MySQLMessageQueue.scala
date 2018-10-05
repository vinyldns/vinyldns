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
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._
import vinyldns.core.route.Monitored
import vinyldns.mysql.queue.MessageType.{RecordChangeMessageType, ZoneChangeMessageType}
import vinyldns.mysql.queue.MySQLMessageQueue.{MessageId, MessageType}
import vinyldns.proto.VinylDNSProto

import scala.concurrent.duration.FiniteDuration

object MySQLMessageQueue {
  sealed abstract class MessageType(val value: Int)
  final case class InvalidMessageHandle(msg: String) extends Throwable(msg)
  final case class MessageAttemptsExceeded(msg: String) extends Throwable(msg)
  final case class MessageId(value: String) extends AnyVal
}

object MessageType {
  case object RecordChangeMessageType extends MessageType(1)
  case object ZoneChangeMessageType extends MessageType(2)
  final case class InvalidMessageType(msg: String) extends Throwable(msg)

  def fromCommand(cmd: ZoneCommand): MessageType = cmd match {
    case _: ZoneChange => ZoneChangeMessageType
    case _: RecordSetChange => RecordChangeMessageType
  }

  def fromInt(i: Int): Either[InvalidMessageType, MessageType] = i match {
    case 1 => Right(RecordChangeMessageType)
    case 2 => Right(ZoneChangeMessageType)
    case _ => Left(InvalidMessageType(s"$i is not a valid message type value"))
  }
}

final case class MySQLCommandMessage(id: MessageId, attempts: Int, command: ZoneCommand)
    extends CommandMessage

class MySQLMessageQueue
    extends MessageQueue[MySQLCommandMessage]
    with Monitored
    with ProtobufConversions {
  import MySQLMessageQueue._

  private val logger = LoggerFactory.getLogger(classOf[MySQLMessageQueue])

  private val INSERT_MESSAGE =
    sql"""
      |REPLACE INSERT INTO message_queue(id, message_type, in_flight, data, created, updated)
      |     VALUES ({id}, {messageType}, {inFlight}, {data}, {created}, {updated})
    """.stripMargin

  private val REQUEUE_MESSAGE =
    sql"""
      |UPDATE message_queue
      |   SET in_flight = 0, updated = NOW()
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
         |SELECT id, message_type, data, attempts
         |  FROM message_queue
         | WHERE in_flight = 0
         |    OR updated < DATE_SUB(NOW(),INTERVAL timeout_seconds SECOND)
         | LIMIT ?
    """.stripMargin

  private val DELETE_MESSAGES =
    sql"""
         |DELETE FROM message_queue WHERE id IN (?)
    """.stripMargin

  private val CLAIM_MESSAGES =
    sql"""
         |UPDATE message_queue
         |   SET in_flight=1, updated=NOW()
         | WHERE id in (?)
    """.stripMargin

  /* Parses a message from fields, returning the message id on failure, otherwise a good CommandMessage */
  def parseMessage(
      id: MessageId,
      typ: Int,
      data: Array[Byte],
      attempts: Int): Either[(Throwable, MessageId), MySQLCommandMessage] = {
    // parse the type, if it cannot parse we fail with the message id, same with the data
    for {
      messageType <- MessageType.fromInt(typ)
      cmd <- Either.catchNonFatal {
        messageType match {
          case ZoneChangeMessageType => fromPB(VinylDNSProto.ZoneChange.parseFrom(data))
          case RecordChangeMessageType => fromPB(VinylDNSProto.RecordSetChange.parseFrom(data))
        }
      }
      _ <- Either.cond(attempts < 100, (), MessageAttemptsExceeded(id.value)) // mark as error if too many attempts
    } yield MySQLCommandMessage(id, attempts, cmd)
  }.leftMap { e =>
    (e, id)
  }

  def fetchUnclaimed(limit: Int)(
      implicit s: DBSession): List[Either[(Throwable, MessageId), MySQLCommandMessage]] =
    FETCH_UNCLAIMED
      .bind(limit)
      .map { rs =>
        val id = MessageId(rs.string(1))
        val typ = rs.int(2)
        val data = rs.bytes(3)
        val attempts = rs.int(4)
        parseMessage(id, typ, data, attempts)
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
  }

  /* Generate params for insertion of messages */
  def genParams(commands: NonEmptyList[ZoneCommand]): Seq[Seq[(Symbol, Any)]] =
    commands.toList.map(genParams)

  def genParams(cmd: ZoneCommand): Seq[(Symbol, Any)] = {
    val ts = DateTime.now
    Seq(
      'id -> cmd.id,
      'messageType -> MessageType.fromCommand(cmd).value,
      'inFlight -> 0,
      'data -> getBytes(cmd),
      'created -> ts.getMillis,
      'updated -> ts.getMillis
    )
  }

  def receive(count: MessageCount): IO[List[MySQLCommandMessage]] =
    monitor("queue.JDBC.receive") {
      IO {
        // Need a max count, we can just do 10
        val limit = Math.min(10, count.value)

        DB.localTx { implicit s =>
          // get unclaimed messages, note these could fail during retrieval
          val claimed = fetchUnclaimed(limit)

          // Errors could not be deserialized, have an invalid type, or exceeded retries
          val errors = claimed.collect {
            case Left((e, id)) =>
              logger.error(s"Encountered error for message with id $id", e)
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

  def requeue(message: MySQLCommandMessage): IO[Unit] =
    monitor("queue.JDBC.requeue") {
      IO {
        DB.localTx { implicit s =>
          REQUEUE_MESSAGE.bind(message.id).update().apply()
        }
      }
    }.as(())

  def remove(message: MySQLCommandMessage): IO[Unit] =
    monitor("queue.JDBC.remove") {
      IO {
        DB.localTx { implicit s =>
          deleteMessages(List(message.id))
        }
      }.as(())
    }

  def changeMessageTimeout(message: MySQLCommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("queue.JDBC.changeMessageTimeout") {
      IO {
        DB.localTx { implicit s =>
          CHANGE_TIMEOUT.bind(message.id, duration.toSeconds).update().apply()
        }
      }.as(())
    }

  def send[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult] =
    monitor("queue.JDBC.sendBatch") {
      IO {
        DB.localTx { implicit s =>
          // Note, we do replace into, so these really cannot fail, they all succeed or all fail
          // Other note, not doing a size check on the messages, but we should chunk these, assuming a
          // small number for right now which is not ideal
          INSERT_MESSAGE.batchByName(genParams(messages): _*).apply()
          SendBatchResult(messages.toList, Nil)
        }
      }
    }

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitor("queue.JDBC.send") {
      IO {
        DB.localTx { implicit s =>
          INSERT_MESSAGE.bindByName(genParams(command): _*).update().apply()
        }
      }
    }
}
