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
import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeType, GroupRepository, MembershipAccessStatus, User, UserRepository}
import org.slf4j.LoggerFactory

import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Message, Session}
import scala.util.Try
import vinyldns.core.domain.record.{AAAAData, AData, CNAMEData, MXData, OwnerShipTransferStatus, PTRData, RecordData, RecordSetChange, TXTData}
import vinyldns.core.domain.record.OwnerShipTransferStatus.OwnerShipTransferStatus

import java.time.format.{DateTimeFormatter, FormatStyle}
import vinyldns.core.domain.batch.BatchChangeStatus._
import vinyldns.core.domain.batch.BatchChangeApprovalStatus._
import vinyldns.core.domain.zone.Zone

import java.time.ZoneId

class EmailNotifier(config: EmailNotifierConfig, session: Session, userRepository: UserRepository, groupRepository: GroupRepository)
    extends Notifier {

  private val logger = LoggerFactory.getLogger(classOf[EmailNotifier])

  def notify(notification: Notification[_]): IO[Unit] =
    notification.change match {
      case bc: BatchChange => sendBatchChangeNotification(bc)
      case rsc: RecordSetChange => sendRecordSetOwnerTransferNotification(rsc)
      case gc: GroupChange => sendGroupChangeNotification(gc)
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

  def sendRecordSetOwnerTransferNotification(rsc: RecordSetChange): IO[Unit] =
    for {
      currentUser <-  userRepository.getUser(rsc.userId)
      currentGroup <- groupRepository.getGroup(rsc.recordSet.ownerGroupId.getOrElse("<none>"))
      ownerGroup <- groupRepository.getGroup(rsc.recordSet.recordSetGroupChange.map(_.requestedOwnerGroupId.getOrElse("<none>")).getOrElse("<none>"))
      currentUsers <- currentGroup match {
        case Some(group) =>
          val users = group.memberIds.toList.map { id =>
            userRepository.getUser(id).map {
              case Some(user) => user
              case None => null
            }
          }
          users.traverse(identity)
        case None => IO.pure(List.empty)
      }
      ownerUsers <- ownerGroup match {
        case Some(group) =>
          val users = group.memberIds.toList.map { id =>
            userRepository.getUser(id).map {
              case Some(user) => user
              case None => null
            }
          }
          users.traverse(identity)
        case None => IO.pure(List.empty)
      }
      toEmails = currentUsers.collect { case UserWithEmail(address) => address }
      ccEmails = ownerUsers.collect { case UserWithEmail(address) => address }
      _ <- send(toEmails: _*)(ccEmails: _*) { message =>
        message.setSubject(s"VinylDNS RecordSet Ownership transfer")
        message.setContent(formatRecordSetOwnerShipTransfer(rsc, currentUser, currentGroup, ownerGroup), "text/html")
        message
      }
    } yield ()

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
                                       ownerGroup: Option[Group]
                                       ): String = {
    val portalHost = config.smtp.getProperty("mail.smtp.portal.url")
    val sb = new StringBuilder
    sb.append(s"""<h2><u>RecordSet Ownership Transfer Alert: </u></h2>
                 |<b>Submitted time: </b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(rsc.created)} <br/><br/>
                 | <b>Submitter: </b> ${currentUser.get.userName}<br/><br/>
                 | <b>Zone: </b> ${if(portalHost != null) s"""<a href="$portalHost/zones/${rsc.zone.id}">${rsc.zone.name}</a> <br/>"""
                   else s"${rsc.zone.name} <br/>"}
                 | <br/><table border = "1">
                 |  <tr>
                 |   <th>RecordSet</th>
                 |   <th>Record Type</th>
                 |   <th>TTL</th>
                 |   <th>Record Data</th>
                 |  </tr>
                 |  <tr>
                 |   <td>${rsc.recordSet.name}</td>
                 |   <td>${rsc.recordSet.typ}</td>
                 |   <td>${rsc.recordSet.ttl}</td>
                 |   <td>${rsc.recordSet.records.map(id =>id)}</td>
                 |  </tr>
                 | </table><br/>
                 | <b>Current Owner Group: </b> ${currentGroup.get.name} <br/><br/>
                 | <b>Transfer Owner Group: </b> ${ownerGroup.get.name} <br/><br/>
                 | <b>Status: ${formatOwnerShipStatus(rsc.recordSet.recordSetGroupChange.map(_.ownerShipTransferStatus).get,rsc.zone,portalHost)}</b>
                 | <br/><br/>
               """.stripMargin)
    sb.toString
  }

  def formatOwnerShipStatus(status: OwnerShipTransferStatus, zone:Zone, portalHost: String): String =
    status match {
      case OwnerShipTransferStatus.ManuallyRejected => "<i style=\"color: red;\">Rejected</i>"
      case OwnerShipTransferStatus.PendingReview => s"""<i style=\"color: blue;\">Pending Review </i> <br/><br/>
                                                    Requesting your review for the Ownership transfer. <br/>
                                                    ${if(portalHost != null)
                                                    s"""<a href="$portalHost/zones/${zone.id}">Go to Zones </a>
                                                     >>>  Search by RecordSet Name  >>>  Click Close Request </i><br/>"""
                                                    else s""}"""
      case OwnerShipTransferStatus.ManuallyApproved => "<i style=\"color: green;\">Approved</i>"
      case OwnerShipTransferStatus.Cancelled => "<i style=\"color: dark grey;\">Cancelled</i>"
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

  def sendGroupChangeNotification(gc: GroupChange): IO[Unit] = {
    groupRepository.getGroup(gc.newGroup.id).flatMap {
      case Some(grp) =>
        GroupWithEmail.apply(grp) match {
          case Some(email) =>
            send(email)() { message =>
              message.setSubject(s"VinylDNS Group ${grp.name} change notification")
              message.setContent(formatGroupChange(gc, grp), "text/html")
              message
            }
          case None =>
            IO {
              logger.warn(s"Group ${grp.name} (${grp.id}) doesn't have an email address configured")
            }
        }
      case None =>
        IO {
          logger.warn(s"Unable to find group: ${gc.newGroup.id}")
        }
    }
  }

  def formatGroupChange(gc: GroupChange, group: Group): String = {
    val sb = new StringBuilder
    sb.append(s"""<h1>Group Change Notification</h1>
                 | <b>Group Name:</b> ${group.name} <br/>
                 | <b>Group ID:</b> ${group.id} <br/>
                 | <b>Change Type:</b> ${gc.changeType} <br/>
                 | <b>Changed By:</b> ${gc.userId} <br/>
                 | <b>Time:</b> ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withZone(ZoneId.systemDefault()).format(gc.created)} <br/>""".stripMargin)

    if (gc.changeType == GroupChangeType.Update) {
      val addedAdmins =
        (gc.newGroup.adminUserIds.diff(gc.oldGroup.map(_.adminUserIds).getOrElse(Set.empty[String])))
      val removedAdmins =
        (gc.oldGroup.map(_.adminUserIds.diff(gc.newGroup.adminUserIds)))

      val addedMembers =
        (gc.newGroup.memberIds.diff(gc.oldGroup.map(_.memberIds).getOrElse(Set.empty[String])))
      val removedMembers =
        (gc.oldGroup.map(_.memberIds.diff(gc.newGroup.memberIds)))

      if (addedAdmins.nonEmpty || removedAdmins.nonEmpty) {
        sb.append("<h2>Administrator Changes</h2>")

        if (addedAdmins.nonEmpty) {
          sb.append("<b>New Administrators:</b><br/>")
          sb.append("<ul>")
          addedAdmins.foreach(admin => sb.append(s"<li>$admin</li>"))
          sb.append("</ul>")
        }

        if (removedAdmins.nonEmpty) {
          sb.append("<b>Removed Administrators:</b><br/>")
          sb.append("<ul>")
          removedAdmins.foreach(admin => sb.append(s"<li>$admin</li>"))
          sb.append("</ul>")
        }
      }
      if (addedMembers.nonEmpty || removedMembers.nonEmpty) {
        sb.append("<h2>Member Changes</h2>")

        if (addedMembers.nonEmpty) {
          sb.append("<b>New Members:</b><br/>")
          sb.append("<ul>")
          addedMembers.foreach(member => sb.append(s"<li>$member</li>"))
          sb.append("</ul>")
        }
        if (removedMembers.nonEmpty) {
          sb.append("<b>Removed Members:</b><br/>")
          sb.append("<ul>")
          removedMembers.foreach(member => sb.append(s"<li>$member</li>"))
          sb.append("</ul>")
        }
      }
      val oldMas = gc.oldGroup.map(_.membershipAccessStatus.getOrElse(MembershipAccessStatus(Set(), Set(), Set())))
      val newMas = gc.newGroup.membershipAccessStatus.getOrElse(MembershipAccessStatus(Set(), Set(), Set()))

      val newPendingMembers = newMas.pendingReviewMember.diff(oldMas.map(_.pendingReviewMember).getOrElse(Set.empty))
      val newApprovedMembers = newMas.approvedMember.diff(oldMas.map(_.approvedMember).getOrElse(Set.empty))
      val newRejectedMembers = newMas.rejectedMember.diff(oldMas.map(_.rejectedMember).getOrElse(Set.empty))

      if (newPendingMembers.nonEmpty || newApprovedMembers.nonEmpty || newRejectedMembers.nonEmpty) {
        sb.append("<h2>Membership Access Changes</h2>")
        if (newPendingMembers.nonEmpty) {
          sb.append("<b>New Membership Requests:</b><br/>")
          sb.append("<ul>")
          newPendingMembers.foreach(m => sb.append(s"<li>${m.userId} (Requested by: ${m.submittedBy})</li>"))
          sb.append("</ul>")
        }
        if (newApprovedMembers.nonEmpty) {
          sb.append("<b>Approved Membership Requests:</b><br/>")
          sb.append("<ul>")
          newApprovedMembers.foreach(m => sb.append(s"<li>${m.userId}</li>"))
          sb.append("</ul>")
        }
        if (newRejectedMembers.nonEmpty) {
          sb.append("<b>Rejected Membership Requests:</b><br/>")
          sb.append("<ul>")
          newRejectedMembers.foreach(m => sb.append(s"<li>${m.userId}</li>"))
          sb.append("</ul>")
        }
      }
    }
    sb.toString
  }


  object UserWithEmail {
    def unapply(user: User): Option[Address] =
      for {
        email <- user.email
        address <- Try(new InternetAddress(email)).toOption
      } yield address
  }

  object GroupWithEmail {
    def apply(group: Group): Option[Address] =
      for {
        email <- Some(group.email)
        address <- Try(new InternetAddress(email)).toOption
      } yield address
  }

}
