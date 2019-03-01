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

package vinyldns.v2client.models.membership

import upickle.default.{macroRW, ReadWriter => RW}
import upickle.default._
import vinyldns.v2client.models.Id

case class Group(
    name: String = "",
    email: String = "",
    description: Option[String] = None,
    id: Option[String] = None,
    created: Option[String] = None,
    members: Option[Seq[Id]] = None,
    admins: Option[Seq[Id]] = None)

object Group {
  implicit val rw: RW[Group] = macroRW

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
