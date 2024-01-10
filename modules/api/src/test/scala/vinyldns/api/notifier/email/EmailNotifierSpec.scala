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

package vinyldns.api.notifier.email
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import vinyldns.api.CatsHelpers
import javax.mail.{Provider, Session, Transport, URLName}
import java.util.Properties
import vinyldns.core.domain.membership.{User, UserRepository}
import vinyldns.core.notifier.Notification

import javax.mail.internet.InternetAddress
import org.mockito.Matchers.{eq => eqArg, _}
import org.mockito.Mockito._
import org.mockito.ArgumentCaptor
import cats.effect.IO
import javax.mail.{Address, Message}
import _root_.vinyldns.core.domain.batch._
import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.AData
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import vinyldns.core.domain.Encrypted

import scala.collection.JavaConverters._
import vinyldns.core.notifier.NotifierConfig

object MockTransport extends MockitoSugar {
  val mockTransport: Transport = mock[Transport]
}

class MockTransport(session: Session, urlname: URLName) extends Transport(session, urlname) {
  import MockTransport._

  override def connect(): Unit = mockTransport.connect()

  override def close(): Unit = mockTransport.close()

  def sendMessage(msg: Message, addresses: Array[Address]): Unit =
    mockTransport.sendMessage(msg, addresses)
}

class EmailNotifierSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with CatsHelpers {

  import MockTransport._

  val mockUserRepository: UserRepository = mock[UserRepository]
  val session: Session = Session.getInstance(new Properties())
  session.setProvider(
    new Provider(
      Provider.Type.TRANSPORT,
      "smtp",
      "vinyldns.api.notifier.email.MockTransport",
      "vinyl",
      "1.0"
    )
  )

  override protected def beforeEach(): Unit =
    reset(mockUserRepository, mockTransport)

  def batchChange(
      description: Option[String] = None,
      changes: List[SingleChange] = List.empty
  ): BatchChange =
    BatchChange(
      "test",
      "testUser",
      description,
      Instant.now.truncatedTo(ChronoUnit.MILLIS),
      changes,
      None,
      BatchChangeApprovalStatus.AutoApproved,
      BatchChangeStatus.PendingProcessing,
      None,
      None,
      None,
      "testBatch"
    )

  "Email Notifier" should {
    "do nothing for unsupported Notifications" in {
      val emailConfig: Config = ConfigFactory.parseMap(
        Map[String, Any](
          "from" -> "Testing <test@test.com>",
          "smtp.host" -> "wouldfail.mail.com",
          "smtp.auth.mechanisms" -> "PLAIN"
        ).asJava
      )
      val notifier = new EmailNotifierProvider()
        .load(NotifierConfig("", emailConfig), mockUserRepository)
        .unsafeRunSync()

      notifier.notify(new Notification("this won't be supported ever")) should be(IO.unit)
    }

    "do nothing for user without email" in {
      val notifier = new EmailNotifier(
        EmailNotifierConfig(new InternetAddress("test@test.com"), new Properties()),
        session,
        mockUserRepository
      )

      doReturn(IO.pure(Some(User("testUser", "access", Encrypted("secret")))))
        .when(mockUserRepository)
        .getUser("test")

      notifier.notify(Notification(batchChange())).unsafeRunSync()

      verify(mockUserRepository).getUser("test")
    }

    "do nothing when user not found" in {
      val notifier = new EmailNotifier(
        EmailNotifierConfig(new InternetAddress("test@test.com"), new Properties()),
        session,
        mockUserRepository
      )

      doReturn(IO.pure(None))
        .when(mockUserRepository)
        .getUser("test")

      notifier.notify(Notification(batchChange())).unsafeRunSync()

      verify(mockUserRepository).getUser("test")
    }

    "send an email to a user" in {
      val fromAddress = new InternetAddress("test@test.com")
      val notifier = new EmailNotifier(
        EmailNotifierConfig(fromAddress, new Properties()),
        session,
        mockUserRepository
      )

      doReturn(
        IO.pure(Some(User("testUser", "access", Encrypted("secret"), None, None, Some("testuser@test.com"))))
      ).when(mockUserRepository)
        .getUser("test")

      val expectedAddresses = Array[Address](new InternetAddress("testuser@test.com"))
      val messageArgument = ArgumentCaptor.forClass(classOf[Message])

      doNothing().when(mockTransport).connect()
      doNothing()
        .when(mockTransport)
        .sendMessage(messageArgument.capture(), eqArg(expectedAddresses))
      doNothing().when(mockTransport).close()

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

      val message = messageArgument.getValue

      message.getFrom should be(Array(fromAddress))
      message.getContentType should be("text/html; charset=us-ascii")
      message.getAllRecipients should be(expectedAddresses)
      message.getSubject should be("VinylDNS Batch change testBatch results")
      val content = message.getContent.asInstanceOf[String]

      content.contains(change.id) should be(true)
      content.contains(description) should be(true)

      val regex = raw"<tr>((.|\s)+?)<\/tr>".r
      val rows = (for (m <- regex.findAllMatchIn(content)) yield m.group(0)).toList

      def assertSingleChangeCaptured(row: String, sc: SingleChange): Unit = {
        row.contains(sc.inputName) should be(true)
        row.contains(sc.typ.toString) should be(true)
        row.contains(sc.status.toString) should be(true)
        sc.systemMessage.foreach(row.contains(_) should be(true))
        sc match {
          case ac: SingleAddChange =>
            row.contains("Add") should be(true)
            ac.recordData match {
              case AData(address) => row.contains(address) should be(true)
              case _ => row.contains(ac.recordData) should be(true)
            }
            row.contains(ac.ttl.toString) should be(true)
          case dc: SingleDeleteRRSetChange =>
            row.contains("Delete") should be(true)
            dc.recordData match {
              case Some(AData(address)) => row.contains(address) should be(true)
              case Some(recordData) => row.contains(recordData) should be(true)
              case None => row.contains(dc.recordData) should be(false)
            }
        }
      }

      rows.tail.zip(singleChanges).foreach((assertSingleChangeCaptured _).tupled)

      verify(mockUserRepository).getUser("test")
      verify(mockTransport).sendMessage(any[Message], eqArg(expectedAddresses))

    }
  }

}
