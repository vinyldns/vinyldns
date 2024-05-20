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

package vinyldns.api.notifier.sns

import cats.effect.{IO, Timer}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.typesafe.config.{Config, ConfigFactory}
import java.time.Instant
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpecLike
import vinyldns.api.MySqlApiIntegrationSpec
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.{AData, RecordType}
import vinyldns.core.notifier._
import vinyldns.mysql.MySqlIntegrationSpec

import scala.concurrent.ExecutionContext

class SnsNotifierIntegrationSpec
  extends MySqlApiIntegrationSpec
    with MySqlIntegrationSpec
    with Matchers
    with AnyWordSpecLike {

  import vinyldns.api.domain.DomainValidations._

  implicit val formats = DefaultFormats
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  val snsConfig: Config = ConfigFactory.load().getConfig("vinyldns.sns.settings")

  "Sns Notifier" should {

    "send a notification" in {
      val batchChange = BatchChange(
        okUser.id,
        okUser.userName,
        None,
        Instant.parse("2019-07-22T19:38:23Z"),
        List(
          SingleAddChange(
            Some("some-zone-id"),
            Some("zone-name"),
            Some("record-name"),
            "a" * HOST_MAX_LENGTH,
            RecordType.A,
            300,
            AData("1.1.1.1"),
            SingleChangeStatus.Complete,
            None,
            None,
            None
          )
        ),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved,
        id = "a615e2bb-8b35-4a39-8947-1edd0e653afa"
      )

      val credentialsProvider = new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(
          snsConfig.getString("access-key"),
          snsConfig.getString("secret-key")
        )
      )
      val sns = AmazonSNSClientBuilder.standard
        .withEndpointConfiguration(
          new EndpointConfiguration(
            sys.env.getOrElse("SNS_SERVICE_ENDPOINT", snsConfig.getString("service-endpoint")),
            snsConfig.getString("signing-region")
          )
        )
        .withCredentials(credentialsProvider)
        .build()

      val sqs = AmazonSQSClientBuilder
        .standard()
        .withEndpointConfiguration(
          new EndpointConfiguration(sys.env.getOrElse("SQS_SERVICE_ENDPOINT", "http://127.0.0.1:19003"), "us-east-1")
        )
        .withCredentials(credentialsProvider)
        .build()

      val program = for {
        queueUrl <- IO {
          sqs.createQueue("batchChanges").getQueueUrl
        }
        topic <- IO {
          sns.createTopic("batchChanges").getTopicArn
        }
        _ <- IO {
          sns.subscribe(topic, "sqs", queueUrl)
        }
        notifier <- new SnsNotifierProvider()
          .load(NotifierConfig("", snsConfig), userRepository, groupRepository)
        _ <- notifier.notify(Notification(batchChange))
        _ <- IO.sleep(1.seconds)
        messages <- IO {
          sqs.receiveMessage(queueUrl).getMessages
        }
        _ <- IO {
          sns.deleteTopic(topic)
          sqs.deleteQueue(queueUrl)
        }
      } yield messages

      val messages = program.unsafeRunSync()

      messages.size should be(1)

      val notification = parse(messages.get(0).getBody)
      (notification \ "Message").extract[String] should be(
        """{"userId":"ok","userName":"ok","createdTimestamp":"2019-07-22T19:38:23Z",""" +
          """"status":"Complete","approvalStatus":"AutoApproved","id":"a615e2bb-8b35-4a39-8947-1edd0e653afa"}"""
      )
    }

  }

}
