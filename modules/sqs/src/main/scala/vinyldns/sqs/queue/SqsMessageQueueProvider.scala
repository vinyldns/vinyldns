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

package vinyldns.sqs.queue
import cats.effect.IO
import org.slf4j.LoggerFactory
import pureconfig.module.catseffect.loadConfigF
import vinyldns.core.queue.{MessageQueue, MessageQueueConfig, MessageQueueProvider}

import scala.util.matching.Regex

object SqsMessageQueueProvider extends MessageQueueProvider {
  final val DEFAULT_QUEUE_NAME = "sqs"

  sealed abstract class SqsMessageQueueProviderError(message: String) extends Throwable(message)
  final case class InvalidQueueName(queueName: String)
      extends SqsMessageQueueProviderError(
        s"Invalid queue name: $queueName. Must be 1-80 alphanumeric, hyphen or underscore characters.")

  private val logger = LoggerFactory.getLogger("SqsMessageQueueProvider")

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    for {
      settingsConfig <- loadConfigF[IO, SqsMessageQueueSettings](config.settings)
      queueName <- IO.fromEither(getQueueName(settingsConfig))
      queue <- setupQueue(settingsConfig, queueName)
    } yield queue

  def getQueueName(sqsMessageQueueSettings: SqsMessageQueueSettings)
    : Either[SqsMessageQueueProviderError, String] = {
    logger.info(s"Retrieving queue name...")
    sqsMessageQueueSettings.queueName match {
      case Some(name) => validateQueueName(name)
      case None => Right(SqsMessageQueueProvider.DEFAULT_QUEUE_NAME)
    }
  }

  def validateQueueName(queueName: String): Either[SqsMessageQueueProviderError, String] = {

    /** Queue name restrictions:
        - Up to 80 characters (alphanumeric, hyphens and underscores)
        - Name of a FIFO queue must end with ".fifo" suffix

      see: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-limits.html#limits-queues */
    val validQueueNameRegex: Regex = """^([\w\-]{1,80})$|^([\w\-]{1,75}\.fifo)$""".r

    validQueueNameRegex
      .findFirstIn(queueName)
      .map(Right(_))
      .getOrElse(Left(InvalidQueueName(queueName)))
  }

  def setupQueue(
      sqsMessageQueueSettings: SqsMessageQueueSettings,
      queueName: String): IO[SqsMessageQueue] = {
    logger.info(s"Setting up queue...")
    IO {
      SqsMessageQueue(sqsMessageQueueSettings, queueName)
    }
  }
}
