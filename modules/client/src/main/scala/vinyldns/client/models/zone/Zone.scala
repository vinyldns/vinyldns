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

package vinyldns.client.models.zone

import vinyldns.client.models.OptionRW
import upickle.default.{ReadWriter, macroRW}

trait BasicZoneInfo {
  def name: String
  def email: String
  def adminGroupId: String
  def connection: Option[ZoneConnection]
  def transferConnection: Option[ZoneConnection]
}

case class Zone(
    id: String,
    name: String,
    email: String,
    adminGroupId: String,
    adminGroupName: String,
    status: String,
    created: String,
    account: String,
    shared: Boolean,
    acl: List[ACLRule],
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None
) extends BasicZoneInfo

object Zone extends OptionRW {
  implicit val rw: ReadWriter[Zone] = macroRW
}

case class ZoneCreateInfo(
    name: String = "",
    email: String = "",
    adminGroupId: String = "",
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None
) extends BasicZoneInfo {

  def withNewConnectionKeyName(value: String): ZoneCreateInfo = {
    val connection = this.connection match {
      case Some(c) => c.copy(keyName = value, name = value)
      case None => ZoneConnection(keyName = value, name = value)
    }
    this.copy(connection = Some(connection))
  }

  def withNewConnectionKey(value: String): ZoneCreateInfo = {
    val connection = this.connection match {
      case Some(c) => c.copy(key = value)
      case None => ZoneConnection(key = value)
    }
    this.copy(connection = Some(connection))
  }

  def withNewConnectionServer(value: String): ZoneCreateInfo = {
    val connection = this.connection match {
      case Some(c) => c.copy(primaryServer = value)
      case None => ZoneConnection(primaryServer = value)
    }
    this.copy(connection = Some(connection))
  }

  def withNewTransferKeyName(value: String): ZoneCreateInfo = {
    val connection = this.transferConnection match {
      case Some(c) => c.copy(keyName = value, name = value)
      case None => ZoneConnection(keyName = value, name = value)
    }
    this.copy(transferConnection = Some(connection))
  }

  def withNewTransferKey(value: String): ZoneCreateInfo = {
    val connection = this.transferConnection match {
      case Some(c) => c.copy(key = value)
      case None => ZoneConnection(key = value)
    }
    this.copy(transferConnection = Some(connection))
  }

  def withNewTransferServer(value: String): ZoneCreateInfo = {
    val connection = this.transferConnection match {
      case Some(c) => c.copy(primaryServer = value)
      case None => ZoneConnection(primaryServer = value)
    }
    this.copy(transferConnection = Some(connection))
  }
}

object ZoneCreateInfo extends OptionRW {

  implicit val rw: ReadWriter[ZoneCreateInfo] = macroRW
}

case class ZoneConnection(
    name: String = "",
    primaryServer: String = "",
    keyName: String = "",
    key: String = ""
)

object ZoneConnection {
  implicit val rw: ReadWriter[ZoneConnection] = macroRW
}
