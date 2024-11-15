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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import vinyldns.api.CatsHelpers
import vinyldns.core.domain.membership.{GroupRepository, UserRepository}
import vinyldns.core.domain.membership.UserRepository
import vinyldns.core.notifier.Notification
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.ArgumentCaptor
import cats.effect.IO
import _root_.vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, SingleAddChange, SingleChange, SingleChangeStatus, SingleDeleteRRSetChange}
import java.time.Instant
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.AData
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._
import vinyldns.core.notifier.NotifierConfig
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.PublishResult

class SnsNotifierSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with CatsHelpers {

  val mockUserRepository = mock[UserRepository]
  val mockGroupRepository = mock[GroupRepository]
  val mockSns = mock[AmazonSNS]

  override protected def beforeEach(): Unit =
    reset(mockUserRepository, mockSns)

  def batchChange(
      description: Option[String] = None,
      changes: List[SingleChange] = List.empty
  ): BatchChange =
    BatchChange(
      "test",
      "testUser",
      description,
      Instant.parse("2019-07-22T17:01:19Z"),
      changes,
      None,
      BatchChangeApprovalStatus.AutoApproved,
      None,
      None,
      None,
      "testBatch"
    )

  "Sns Notifier" should {
    "do nothing for unsupported Notifications" in {
      val snsConfig: Config = ConfigFactory.parseMap(
        Map[String, Any](
          "topic-arn" -> "batches",
          "service-endpoint" -> "someValue",
          "signing-region" -> "us-east-1",
          "access-key" -> "access",
          "secret-key" -> "secret"
        ).asJava
      )
      val notifier = new SnsNotifierProvider()
        .load(NotifierConfig("", snsConfig), mockUserRepository, mockGroupRepository)
        .unsafeRunSync()

      notifier.notify(Notification("this won't be supported ever")) should be(IO.unit)
    }

    "send a notification" in {
      val notifier = new SnsNotifier(
        SnsNotifierConfig("batches", "someValue", "us-east-1", "access", "secret"),
        mockSns
      )

      val requestArgument = ArgumentCaptor.forClass(classOf[PublishRequest])

      doReturn(new PublishResult()).when(mockSns).publish(requestArgument.capture())

      val description = "notes"
      val singleChanges: List[SingleChange] = List(
        SingleAddChange(
          Some(""),
          Some(""),
          Some(""),
          "www.test.com",
          RecordType.A,
          200,
          AData("1.2.3.4"),
          SingleChangeStatus.Complete,
          None,
          None,
          None,
          List.empty
        ),
        SingleDeleteRRSetChange(
          Some(""),
          Some(""),
          Some(""),
          "deleteme.test.com",
          RecordType.A,
          None,
          SingleChangeStatus.Failed,
          Some("message for you"),
          None,
          None,
          List.empty
        )
      )
      val change = batchChange(Some(description), singleChanges)

      notifier.notify(Notification(change)).unsafeRunSync()

      val request = requestArgument.getValue

      request.getTopicArn should be("batches")
      val userNameAttribute = request.getMessageAttributes.get("userName")
      userNameAttribute.getDataType should be("String")
      userNameAttribute.getStringValue should be("testUser")

      request.getMessage should be(
        """{"userId":"test","userName":"testUser","comments":"notes",""" +
          """"createdTimestamp":"2019-07-22T17:01:19Z","status":"PartialFailure","approvalStatus":"AutoApproved",""" +
          """"id":"testBatch"}"""
      )

      verify(mockSns).publish(any[PublishRequest])

    }
  }

}
