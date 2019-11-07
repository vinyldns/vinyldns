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
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}

sealed abstract class MessageType(val value: Int)
object MessageType {
  case object RecordChangeMessageType extends MessageType(1)
  case object ZoneChangeMessageType extends MessageType(2)
  case object BatchChangeMessageType extends MessageType(3)
  final case class InvalidMessageType(value: Int)
      extends Throwable(s"$value is not a valid message type value")

  def fromCommand(cmd: ZoneCommand): MessageType = cmd match {
    case _: ZoneChange => ZoneChangeMessageType
    case _: RecordSetChange => RecordChangeMessageType
    case _: BatchChangeCommand => BatchChangeMessageType
  }

  def fromInt(i: Int): Either[InvalidMessageType, MessageType] = i match {
    case 1 => Right(RecordChangeMessageType)
    case 2 => Right(ZoneChangeMessageType)
    case 3 => Right(BatchChangeMessageType)
    case _ => Left(InvalidMessageType(i))
  }
}
