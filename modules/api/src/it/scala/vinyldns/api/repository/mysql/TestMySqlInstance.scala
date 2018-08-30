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

import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.zone.ZoneRepository
import vinyldns.api.repository.DataStore
import vinyldns.api.repository.RepositoryName._

object TestMySqlInstance {
  lazy val instance: DataStore =
    new MySqlDataStoreProvider().load(VinylDNSConfig.mySqlConfig).unsafeRunSync()

  lazy val zoneRepository: ZoneRepository = instance.get[ZoneRepository](zone).get
  lazy val batchChangeRepository: BatchChangeRepository =
    instance.get[BatchChangeRepository](batchChange).get
}
