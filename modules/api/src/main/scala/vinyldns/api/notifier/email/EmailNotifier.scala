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
import cats.implicits._
import cats.effect.IO
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, SingleAddChange, SingleChange, SingleDeleteRRSetChange}
import vinyldns.core.domain.membership.{Group, GroupRepository, User, UserRepository}
import org.slf4j.LoggerFactory

import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Message, Session}
import scala.util.Try
import vinyldns.core.domain.record.{AAAAData, AData, CNAMEData, MXData, OwnerShipTransferStatus, PTRData, RecordData, RecordSetChange, TXTData}
import vinyldns.core.domain.record.OwnerShipTransferStatus.OwnerShipTransferStatus

import java.time.format.{DateTimeFormatter, FormatStyle}
import vinyldns.core.domain.batch.BatchChangeStatus._
import vinyldns.core.domain.batch.BatchChangeApprovalStatus._

import java.time.ZoneId

class EmailNotifier(config: EmailNotifierConfig, session: Session, userRepository: UserRepository, groupRepository: GroupRepository)
    extends Notifier {

  private val logger = LoggerFactory.getLogger(classOf[EmailNotifier])

  def notify(notification: Notification[_]): IO[Unit] =
    notification.change match {
      case bc: BatchChange => sendBatchChangeNotification(bc)
      case rsc: RecordSetChange => sendRecordSetOwnerTransferNotification(rsc)
      case _ => IO.unit
    }


  def send(toAddresses: Address*)(ccAddresses: Address*)(buildMessage: Message => Message): IO[Unit] = IO {
    val message = new MimeMessage(session)
    message.setRecipients(Message.RecipientType.TO, toAddresses.toArray)
    message.setRecipients(Message.RecipientType.CC, ccAddresses.toArray)
    message.setFrom(config.from)
    buildMessage(message)
    message.saveChanges()
    val transport = session.getTransport("smtp")
    transport.connect()
    transport.sendMessage(message, message.getAllRecipients)
    transport.close()
  }

  def sendBatchChangeNotification(bc: BatchChange): IO[Unit] = {
    userRepository.getUser(bc.userId).flatMap {
      case Some(UserWithEmail(email))  =>
        send(email)() { message =>
          message.setSubject(s"VinylDNS Batch change ${bc.id} results")
          message.setContent(formatBatchChange(bc), "text/html")
          message
        }
      case Some(user: User) if user.email.isDefined =>
        IO {
          logger.warn(
            s"Unable to properly parse email for ${user.id}: ${user.email.getOrElse("<none>")}"
          )
        }
      case None => IO {
        logger.warn(s"Unable to find user: ${bc.userId}")
      }
      case _ => IO.unit
    }
  }

  def sendRecordSetOwnerTransferNotification(rsc: RecordSetChange): IO[Unit] = {
    for {
      currentUser <-  userRepository.getUser(rsc.userId)
      currentGroup <- groupRepository.getGroup(rsc.recordSet.ownerGroupId.getOrElse("<none>"))
      ownerGroup <- groupRepository.getGroup(rsc.recordSet.recordSetGroupChange.map(_.requestedOwnerGroupId.getOrElse("<none>")).getOrElse("<none>"))
      _ <- currentGroup match {
        case Some(group) =>
          group.memberIds.toList.traverse { id =>
            userRepository.getUser(id).flatMap {
              case Some(UserWithEmail(toEmail)) =>
                ownerGroup match {
                  case Some(ccg) =>
                    ccg.memberIds.toList.traverse { id =>
                        userRepository.getUser(id).flatMap {
                          case Some(ccUser) =>
                            val ccEmail = ccUser.email.getOrElse("<none>")
                            send(toEmail)(new InternetAddress(ccEmail)) { message =>
                              message.setSubject(s"VinylDNS RecordSet Ownership transfer for ${rsc.recordSet.name} ")
                              message.setContent(formatRecordSetOwnerShipTransfer(rsc, currentUser, currentGroup, ownerGroup), "text/html")
                              message
                            }
                          case None =>
                            IO.unit
                        }
                      }
                  case None => IO.unit
                }
              case Some(user: User) if user.email.isDefined =>
                IO {
                  logger.warn(
                    s"Unable to properly parse email for ${user.id}: ${user.email.getOrElse("<none>")}"
                  )
                }
              case None =>
                IO {
                  logger.warn(s"Unable to find user: ${rsc.userId}")
                }
              case _ =>
                IO.unit
            }
          }
        case None => IO.unit // Handle case where toGroup is None
      }
    } yield ()
  }


  def formatBatchChange(bc: BatchChange): String = {
    val sb = new StringBuilder
    // Batch change info
    sb.append(s"""<h1>Batch Change Results</h1>
      | <b>Submitter:</b> ${bc.userName} <br/>
      | ${bc.comments.map(comments => s"<b>Description:</b> $comments</br>").getOrElse("")}
      | <b>Created:</b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(bc.createdTimestamp)} <br/>
      | <b>Id:</b> ${bc.id}<br/>
      | <b>Status:</b> ${formatBatchStatus(bc.approvalStatus, bc.status)}<br/>""".stripMargin)

    // For manually reviewed e-mails, add additional info; e-mails are not sent for pending batch changes
    if (bc.approvalStatus != AutoApproved) {
      bc.reviewComment.foreach(
        reviewComment => sb.append(s"<b>Review comment:</b> $reviewComment <br/>")
      )
      bc.reviewTimestamp.foreach(
        reviewTimestamp =>
          sb.append(
            s"<b>Time reviewed:</b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(reviewTimestamp)} <br/>"
          )
      )
    }

    bc.cancelledTimestamp.foreach(
      cancelledTimestamp =>
        sb.append(
          s"<b>Time cancelled:</b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(cancelledTimestamp)} <br/>"
        )
    )

    // Single change data table
    sb.append(s"""<br/><table border = "1">
      |   <tr><th>#</th><th>Change Type</th><th>Record Type</th><th>Input Name</th>
      |       <th>TTL</th><th>Record Data</th><th>Status</th><th>Message</th></tr>
      |   ${bc.changes.zipWithIndex.map((formatSingleChange _).tupled).mkString("\n")}
      | </table>
     """.stripMargin)
    sb.toString
  }


  def formatBatchStatus(approval: BatchChangeApprovalStatus, status: BatchChangeStatus): String =
    (approval, status) match {
      case (ManuallyRejected, _) => "Rejected"
      case (BatchChangeApprovalStatus.PendingReview, _) => "Pending Review"
      case (_, PartialFailure) => "Partially Failed"
      case (_, status) => status.toString
    }

  def formatRecordSetOwnerShipTransfer(rsc: RecordSetChange,
                                       currentUser: Option[User],
                                       currentGroup: Option[Group],
                                       ownerGroup: Option[Group]): String = {
    val sb = new StringBuilder
    sb.append(s"""<h2>RecordSet Ownership Transfer Alert: </h2>
                 | <b>Zone: </b> ${rsc.zone.name}</a><br/><br/>
                 | <b>RecordSet: </b> ${rsc.recordSet.name}<br/><br/>
                 | <b>Current Group: </b> ${currentGroup.get.name} <br/><br/>
                 | <b>Transfer Group: </b> ${ownerGroup.get.name} <br/><br/>
                 | <b>Status: </b> ${formatOwnerShipStatus(rsc.recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).get)}
                 | <b>Submitter: </b> ${currentUser.get.userName}<br/><br/>
                 | <b>Submitted time: </b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(rsc.created)} <br/><br/>
               """.stripMargin)
    sb.toString
  }

  def formatOwnerShipStatus(status: OwnerShipTransferStatus): String =
    status match {
      case OwnerShipTransferStatus.ManuallyRejected => "Rejected"
      case OwnerShipTransferStatus.PendingReview => "Pending Review"
      case OwnerShipTransferStatus.ManuallyApproved => "Approved"
      case OwnerShipTransferStatus.Cancelled => "Cancelled"
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
        _,
        _
        ) =>
      s"""<tr><td>${index + 1}</td><td>Add</td><td>$typ</td><td>$inputName</td>
        |     <td>$ttl</td><td>${formatRecordData(recordData)}</td><td>$status</td>
        |     <td>${systemMessage.getOrElse("")}</td></tr>"""
    case SingleDeleteRRSetChange(
        _,
        _,
        _,
        inputName,
        typ,
        recordData,
        status,
        systemMessage,
        _,
        _,
        _,
        _
        ) =>
      val recordDataValue = recordData.map(_.toString).getOrElse("")
      s"""<tr><td>${index + 1}</td><td>Delete</td><td>$typ</td><td>$inputName</td>
        |     <td></td><td>$recordDataValue</td><td>$status</td><td>${systemMessage
        .getOrElse("")}</td></tr>"""
  }

  def formatRecordData(rd: RecordData): String = rd match {
    case AData(address) => address
    case AAAAData(address) => address
    case CNAMEData(cname) => cname.fqdn
    case MXData(preference, exchange) => s"Preference: $preference Exchange: $exchange"
    case PTRData(name) => name.fqdn
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
