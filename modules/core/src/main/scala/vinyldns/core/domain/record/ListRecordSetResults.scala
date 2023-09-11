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

import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort

object NameSort extends Enumeration {
  type NameSort = Value
  val ASC, DESC = Value

  def find(value: String): Value = value.toUpperCase match {
    case "DESC" => NameSort.DESC
    case _ => NameSort.ASC

  }
}

object RecordTypeSort extends Enumeration {
  type RecordTypeSort = Value
  val ASC, DESC, NONE = Value

  def find(value: String): Value = value.toUpperCase match {
    case "DESC" => RecordTypeSort.DESC
    case "ASC" => RecordTypeSort.ASC
    case _ => RecordTypeSort.NONE

  }
}

case class ListRecordSetResults(
                                 recordSets: List[RecordSet] = List[RecordSet](),
                                 nextId: Option[String] = None,
                                 startFrom: Option[String] = None,
                                 maxItems: Option[Int] = None,
                                 recordNameFilter: Option[String] = None,
                                 recordTypeFilter: Option[Set[RecordType]] = None,
                                 recordOwnerGroupFilter: Option[String] = None,
                                 nameSort: NameSort,
                                 recordTypeSort: RecordTypeSort
                               )
