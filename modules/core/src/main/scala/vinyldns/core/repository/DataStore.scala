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

package vinyldns.core.repository

import cats.effect.IO
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{GenerateZoneRepository, ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.RepositoryName.RepositoryName
import vinyldns.core.health.HealthCheck.HealthCheck
import vinyldns.core.task.TaskRepository

import scala.reflect.ClassTag

class LoadedDataStore(
    val dataStore: DataStore,
    val shutdownHook: IO[Unit],
    val healthCheck: HealthCheck
)

object DataStore {
  def apply(
      userRepository: Option[UserRepository] = None,
      groupRepository: Option[GroupRepository] = None,
      membershipRepository: Option[MembershipRepository] = None,
      groupChangeRepository: Option[GroupChangeRepository] = None,
      recordSetRepository: Option[RecordSetRepository] = None,
      recordChangeRepository: Option[RecordChangeRepository] = None,
      recordSetCacheRepository: Option[RecordSetCacheRepository] = None,
      zoneChangeRepository: Option[ZoneChangeRepository] = None,
      zoneRepository: Option[ZoneRepository] = None,
      batchChangeRepository: Option[BatchChangeRepository] = None,
      userChangeRepository: Option[UserChangeRepository] = None,
      taskRepository: Option[TaskRepository] = None,
      generateZoneRepository: Option[GenerateZoneRepository] = None,
  ): DataStore =
    new DataStore(
      userRepository,
      groupRepository,
      membershipRepository,
      groupChangeRepository,
      recordSetRepository,
      recordChangeRepository,
      recordSetCacheRepository,
      zoneChangeRepository,
      zoneRepository,
      batchChangeRepository,
      userChangeRepository,
      taskRepository,
      generateZoneRepository
    )
}

class DataStore(
    userRepository: Option[UserRepository] = None,
    groupRepository: Option[GroupRepository] = None,
    membershipRepository: Option[MembershipRepository] = None,
    groupChangeRepository: Option[GroupChangeRepository] = None,
    recordSetRepository: Option[RecordSetRepository] = None,
    recordChangeRepository: Option[RecordChangeRepository] = None,
    recordSetCacheRepository: Option[RecordSetCacheRepository] = None,
    zoneChangeRepository: Option[ZoneChangeRepository] = None,
    zoneRepository: Option[ZoneRepository] = None,
    batchChangeRepository: Option[BatchChangeRepository] = None,
    userChangeRepository: Option[UserChangeRepository] = None,
    taskRepository: Option[TaskRepository] = None,
    generateZoneRepository: Option[GenerateZoneRepository] = None
) {

  lazy val dataStoreMap: Map[RepositoryName, Repository] =
    List(
      userRepository.map(RepositoryName.user -> _),
      groupRepository.map(RepositoryName.group -> _),
      membershipRepository.map(RepositoryName.membership -> _),
      groupChangeRepository.map(RepositoryName.groupChange -> _),
      recordSetRepository.map(RepositoryName.recordSet -> _),
      recordChangeRepository.map(RepositoryName.recordChange -> _),
      recordSetCacheRepository.map(RepositoryName.recordSetCache -> _),
      zoneChangeRepository.map(RepositoryName.zoneChange -> _),
      zoneRepository.map(RepositoryName.zone -> _),
      batchChangeRepository.map(RepositoryName.batchChange -> _),
      userChangeRepository.map(RepositoryName.userChange -> _),
      taskRepository.map(RepositoryName.task -> _),
      generateZoneRepository.map(RepositoryName.generateZone -> _)
    ).flatten.toMap

  def keys: Set[RepositoryName] = dataStoreMap.keySet

  def get[A <: Repository: ClassTag](name: RepositoryName): Option[A] =
    dataStoreMap.get(name).flatMap {
      case a: A => Some(a)
      case _ => None
    }
}
