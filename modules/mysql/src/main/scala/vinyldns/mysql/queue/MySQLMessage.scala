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
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.queue.CommandMessage
import vinyldns.mysql.queue.MySQLMessageQueue.MessageId

import scala.concurrent.duration.FiniteDuration

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
        Left(
          UnsupportedCommandMessage(
            s"${other.getClass.getName} is unsupported for MySQL Message Queue"))
    }
}
