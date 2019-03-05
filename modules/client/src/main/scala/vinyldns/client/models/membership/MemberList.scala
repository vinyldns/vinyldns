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

package vinyldns.client.models.membership

import vinyldns.client.models.user.User
import upickle.default.{macroRW, ReadWriter => RW}
import upickle.default._

case class MemberList(
    members: List[User],
    nextId: Option[String] = None,
    startFrom: Option[String] = None,
    maxItems: Int
)

object MemberList {
  implicit val rw: RW[MemberList] = macroRW

  // uPickle by default treats empty options as empty arrays, this has it use None
  implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }

  implicit def OptionReader[T: Reader]: Reader[Option[T]] =
    implicitly[Reader[T]].mapNulls {
      case null => None
      case x => Some(x)
    }
}
