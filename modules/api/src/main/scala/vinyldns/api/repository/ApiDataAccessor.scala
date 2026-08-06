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

import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership.{GroupChangeRepository, GroupRepository, MembershipRepository, UserRepository}
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{GenerateZoneRepository, ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.DataAccessor

final case class ApiDataAccessor(
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    membershipRepository: MembershipRepository,
    groupChangeRepository: GroupChangeRepository,
    recordSetRepository: RecordSetRepository,
    recordChangeRepository: RecordChangeRepository,
    recordSetCacheRepository: RecordSetCacheRepository,
    zoneChangeRepository: ZoneChangeRepository,
    zoneRepository: ZoneRepository,
    batchChangeRepository: BatchChangeRepository,
    generateZoneRepository: GenerateZoneRepository
) extends DataAccessor
