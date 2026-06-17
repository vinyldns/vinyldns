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

package vinyldns.core.queue

import com.typesafe.config.Config
import pureconfig.ConfigReader
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

final case class MessageQueueConfig(
    className: String,
    pollingInterval: FiniteDuration,
    messagesPerPoll: Int,
    settings: Config,
    maxRetries: Int
)
object MessageQueueConfig {
  implicit val configReader: ConfigReader[MessageQueueConfig] = ConfigReader.fromCursor { c =>
    for {
      oc              <- c.asObjectCursor
      className       <- oc.atKey("class-name").flatMap(_.asString)
      settings        <- oc.atKey("settings").flatMap(_.asObjectCursor).map(_.objValue.toConfig)
      maxRetries      <- oc.atKey("max-retries").flatMap(_.asInt)
      pollingInterval <- oc.atKey("polling-interval").flatMap(ConfigReader[FiniteDuration].from)
      msgsPerPoll     <- oc.atKey("messages-per-poll").flatMap(_.asInt)
    } yield MessageQueueConfig(className, pollingInterval, msgsPerPoll, settings, maxRetries)
  }
}
