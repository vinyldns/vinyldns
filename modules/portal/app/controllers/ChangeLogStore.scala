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

package controllers

import models.UserAccount
import org.joda.time.DateTime

import scala.util.Try

// $COVERAGE-OFF$
object ChangeType {
  def apply(s: String): ChangeType =
    s.toLowerCase match {
      case "created" => Create
      case "updated" => Update
      case "deleted" => Delete
      case _ => throw new IllegalArgumentException(s"$s is not a valid change type")
    }
}

sealed trait ChangeType
case object Create extends ChangeType {
  override val toString = "created"
}
case object Update extends ChangeType {
  override val toString = "updated"
}
case object Delete extends ChangeType {
  override val toString = "deleted"
}

sealed trait ChangeLogMessage
final case class UserChangeMessage(
    userId: String,
    username: String,
    timeStamp: DateTime,
    changeType: ChangeType,
    updatedUser: UserAccount,
    previousUser: Option[UserAccount])
    extends ChangeLogMessage

trait ChangeLogStore {
  def log(change: ChangeLogMessage): Try[ChangeLogMessage]
}
// $COVERAGE-ON$
