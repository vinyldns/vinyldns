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

package vinyldns.api.domain.record

import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.repository.dynamodb.DynamoDBRecordSetRepository

import scala.concurrent.Future

case class ListRecordSetResults(
    recordSets: List[RecordSet] = List[RecordSet](),
    nextId: Option[String] = None,
    startFrom: Option[String] = None,
    maxItems: Option[Int] = None,
    recordNameFilter: Option[String] = None)

trait RecordSetRepository {

  def apply(changeSet: ChangeSet): Future[ChangeSet]

  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): Future[ListRecordSetResults]

  def getRecordSets(zoneId: String, name: String, typ: RecordType): Future[List[RecordSet]]

  def getRecordSet(zoneId: String, recordSetId: String): Future[Option[RecordSet]]

  def getRecordSetCount(zoneId: String): Future[Int]

  def getRecordSetsByName(zoneId: String, name: String): Future[List[RecordSet]]
}

object RecordSetRepository {

  def apply(): RecordSetRepository =
    DynamoDBRecordSetRepository()
}
