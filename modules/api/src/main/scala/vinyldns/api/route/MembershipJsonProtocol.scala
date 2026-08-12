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

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s._
import org.json4s.JsonDSL._
import vinyldns.api.domain.membership._
import vinyldns.core.domain.membership.{Group, GroupChangeType, GroupStatus, LockStatus, MembershipAccessStatus}

object MembershipJsonProtocol {
  final case class CreateGroupInput(
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId],
      admins: Set[UserId],
      membershipAccessStatus : Option[MembershipAccessStatus]
  )
  final case class UpdateGroupInput(
      id: String,
      name: String,
      email: String,
      description: Option[String],
      members: Set[UserId],
      admins: Set[UserId],
      membershipAccessStatus : Option[MembershipAccessStatus]
  )
  final case class MemberStatusGroupInput(
                                           pendingReviewMember: Set[String] = Set.empty,
                                           rejectedMember: Set[String] = Set.empty,
                                           approvedMember: Set[String] = Set.empty
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
        (js \ "name").required[String]("Missing Group.name"),
        (js \ "email").required[String]("Missing Group.email"),
        (js \ "description").optional[String],
        (js \ "members").required[Set[UserId]]("Missing Group.members"),
        (js \ "admins").required[Set[UserId]]("Missing Group.admins"),
        (js \ "membershipAccessStatus").optional[MembershipAccessStatus]
      ).mapN(CreateGroupInput.apply)
  }
  case object UpdateGroupInputSerializer extends ValidationSerializer[UpdateGroupInput] {
    override def fromJson(js: JValue): ValidatedNel[String, UpdateGroupInput] =
      (
        (js \ "id").required[String]("Missing Group.id"),
        (js \ "name").required[String]("Missing Group.name"),
        (js \ "email").required[String]("Missing Group.email"),
        (js \ "description").optional[String],
        (js \ "members").required[Set[UserId]]("Missing Group.members"),
        (js \ "admins").required[Set[UserId]]("Missing Group.admins"),
        (js \ "membershipAccessStatus").optional[MembershipAccessStatus]
      ).mapN(UpdateGroupInput.apply)
  }
  case object MemberStatusGroupInputSerializer extends ValidationSerializer[MemberStatusGroupInput] {
    override def fromJson(js: JValue): ValidatedNel[String, MemberStatusGroupInput] =
      (
        (js \ "members").default[Set[String]](Set.empty),
        (js \ "admins").default[Set[String]](Set.empty),
        (js \ "membershipAccessStatus").default[Set[String]](Set.empty)
        ).mapN(MemberStatusGroupInput.apply)
  }

  /**
    * This is unfortunate, but is necessary to support tests at least right now because of the way that Json
    * serialization works with Enum values.
    */
  case object GroupSerializer extends ValidationSerializer[Group] {
    override def fromJson(js: JValue): ValidatedNel[String, Group] =
      (
        (js \ "name").required[String]("Missing Group.name"),
        (js \ "email").required[String]("Missing Group.email"),
        (js \ "description").optional[String],
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "created").default[Instant](Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        (js \ "status").default(GroupStatus, GroupStatus.Active),
        (js \ "memberIds").default[Set[String]](Set.empty),
        (js \ "adminUserIds").default[Set[String]](Set.empty),
        (js \ "membershipAccessStatus").optional[MembershipAccessStatus]
      ).mapN(Group.apply)
  }

  case object GroupInfoSerializer extends ValidationSerializer[GroupInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, GroupInfo] = {
      (
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "name").required[String]("Missing Group.name"),
        (js \ "email").required[String]("Missing Group.email"),
        (js \ "description").optional[String],
        (js \ "created").default[Instant](Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        (js \ "status").default(GroupStatus, GroupStatus.Active),
        (js \ "members").default[Set[UserId]](Set.empty),
        (js \ "admins").default[Set[UserId]](Set.empty),
        (js \ "membershipAccessStatus").optional[MembershipAccessStatus]
      ).mapN(GroupInfo.apply)
    }

    override def toJson(gi: GroupInfo): JValue =
      ("id" -> gi.id) ~
      ("name" -> gi.name) ~
      ("email" -> gi.email) ~
      ("description" -> gi.description) ~
      ("created" -> Extraction.decompose(gi.created)) ~
      ("status" -> Extraction.decompose(gi.status)) ~
      ("members" -> Extraction.decompose(gi.members)) ~
      ("admins" -> Extraction.decompose(gi.admins)) ~
      ("membershipAccessStatus" -> Extraction.decompose(gi.membershipAccessStatus))
  }

  case object GroupChangeInfoSerializer extends ValidationSerializer[GroupChangeInfo] {
    override def fromJson(js: JValue): ValidatedNel[String, GroupChangeInfo] = {
      (
        (js \ "newGroup").required[GroupInfo]("Missing new group"),
        (js \ "changeType").required(GroupChangeType, "Missing change type"),
        (js \ "userId").required[String]("Missing userId"),
        (js \ "oldGroup").optional[GroupInfo],
        (js \ "id").default[String](UUID.randomUUID().toString),
        (js \ "created").default[Instant](Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        (js \ "userName").required[String]("Missing userName"),
        (js \ "groupChangeMessage").required[String]("Missing groupChangeMessage"),
      ).mapN(GroupChangeInfo.apply)
    }

    override def toJson(gci: GroupChangeInfo): JValue =
      ("newGroup" -> Extraction.decompose(gci.newGroup)) ~
      ("changeType" -> Extraction.decompose(gci.changeType)) ~
      ("userId" -> gci.userId) ~
      ("oldGroup" -> Extraction.decompose(gci.oldGroup)) ~
      ("id" -> gci.id) ~
      ("created" -> gci.created.toString) ~
      ("userName" -> gci.userName) ~
      ("groupChangeMessage" -> gci.groupChangeMessage)
  }
}
