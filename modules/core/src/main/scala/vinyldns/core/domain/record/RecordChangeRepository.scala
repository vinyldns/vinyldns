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
import scalikejdbc.DB
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.repository.Repository

trait RecordChangeRepository extends Repository {

  def save(db: DB, changeSet: ChangeSet): IO[ChangeSet]

  def listRecordSetChanges(
      zoneId: Option[String],
      startFrom: Option[Int] = None,
      maxItems: Int = 100,
      fqdn: Option[String] = None,
      recordType: Option[RecordType] = None
  ): IO[ListRecordSetChangesResults]

  def getRecordSetChange(changeId: String): IO[Option[RecordSetChange]]

  def listFailedRecordSetChanges(zoneId: Option[String],
                                  maxItems: Int = 100,
                                 startFrom: Int = 0
                                ): IO[ListFailedRecordSetChangesResults]

}
