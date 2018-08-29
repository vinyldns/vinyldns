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

package vinyldns.api.domain.zone

import cats.effect._
import vinyldns.api.repository.Repository
import vinyldns.api.repository.dynamodb.DynamoDBZoneChangeRepository

case class ListZoneChangesResults(
    items: List[ZoneChange] = List[ZoneChange](),
    nextId: Option[String] = None,
    startFrom: Option[String] = None,
    maxItems: Int = 100)

trait ZoneChangeRepository extends Repository {

  def save(zoneChange: ZoneChange): IO[ZoneChange]

  def getPending(zoneId: String): IO[List[ZoneChange]]

  def getAllPendingZoneIds(): IO[List[String]]

  def listZoneChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100): IO[ListZoneChangesResults]
}

object ZoneChangeRepository {
  def apply(): ZoneChangeRepository =
    DynamoDBZoneChangeRepository()

}
