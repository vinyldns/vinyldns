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

import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.api.repository.RepositoryName.RepositoryName

import scala.reflect.ClassTag

class DataStore(
    userRepository: Option[UserRepository] = None,
    groupRepository: Option[GroupRepository] = None,
    membershipRepository: Option[MembershipRepository] = None,
    groupChangeRepository: Option[GroupChangeRepository] = None,
    recordSetRepository: Option[RecordSetRepository] = None,
    recordChangeRepository: Option[RecordChangeRepository] = None,
    zoneChangeRepository: Option[ZoneChangeRepository] = None,
    zoneRepository: Option[ZoneRepository] = None,
    batchChangeRepository: Option[BatchChangeRepository] = None
) {

  lazy val dataStoreMap: Map[RepositoryName, Repository] =
    List(
      userRepository.map(RepositoryName.user -> _),
      groupRepository.map(RepositoryName.group -> _),
      membershipRepository.map(RepositoryName.membership -> _),
      groupChangeRepository.map(RepositoryName.groupChange -> _),
      recordSetRepository.map(RepositoryName.recordSet -> _),
      recordChangeRepository.map(RepositoryName.recordChange -> _),
      zoneChangeRepository.map(RepositoryName.zoneChange -> _),
      zoneRepository.map(RepositoryName.zone -> _),
      batchChangeRepository.map(RepositoryName.batchChange -> _)
    ).flatten.toMap

  def keys: Set[RepositoryName] = dataStoreMap.keySet

  def get[A <: Repository: ClassTag](name: RepositoryName): Option[A] =
    dataStoreMap.get(name).flatMap {
      case a: A => Some(a)
      case _ => None
    }
}
