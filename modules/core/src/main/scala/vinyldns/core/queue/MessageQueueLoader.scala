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

import cats.effect.IO
import org.slf4j.LoggerFactory

object MessageQueueLoader {

  private val logger = LoggerFactory.getLogger("MessageQueueLoader")

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    for {
      _ <- IO(logger.info(s"Attempting to load queue ${config.className}"))
      provider <- IO(
        Class
          .forName(config.className)
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[MessageQueueProvider]
      )
      queue <- provider.load(config)
    } yield queue
}
