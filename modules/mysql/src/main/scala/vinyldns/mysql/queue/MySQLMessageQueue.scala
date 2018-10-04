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
import vinyldns.mysql.queue.MySQLMessageQueue.MessageType
import vinyldns.proto.VinylDNSProto

import scala.concurrent.duration.FiniteDuration

object MySQLMessageQueue {
  sealed abstract class MessageType(val value: Int)
  object MessageType {
    case object RecordChangeMessageType extends MessageType(1)
    case object ZoneChangeMessageType extends MessageType(2)
    final case class InvalidMessageType(msg: String) extends Throwable(msg)
    final case class InvalidMessageHandle(msg: String) extends Throwable(msg)

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
}

final case class MySQLMessageHandle(id: String) extends MessageHandle

class MySQLMessageQueue extends MessageQueue with Monitored with ProtobufConversions {
  import MySQLMessageQueue.MessageType._
  private val logger = LoggerFactory.getLogger(classOf[MySQLMessageQueue])

  private val INSERT_MESSAGE =
    sql"""
      |REPLACE INSERT INTO message_queue(id, message_type, in_flight, data, created, updated)
      |     VALUES ({id}, {messageType}, {inFlight}, {data}, {created}, {updated})
    """.stripMargin

  private val GET_UNCLAIMED_MESSAGES =
    sql"""
      |SELECT id, message_type, data
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
      'messageType -> fromCommand(cmd).value,
      'inFlight -> 0,
      'data -> getBytes(cmd),
      'created -> ts.getMillis,
      'updated -> ts.getMillis
    )
  }

  def receive(count: MessageCount): IO[List[CommandMessage]] =
    monitor("queue.JDBC.receive") {
      def parseMessage(id: String, typ: Int, data: Array[Byte]): Either[String, ZoneCommand] = {
        // parse the type, if it cannot parse we fail with the message id, same with the data
        for {
          messageType <- MessageType.fromInt(typ)
          cmd <- Either.catchNonFatal {
            messageType match {
              case ZoneChangeMessageType => fromPB(VinylDNSProto.ZoneChange.parseFrom(data))
              case RecordChangeMessageType => fromPB(VinylDNSProto.RecordSetChange.parseFrom(data))
            }
          }
        } yield cmd
      }.leftMap { _ =>
        id
      } // return the id on failure, we will delete it later

      // run our query and get our records back
      def fetchUnclaimed(limit: Int)(implicit s: DBSession): List[Either[String, ZoneCommand]] =
        GET_UNCLAIMED_MESSAGES
          .bind(limit)
          .map { rs =>
            val id = rs.string(1)
            val typ = rs.int(2)
            val data = rs.bytes(3)
            parseMessage(id, typ, data)
          }
          .list()
          .apply()

      // Let's destroy messages with errors, and log loudly
      def destroyErrors(ids: List[String])(implicit s: DBSession): Int = {
        val messages = ids.mkString(",")
        logger.warn(s"Deleting errant messages, ids = $messages")
        DELETE_MESSAGES.bind(messages).update().apply()
      }

      // Claim the messages, updating the in-flight flag and the time stamp
      def markInFlight(ids: List[String])(implicit s: DBSession): Unit = {
        val messages = ids.mkString(",")
        CLAIM_MESSAGES.bind(messages).update().apply()
      }

      IO {
        // Need a max count, we can just do 10
        val limit = Math.min(10, count.value)

        DB.localTx { implicit s =>
          val claimed = fetchUnclaimed(limit)
          val errors = claimed.collect { case Left(e) => e }
          val successes = claimed.collect { case Right(ok) => ok }

          destroyErrors(errors)
          markInFlight(successes.map(_.id))

          successes.map(cmd => CommandMessage(MySQLMessageHandle(cmd.id), cmd))
        }
      }
    }

  def handle(handle: MessageHandle): Either[InvalidMessageHandle, MySQLMessageHandle] =
    handle match {
      case mysql: MySQLMessageHandle => Right(mysql)
      case bad =>
        Left(InvalidMessageHandle(s"Invalid message handle type provided: ${bad.getClass.getName}"))
    }

  def requeue(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.requeue") {
      IO.fromEither(handle(message.handle))
        .map { handle =>
          DB.localTx { implicit s =>
            REQUEUE_MESSAGE.bind(handle.id).update().apply()
          }
        }
        .as(())
    }

  def remove(message: CommandMessage): IO[Unit] =
    monitor("queue.JDBC.remove") {
      IO.fromEither(handle(message.handle))
        .map { handle =>
          DB.localTx { implicit s =>
            DELETE_MESSAGES.bind(handle.id).update().apply()
          }
        }
        .as(())
    }

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("queue.JDBC.changeMessageTimeout") {
      IO.fromEither(handle(message.handle))
        .map { handle =>
          DB.localTx { implicit s =>
            CHANGE_TIMEOUT.bind(handle.id, duration.toSeconds).update().apply()
          }
        }
        .as(())
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
