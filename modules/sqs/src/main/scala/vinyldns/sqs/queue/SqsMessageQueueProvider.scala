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
import cats.implicits._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import org.slf4j.LoggerFactory
import pureconfig.module.catseffect.loadConfigF
import vinyldns.core.queue.{MessageQueue, MessageQueueConfig, MessageQueueProvider}

import scala.util.matching.Regex

class SqsMessageQueueProvider extends MessageQueueProvider {
  import SqsMessageQueueProvider._

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    for {
      settingsConfig <- loadConfigF[IO, SqsMessageQueueSettings](config.settings)
      _ <- IO.fromEither(validateQueueName(settingsConfig.queueName))
      client <- setupClient(settingsConfig)
      queueUrl <- setupQueue(client, settingsConfig.queueName)
    } yield new SqsMessageQueue(queueUrl, client)

  def validateQueueName(queueName: String): Either[InvalidQueueName, String] = {

    /** Queue name restrictions:
        - Up to 80 characters (alphanumeric, hyphens and underscores)
        - FIFO (exactly-once processing) queues are not supported

      see: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-limits.html#limits-queues */
    val validQueueNameRegex: Regex = """^([\w\-]{1,80})$""".r

    validQueueNameRegex
      .findFirstIn(queueName)
      .map(Right(_))
      .getOrElse(Left(InvalidQueueName(queueName)))
  }

  def setupClient(sqsMessageQueueSettings: SqsMessageQueueSettings): IO[AmazonSQSAsync] =
    IO {
      AmazonSQSAsyncClientBuilder
        .standard()
        .withEndpointConfiguration(
          new EndpointConfiguration(
            sqsMessageQueueSettings.serviceEndpoint,
            sqsMessageQueueSettings.signingRegion))
        .withCredentials(
          new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(
              sqsMessageQueueSettings.accessKey,
              sqsMessageQueueSettings.secretKey)))
        .build()
    }

  def setupQueue(client: AmazonSQSAsync, queueName: String): IO[String] = {
    logger.info(s"Setting up queue...")
    // Create queue if it doesn't exist
    IO {
      client.getQueueUrl(queueName).getQueueUrl
    }.recoverWith {
      case _: QueueDoesNotExistException => IO(client.createQueue(queueName).getQueueUrl)
    }
  }
}

object SqsMessageQueueProvider {
  final case class InvalidQueueName(queueName: String)
      extends Throwable(
        s"Invalid queue name: $queueName. Must be 1-80 alphanumeric, hyphen or underscore characters. FIFO queues " +
        "(queue names ending in \".fifo\") are not supported.")

  private val logger = LoggerFactory.getLogger(classOf[SqsMessageQueueProvider])
}
