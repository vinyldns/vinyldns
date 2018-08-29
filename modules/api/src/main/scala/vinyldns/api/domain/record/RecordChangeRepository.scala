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

import cats.effect._
import vinyldns.api.repository.Repository
import vinyldns.api.repository.dynamodb.DynamoDBRecordChangeRepository

case class ListRecordSetChangesResults(
    items: List[RecordSetChange] = List[RecordSetChange](),
    nextId: Option[String] = None,
    startFrom: Option[String] = None,
    maxItems: Int = 100)

trait RecordChangeRepository extends Repository {

  def save(changeSet: ChangeSet): IO[ChangeSet]

  def getPendingChangeSets(zoneId: String): IO[List[ChangeSet]]

  def getChanges(zoneId: String): IO[List[ChangeSet]]

  def listRecordSetChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100): IO[ListRecordSetChangesResults]

  def getRecordSetChange(zoneId: String, changeId: String): IO[Option[RecordSetChange]]

  def getAllPendingZoneIds(): IO[List[String]]
}

object RecordChangeRepository {

  def apply(): RecordChangeRepository =
    DynamoDBRecordChangeRepository()
}
