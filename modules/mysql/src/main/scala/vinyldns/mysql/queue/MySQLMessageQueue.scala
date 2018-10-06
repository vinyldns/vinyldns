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

import scala.concurrent.duration._

object MessageType {
  case object RecordChangeMessageType extends MessageType(1)
  case object ZoneChangeMessageType extends MessageType(2)
  final case class InvalidMessageType(value: Int)
      extends Throwable(s"$value is not a valid message type value")

  def fromCommand(cmd: ZoneCommand): MessageType = cmd match {
    case _: ZoneChange => ZoneChangeMessageType
    case _: RecordSetChange => RecordChangeMessageType
  }

  def fromInt(i: Int): Either[InvalidMessageType, MessageType] = i match {
    case 1 => Right(RecordChangeMessageType)
    case 2 => Right(ZoneChangeMessageType)
    case _ => Left(InvalidMessageType(i))
  }
}

/* MySQL Command Message implementation */
final case class MySQLMessage(
    id: MessageId,
    attempts: Int,
    timeout: FiniteDuration,
    command: ZoneCommand)
    extends CommandMessage
object MySQLMessage {
  final case class UnsupportedCommandMessage(msg: String) extends Throwable(msg)

  /* Casts a CommandMessage safely, if not a MySQLCommandMessage, then we fail */
  def cast(message: CommandMessage): Either[UnsupportedCommandMessage, MySQLMessage] =
    message match {
      case mysql: MySQLMessage => Right(mysql)
      case other =>
        Left(UnsupportedCommandMessage(s"${other.getClass.getName} is unsupported for MySQL Queue"))
    }
}

object MySQLMessageQueue {
  sealed abstract class MessageType(val value: Int)
  final case class InvalidMessageHandle(msg: String) extends Throwable(msg)
  final case class MessageAttemptsExceeded(msg: String) extends Throwable(msg)
  final case class InvalidMessageTimeout(timeout: Int)
      extends Throwable(s"Invalid message timeout $timeout")
  final case class MessageId(value: String) extends AnyVal
}

class MySQLMessageQueue extends MessageQueue with Monitored with ProtobufConversions {
  import MySQLMessageQueue._

  private val logger = LoggerFactory.getLogger(classOf[MySQLMessageQueue])

  private val INSERT_MESSAGE =
    sql"""
      |INSERT IGNORE INTO message_queue(id, message_type, in_flight, data, created, updated, timeout_seconds, attempts)
      |     VALUES ({id}, {messageType}, {inFlight}, {data}, {created}, {updated}, {timeoutSeconds}, {attempts})
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

  /* Parses a message from fields, returning the message id on failure, otherwise a good CommandMessage */
  def parseMessage(
      id: MessageId,
      typ: Int,
      data: Array[Byte],
      attempts: Int,
      timeoutSeconds: Int): Either[(Throwable, MessageId), MySQLMessage] = {
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
      _ <- Either.cond(
        timeoutSeconds > 0,
        (),
        MySQLMessageQueue.InvalidMessageTimeout(timeoutSeconds))
    } yield MySQLMessage(id, attempts, timeoutSeconds.seconds, cmd)
  }.leftMap { e =>
    (e, id)
  }

  def fetchUnclaimed(limit: Int)(
      implicit s: DBSession): List[Either[(Throwable, MessageId), MySQLMessage]] =
    FETCH_UNCLAIMED
      .bind(limit)
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
  }

  /* Generate params for insertion of messages */
  def insertParams(commands: NonEmptyList[ZoneCommand]): Seq[Seq[(Symbol, Any)]] =
    commands.toList.map(insertParams)

  def insertParams(cmd: ZoneCommand): Seq[(Symbol, Any)] = {
    val ts = DateTime.now
    Seq(
      'id -> cmd.id,
      'messageType -> MessageType.fromCommand(cmd).value,
      'inFlight -> 0,
      'attempts -> 0,
      'data -> getBytes(cmd),
      'created -> ts,
      'updated -> ts,
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

  def requeue(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.requeue") {
      IO.fromEither(MySQLMessage.cast(message)).map { mysql =>
        DB.localTx { implicit s =>
          REQUEUE_MESSAGE.bind(mysql.id.value).update().apply()
        }
      }
    }.as(())

  def remove(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.remove") {
      IO.fromEither(MySQLMessage.cast(message))
        .map { mysql =>
          DB.localTx { implicit s =>
            deleteMessages(List(mysql.id))
          }
        }
        .as(())
    }

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("queue.JDBC.changeMessageTimeout") {
      IO.fromEither(MySQLMessage.cast(message))
        .map { mysql =>
          DB.localTx { implicit s =>
            CHANGE_TIMEOUT.bind(duration.toSeconds, mysql.id.value).update().apply()
          }
        }
        .as(())
    }

  def send[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult] =
    monitor("queue.JDBC.sendBatch") {
      IO {
        DB.localTx { implicit s =>
          // Note, we do replace into, so these really cannot fail, they all succeed or all fail
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
        DB.localTx { implicit s =>
          INSERT_MESSAGE.bindByName(insertParams(command): _*).update().apply()
        }
      }
    }
}
