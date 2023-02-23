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

package vinyldns.core.domain.membership
import java.util.UUID

import java.time.Instant

sealed abstract class UserChangeType(val value: String)
object UserChangeType {
  case object Create extends UserChangeType("create")
  case object Update extends UserChangeType("update")

  case class UnknownUserChangeType(value: String) extends Throwable(s"Unknown change type $value")

  def fromString(value: String): Either[UnknownUserChangeType, UserChangeType] =
    value.toLowerCase match {
      case UserChangeType.Create.value => Right(Create)
      case UserChangeType.Update.value => Right(Update)
      case other => Left(UnknownUserChangeType(other))
    }

  def fromChange[A <: UserChange](change: A): UserChangeType = change match {
    case UserChange.CreateUser(_, _, _, _) => Create
    case UserChange.UpdateUser(_, _, _, _, _) => Update
  }
}

/**
  * The nice thing about this is that we cannot have a UserChange in an invalid state.  For example,
  * we cannot have a UserChange(ChangeType.Update, oldUser=None)
  */
sealed trait UserChange {
  def id: String
  def newUser: User
  def madeByUserId: String
  def created: Instant
}
object UserChange {
  final case class CreateUser(
      newUser: User,
      madeByUserId: String,
      created: Instant,
      id: String = UUID.randomUUID().toString
  ) extends UserChange
  final case class UpdateUser(
      newUser: User,
      madeByUserId: String,
      created: Instant,
      oldUser: User,
      id: String = UUID.randomUUID().toString
  ) extends UserChange

  def apply(
      id: String,
      newUser: User,
      madeByUserId: String,
      created: Instant,
      oldUser: Option[User],
      changeType: UserChangeType
  ): Either[IllegalArgumentException, UserChange] =
    changeType match {
      case UserChangeType.Create =>
        Right(CreateUser(newUser, madeByUserId, created, id))
      case UserChangeType.Update =>
        oldUser
          .map(u => Right(UpdateUser(newUser, madeByUserId, created, u, id)))
          .getOrElse(
            Left(
              new IllegalArgumentException(
                s"Unable to create update user change, old user is not defined"
              )
            )
          )
    }
}
