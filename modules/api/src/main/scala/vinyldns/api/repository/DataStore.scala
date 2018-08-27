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
import vinyldns.api.domain.membership.{GroupChangeRepository, MembershipRepository}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.ZoneChangeRepository
import vinyldns.api.domain.membership.{GroupRepository, UserRepository}
import vinyldns.api.domain.zone.ZoneRepository
import vinyldns.api.repository.RepositoryName.RepositoryName

import scala.reflect.ClassTag

class DataStore(
    val userRepository: Option[UserRepository] = None,
    val groupRepository: Option[GroupRepository] = None,
    val membershipRepository: Option[MembershipRepository] = None,
    val groupChangeRepository: Option[GroupChangeRepository] = None,
    val recordSetRepository: Option[RecordSetRepository] = None,
    val recordChangeRepository: Option[RecordChangeRepository] = None,
    val zoneChangeRepository: Option[ZoneChangeRepository] = None,
    val zoneRepository: Option[ZoneRepository] = None,
    val batchChangeRepository: Option[BatchChangeRepository] = None
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

final case class DataAccessor(
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    membershipRepository: MembershipRepository,
    groupChangeRepository: GroupChangeRepository,
    recordSetRepository: RecordSetRepository,
    recordChangeRepository: RecordChangeRepository,
    zoneChangeRepository: ZoneChangeRepository,
    zoneRepository: ZoneRepository,
    batchChangeRepository: BatchChangeRepository)

case class DataStoreStartupError(msg: String) extends Throwable(msg)
