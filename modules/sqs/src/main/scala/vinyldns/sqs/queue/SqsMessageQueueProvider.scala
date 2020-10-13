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
import com.amazonaws.auth.{
  AWSStaticCredentialsProvider,
  BasicAWSCredentials,
  DefaultAWSCredentialsProviderChain
}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import org.slf4j.LoggerFactory
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import cats.effect.Blocker
import vinyldns.core.queue.{MessageQueue, MessageQueueConfig, MessageQueueProvider}

import scala.util.matching.Regex
import cats.effect.ContextShift
import org.apache.commons.lang3.StringUtils

class SqsMessageQueueProvider extends MessageQueueProvider {
  import SqsMessageQueueProvider._

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    for {
      settingsConfig <- Blocker[IO].use(
        ConfigSource.fromConfig(config.settings).loadF[IO, SqsMessageQueueSettings](_)
      )
      _ <- IO.fromEither(validateQueueName(settingsConfig.queueName))
      client <- setupClient(settingsConfig)
      queueUrl <- setupQueue(client, settingsConfig.queueName)
      _ <- IO(logger.error(s"Queue URL: $queueUrl\n"))
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
      logger.debug(
        s"Setting up queue client with settings: " +
          s"service endpoint: ${sqsMessageQueueSettings.serviceEndpoint}; " +
          s"signing region: ${sqsMessageQueueSettings.serviceEndpoint}; " +
          s"queue name: ${sqsMessageQueueSettings.queueName}"
      )

      val sqsAsyncClientBuilder = AmazonSQSAsyncClientBuilder.standard()
      sqsAsyncClientBuilder.withEndpointConfiguration(
        new EndpointConfiguration(
          sqsMessageQueueSettings.serviceEndpoint,
          sqsMessageQueueSettings.signingRegion
        )
      )
      // If either of accessKey or secretKey are empty in conf file; then use AWSCredentialsProviderChain to figure out
      // credentials.
      // https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
      if (StringUtils.isBlank(sqsMessageQueueSettings.accessKey) || StringUtils.isBlank(
          sqsMessageQueueSettings.secretKey
        )) {
        sqsAsyncClientBuilder.withCredentials(
          new DefaultAWSCredentialsProviderChain()
        )
      } else {
        sqsAsyncClientBuilder.withCredentials(
          new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(
              sqsMessageQueueSettings.accessKey,
              sqsMessageQueueSettings.secretKey
            )
          )
        )
      }
      sqsAsyncClientBuilder.build()
    }

  def setupQueue(client: AmazonSQSAsync, queueName: String): IO[String] =
    // Create queue if it doesn't exist
    IO {
      logger.error(s"Setting up queue with name [$queueName]")
      client.getQueueUrl(queueName).getQueueUrl
    }.recoverWith {
      case _: QueueDoesNotExistException => IO(client.createQueue(queueName).getQueueUrl)
    }
}

object SqsMessageQueueProvider {
  final case class InvalidQueueName(queueName: String)
      extends Throwable(
        s"Invalid queue name: $queueName. Must be 1-80 alphanumeric, hyphen or underscore characters. FIFO queues " +
          "(queue names ending in \".fifo\") are not supported."
      )

  private val logger = LoggerFactory.getLogger(classOf[SqsMessageQueueProvider])
}
