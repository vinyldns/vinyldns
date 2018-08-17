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

package vinyldns.api.repository.mysql

import com.typesafe.config.Config
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

// TODO placeholder for mysql loader
class MySqlDataStore(config: Config) extends DataStore {

  val settings = config.getConfig("settings")
  val instance = new VinylDNSJDBC(settings)

  // TODO need to load these dynamically based on the config
  val userRepository: Option[UserRepository] = None
  val groupRepository: Option[GroupRepository] = None
  val membershipRepository: Option[MembershipRepository] = None
  val groupChangeRepository: Option[GroupChangeRepository] = None
  val recordSetRepository: Option[RecordSetRepository] = None
  val recordChangeRepository: Option[RecordChangeRepository] = None
  val zoneChangeRepository: Option[ZoneChangeRepository] = None
  val zoneRepository: Option[ZoneRepository] = Some(instance.zoneRepository)
  val batchChangeRepository: Option[BatchChangeRepository] = Some(instance.batchChangeRepository)

}
