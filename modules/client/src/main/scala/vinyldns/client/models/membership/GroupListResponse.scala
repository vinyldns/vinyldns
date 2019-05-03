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

import upickle.default.{ReadWriter, macroRW}
import vinyldns.client.models.Pagination.PagingKey
import vinyldns.client.models.{OptionRW, PagingKeyRW}

case class GroupListResponse(
    groups: List[GroupResponse],
    maxItems: Int,
    startFrom: PagingKey = None,
    nextId: PagingKey = None,
    groupNameFilter: Option[String] = None)

object GroupListResponse extends OptionRW with PagingKeyRW {
  implicit val rw: ReadWriter[GroupListResponse] = macroRW
}
