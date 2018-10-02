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
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult}
import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.route.Monitor

object SqsConnection {

  def apply(config: Config = ConfigFactory.load().getConfig("vinyldns.sqs")): SqsConnection = {
    val accessKey = config.getString("access-key")
    val secretKey = config.getString("secret-key")
    val serviceEndpoint = config.getString("service-endpoint")
    val signingRegion = config.getString("signing-region")
    val queueUrl = config.getString("queue-url")

    val client =
      AmazonSQSAsyncClientBuilder
        .standard()
        .withEndpointConfiguration(new EndpointConfiguration(serviceEndpoint, signingRegion))
        .withCredentials(
          new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
        .build()

    LiveSqsConnection(client, queueUrl)
  }
}

trait SqsConnection {
  def client: AmazonSQSAsync
  def queueUrl: String
  def shutdown(): Unit

  def sqsAsync[A <: AmazonWebServiceRequest, B <: AmazonWebServiceResult[_]](
      request: A,
      f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B]): IO[B] =
    IO.async[B] { complete: (Either[Throwable, B] => Unit) =>
      val asyncHandler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit = complete(Left(exception))

        def onSuccess(request: A, result: B): Unit = complete(Right(result))
      }

      f(request, asyncHandler)
    }

  def monitored[A](name: String)(f: => IO[A]): IO[A] = {
    val monitor = Monitor(name)
    val startTime = System.currentTimeMillis

    def timeAndRecord: Boolean => Unit = monitor.capture(monitor.duration(startTime), _)

    def failed(): Unit = timeAndRecord(false)
    def succeeded(): Unit = timeAndRecord(true)

    f.attempt.flatMap {
      case Left(error) =>
        failed()
        IO.raiseError(error)
      case Right(ok) =>
        succeeded()
        IO.pure(ok)
    }
  }
}

final case class LiveSqsConnection(client: AmazonSQSAsync, queueUrl: String) extends SqsConnection {
  def shutdown(): Unit = client.shutdown()
}
