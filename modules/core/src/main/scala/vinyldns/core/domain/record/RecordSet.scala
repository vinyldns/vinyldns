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

package vinyldns.core.domain.record

import java.util.UUID

import org.joda.time.DateTime

object RecordType extends Enumeration {
  type RecordType = Value
  val A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF, UNKNOWN = Value
}

object RecordSetStatus extends Enumeration {
  type RecordSetStatus = Value
  val Active, Inactive, Pending, PendingUpdate, PendingDelete = Value
}

import RecordSetStatus._
import RecordType._

case class RecordSet(
    zoneId: String,
    name: String,
    typ: RecordType,
    ttl: Long,
    status: RecordSetStatus,
    created: DateTime,
    updated: Option[DateTime] = None,
    records: List[RecordData] = List.empty,
    id: String = UUID.randomUUID().toString,
    account: String = "system") {

  def isPending: Boolean =
    (status == RecordSetStatus.Pending
      || status == RecordSetStatus.PendingUpdate
      || status == RecordSetStatus.PendingDelete)

  override def toString: String = {

    val sb = new StringBuilder
    sb.append("RecordSet: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("name=\"").append(name).append("\"; ")
    sb.append("type=\"").append(typ.toString).append("\"; ")
    sb.append("ttl=\"").append(ttl.toString).append("\"; ")
    sb.append("account=\"").append(account).append("\"; ")
    sb.append("status=\"").append(status.toString).append("\"; ")
    sb.append("records=\"").append(records.toString).append("\"")
    sb.append("]")

    sb.toString
  }
}
