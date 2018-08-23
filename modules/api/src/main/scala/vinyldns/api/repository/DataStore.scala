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

case class DataStore(
    userRepository: Option[UserRepository] = None,
    groupRepository: Option[GroupRepository] = None,
    membershipRepository: Option[MembershipRepository] = None,
    groupChangeRepository: Option[GroupChangeRepository] = None,
    recordSetRepository: Option[RecordSetRepository] = None,
    recordChangeRepository: Option[RecordChangeRepository] = None,
    zoneChangeRepository: Option[ZoneChangeRepository] = None,
    zoneRepository: Option[ZoneRepository] = None,
    batchChangeRepository: Option[BatchChangeRepository] = None
)

case class DataAccessor(
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    membershipRepository: MembershipRepository,
    groupChangeRepository: GroupChangeRepository,
    recordSetRepository: RecordSetRepository,
    recordChangeRepository: RecordChangeRepository,
    zoneChangeRepository: ZoneChangeRepository,
    zoneRepository: ZoneRepository,
    batchChangeRepository: BatchChangeRepository
)

case class DataStoreStartupError(msg: String) extends Throwable(msg)
