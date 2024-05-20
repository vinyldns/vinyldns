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

package vinyldns.api.repository

import cats.data.ValidatedNel
import cats.implicits._
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.{DataAccessorProvider, DataStore, DataStoreConfig}
import vinyldns.core.repository.DataStoreLoader.getRepoOf
import vinyldns.core.repository.RepositoryName._
import vinyldns.core.task.TaskRepository

object ApiDataAccessorProvider extends DataAccessorProvider[ApiDataAccessor] {
  def repoNames: List[RepositoryName] =
    List(
      user,
      group,
      membership,
      groupChange,
      recordSet,
      recordChange,
      recordSetCache,
      zoneChange,
      zone,
      batchChange
    )

  def create(
      dataStores: List[(DataStoreConfig, DataStore)]
  ): ValidatedNel[String, ApiDataAccessor] =
    (
      getRepoOf[UserRepository](dataStores, user),
      getRepoOf[GroupRepository](dataStores, group),
      getRepoOf[MembershipRepository](dataStores, membership),
      getRepoOf[GroupChangeRepository](dataStores, groupChange),
      getRepoOf[RecordSetRepository](dataStores, recordSet),
      getRepoOf[RecordChangeRepository](dataStores, recordChange),
      getRepoOf[RecordSetCacheRepository](dataStores, recordSetCache),
      getRepoOf[ZoneChangeRepository](dataStores, zoneChange),
      getRepoOf[ZoneRepository](dataStores, zone),
      getRepoOf[BatchChangeRepository](dataStores, batchChange),
      getRepoOf[TaskRepository](dataStores, task)
    ).mapN(ApiDataAccessor)
}
