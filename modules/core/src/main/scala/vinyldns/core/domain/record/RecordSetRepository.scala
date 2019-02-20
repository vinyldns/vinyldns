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

import cats.effect._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.repository.Repository

trait RecordSetRepository extends Repository {

  def apply(changeSet: ChangeSet): IO[ChangeSet]

  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): IO[ListRecordSetResults]

  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]]

  def getRecordSet(zoneId: String, recordSetId: String): IO[Option[RecordSet]]

  def getRecordSetCount(zoneId: String): IO[Int]

  def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]]

  def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]]
}
