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

import cats.effect.IO
import vinyldns.api.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.ZoneChangeRepository
import vinyldns.api.repository.{DataStore, DataStoreConfig, DataStoreProvider}

// TODO load dynamically
class DynamoDbDataStoreProvider extends DataStoreProvider {

  def load(config: DataStoreConfig): IO[DataStore] =
    for {
      userRepo <- IO(UserRepository())
      groupRepo <- IO(GroupRepository())
      membershipRepo <- IO(MembershipRepository())
      groupChangeRepo <- IO(GroupChangeRepository())
      recordSetRepo <- IO(RecordSetRepository())
      recordChangeRepo <- IO(RecordChangeRepository())
      zoneChangeRepo <- IO(ZoneChangeRepository())
    } yield
      DataStore(
        Some(userRepo),
        Some(groupRepo),
        Some(membershipRepo),
        Some(groupChangeRepo),
        Some(recordSetRepo),
        Some(recordChangeRepo),
        Some(zoneChangeRepo),
        None,
        None)
}
