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

package vinyldns.api.route

import java.util.UUID
import cats.data._
import cats.implicits._
import org.joda.time.DateTime
import org.json4s._
import vinyldns.api.config.Messages
import vinyldns.api.domain.MessagesService.finalMessages
import vinyldns.api.domain.membership._
import vinyldns.core.domain.membership.{Group, GroupChangeType, GroupStatus, LockStatus}

object MembershipJsonProtocol {
  final case class CreateGroupInput(
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId],
      admins: Set[UserId]
  )
  final case class UpdateGroupInput(
      id: String,
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId],
      admins: Set[UserId]
  )
}

/* Defines the JSON serialization to support the Membership Routes */
trait MembershipJsonProtocol extends JsonValidation {

  import MembershipJsonProtocol._

  val membershipSerializers: Seq[Serializer[_]] = Seq(
    GroupSerializer,
    GroupInfoSerializer,
    GroupChangeInfoSerializer,
    CreateGroupInputSerializer,
    UpdateGroupInputSerializer,
    JsonEnumV(LockStatus),
    JsonEnumV(GroupStatus),
    JsonEnumV(GroupChangeType)
  )

  case object CreateGroupInputSerializer extends ValidationSerializer[CreateGroupInput] {
    override def fromJson(js: JValue): ValidatedNel[String, CreateGroupInput] =
      (
        (js \ "name").required[String](finalMessages("missing-group-name") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "email").required[String](finalMessages("missing-group-email") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "description").optional[String],
        (js \ "members").required[Set[UserId]](finalMessages("missing-group-members") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "admins").required[Set[UserId]](finalMessages("missing-group-admins") match {
          case Messages(_, _, message) =>
            message
        })
      ).mapN(CreateGroupInput.apply)
  }
  case object UpdateGroupInputSerializer extends ValidationSerializer[UpdateGroupInput] {
    override def fromJson(js: JValue): ValidatedNel[String, UpdateGroupInput] =
      (
        (js \ "id").required[String](finalMessages("missing-group-id") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "name").required[String](finalMessages("missing-group-name") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "email").required[String](finalMessages("missing-group-email") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "description").optional[String],
        (js \ "members").required[Set[UserId]](finalMessages("missing-group-members") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "admins").required[Set[UserId]](finalMessages("missing-group-admins") match {
          case Messages(_, _, message) =>
            message
        })
      ).mapN(UpdateGroupInput.apply)
  }

  /**
    * This is unfortunate, but is necessary to support tests at least right now because of the way that Json
    * serialization works with Enum values.
    */
  case object GroupSerializer extends ValidationSerializer[Group] {
    override def fromJson(js: JValue): ValidatedNel[String, Group] =
      (
        (js \ "name").required[String](finalMessages("missing-group-name") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "email").required[String](finalMessages("missing-group-email") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "description").optional[String],
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "created").default[DateTime](DateTime.now),
        (js \ "status").default(GroupStatus, GroupStatus.Active),
        (js \ "memberIds").default[Set[String]](Set.empty),
        (js \ "adminUserIds").default[Set[String]](Set.empty)
      ).mapN(Group.apply)
  }

  case object GroupInfoSerializer extends ValidationSerializer[GroupInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, GroupInfo] =
      (
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "name").required[String](finalMessages("missing-group-name") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "email").required[String](finalMessages("missing-group-email") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "description").optional[String],
        (js \ "created").default[DateTime](DateTime.now),
        (js \ "status").default(GroupStatus, GroupStatus.Active),
        (js \ "members").default[Set[UserId]](Set.empty),
        (js \ "admins").default[Set[UserId]](Set.empty)
      ).mapN(GroupInfo.apply)
  }

  case object GroupChangeInfoSerializer extends ValidationSerializer[GroupChangeInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, GroupChangeInfo] =
      (
        (js \ "newGroup").required[GroupInfo](finalMessages("missing-new-group") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "changeType").required(GroupChangeType, finalMessages("missing-change-type") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "userId").required[String](finalMessages("missing-user-id") match {
          case Messages(_, _, message) =>
            message
        }),
        (js \ "oldGroup").optional[GroupInfo],
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "created").default[String](DateTime.now.getMillis.toString)
      ).mapN(GroupChangeInfo.apply)
  }
}
