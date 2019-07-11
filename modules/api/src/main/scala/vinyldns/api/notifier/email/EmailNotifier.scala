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

import vinyldns.core.notifier.{Notification, Notifier}
import cats.effect.IO
import vinyldns.core.domain.batch.BatchChange
import vinyldns.core.domain.membership.UserRepository
import vinyldns.core.domain.membership.User
import org.slf4j.LoggerFactory
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Message, Session}

import scala.util.Try
import vinyldns.core.domain.batch.SingleChange
import vinyldns.core.domain.batch.SingleAddChange
import vinyldns.core.domain.batch.SingleDeleteChange
import vinyldns.core.domain.record.AData
import vinyldns.core.domain.record.AAAAData
import vinyldns.core.domain.record.CNAMEData
import vinyldns.core.domain.record.MXData
import vinyldns.core.domain.record.TXTData
import vinyldns.core.domain.record.PTRData
import vinyldns.core.domain.record.RecordData
import org.joda.time.format.DateTimeFormat
import vinyldns.core.domain.batch.BatchChangeStatus._
import vinyldns.core.domain.batch.BatchChangeApprovalStatus._
import vinyldns.core.logging.StructuredArgs._

class EmailNotifier(config: EmailNotifierConfig, session: Session, userRepository: UserRepository)
    extends Notifier {

  private val logger = LoggerFactory.getLogger(classOf[EmailNotifier])

  def notify(notification: Notification[_]): IO[Unit] =
    notification.change match {
      case bc: BatchChange => sendBatchChangeNotification(bc)
      case _ => IO.unit
    }

  def send(addresses: Address*)(buildMessage: Message => Message): IO[Unit] = IO {
    val message = new MimeMessage(session)
    message.setRecipients(Message.RecipientType.TO, addresses.toArray)
    message.setFrom(config.from)
    buildMessage(message)
    message.saveChanges()
    val transport = session.getTransport("smtp")
    transport.connect()
    transport.sendMessage(message, message.getAllRecipients())
    transport.close()
  }

  def sendBatchChangeNotification(bc: BatchChange): IO[Unit] =
    userRepository.getUser(bc.userId).flatMap {
      case Some(UserWithEmail(email)) =>
        send(email) { message =>
          message.setSubject(s"VinylDNS Batch change ${bc.id} results")
          message.setContent(formatBatchChange(bc), "text/html")
          message
        }
      case Some(user: User) if user.email.isDefined =>
        IO {
          logger.warn(
            "Unable to properly parse email",
            entries(
              event(
                "batch-notification",
                user,
                Map("email" -> user.email.getOrElse("<none>"), "reason" -> "parser-error"))))
        }
      case None =>
        IO {
          logger.warn(
            "Unable to find user",
            entries(
              event(
                "batch-notification",
                Id(bc.userId, "user"),
                Map("reason" -> "user-not-found"))))
        }
      case _ => IO.unit
    }

  def formatBatchChange(bc: BatchChange): String =
    s"""<h1>Batch Change Results</h1>
      | <b>Submitter:</b> ${bc.userName} <br/>
      | ${bc.comments.map(comments => s"<b>Description:</b> ${comments}</br>").getOrElse("")}
      | <b>Created:</b> ${bc.createdTimestamp.toString(DateTimeFormat.fullDateTime)} <br/>
      | <b>Id:</b> ${bc.id}<br/>
      | <b>Status:</b> ${formatStatus(bc.approvalStatus, bc.status)}<br/>
      | <table border = "1">
      |   <tr><th>#</th><th>Change Type</th><th>Record Type</th><th>Input Name</th>
      |       <th>TTL</th><th>Record Data</th><th>Status</th><th>Message</th></tr>
      |   ${bc.changes.zipWithIndex.map((formatSingleChange _).tupled).mkString("\n")}
      | </table>
     """.stripMargin

  def formatStatus(approval: BatchChangeApprovalStatus, status: BatchChangeStatus): String =
    (approval, status) match {
      case (ManuallyRejected, _) => "Rejected"
      case (PendingApproval, _) => "Pending Approval"
      case (_, PartialFailure) => "Partially Failed"
      case (_, status) => status.toString
    }

  def formatSingleChange(sc: SingleChange, index: Int): String = sc match {
    case SingleAddChange(
        _,
        _,
        _,
        inputName,
        typ,
        ttl,
        recordData,
        status,
        systemMessage,
        _,
        _,
        _) =>
      s"""<tr><td>${index + 1}</td><td>Add</td><td>$typ</td><td>$inputName</td>
        |     <td>$ttl</td><td>${formatRecordData(recordData)}</td><td>$status</td>
        |     <td>${systemMessage.getOrElse("")}</td></tr>"""
    case SingleDeleteChange(_, _, _, inputName, typ, status, systemMessage, _, _, _) =>
      s"""<tr><td>${index + 1}</td><td>Delete</td><td>$typ</td><td>$inputName</td>
        |     <td></td><td></td><td>$status</td><td>${systemMessage.getOrElse("")}</td></tr>"""
  }

  def formatRecordData(rd: RecordData): String = rd match {
    case AData(address) => address
    case AAAAData(address) => address
    case CNAMEData(cname) => cname
    case MXData(preference, exchange) => s"Preference: $preference Exchange: $exchange"
    case PTRData(name) => name
    case TXTData(text) => text
    case _ => rd.toString
  }

  object UserWithEmail {
    def unapply(user: User): Option[Address] =
      for {
        email <- user.email
        address <- Try(new InternetAddress(email)).toOption
      } yield address
  }

}
