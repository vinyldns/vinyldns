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

package vinyldns.api.backend.dns

import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.error.FailureReason
import vinyldns.api.backend.dns.DnsTsigUsage.{TransferOnly, UpdateAndTransfer, UpdateOnly}

sealed abstract class DnsTsigUsage(val humanized: String) {
  def forUpdates: Boolean = this match {
    case UpdateOnly | UpdateAndTransfer => true
    case _ => false
  }
  def forTransfers: Boolean = this match {
    case TransferOnly | UpdateAndTransfer => true
    case _ => false
  }
}
object DnsTsigUsage {
  final case object UpdateOnly extends DnsTsigUsage("update")
  final case object TransferOnly extends DnsTsigUsage("transfer")
  final case object UpdateAndTransfer extends DnsTsigUsage("always")
  final case object Never extends DnsTsigUsage("never")

  val Values: List[DnsTsigUsage] = List(UpdateOnly, TransferOnly, UpdateAndTransfer, Never)

  def fromString(value: String): Option[DnsTsigUsage] = Values.find(_.humanized == value)

  implicit val configReader: ConfigReader[DnsTsigUsage] = ConfigReader.fromString { value =>
    fromString(value).getOrElse(UpdateAndTransfer).asRight[FailureReason]
  }
}
