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

package vinyldns.api.repository.dynamodb

import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.api.repository.DataStore

// TODO add config parameter, load based on that
class DynamoDbDataStore() extends DataStore {
  val userRepository: Option[UserRepository] = Some(UserRepository())
  val groupRepository: Option[GroupRepository] = Some(GroupRepository())
  val membershipRepository: Option[MembershipRepository] = Some(MembershipRepository())
  val groupChangeRepository: Option[GroupChangeRepository] = Some(GroupChangeRepository())
  val recordSetRepository: Option[RecordSetRepository] = Some(RecordSetRepository())
  val recordChangeRepository: Option[RecordChangeRepository] = Some(RecordChangeRepository())
  val zoneChangeRepository: Option[ZoneChangeRepository] = Some(ZoneChangeRepository())
  val zoneRepository: Option[ZoneRepository] = None
  val batchChangeRepository: Option[BatchChangeRepository] = None
}
