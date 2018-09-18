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
import org.scalatest.mockito.MockitoSugar
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}

class MockDataStoreProvider extends DataStoreProvider with MockitoSugar {

  def load(config: DataStoreConfig, crypto: CryptoAlgebra): IO[DataStore] = {
    val repoConfig = config.repositories

    val user = repoConfig.user.map(_ => mock[UserRepository])
    val group = repoConfig.group.map(_ => mock[GroupRepository])
    val membership = repoConfig.membership.map(_ => mock[MembershipRepository])
    val groupChange = repoConfig.groupChange.map(_ => mock[GroupChangeRepository])
    val recordSet = repoConfig.recordSet.map(_ => mock[RecordSetRepository])
    val recordChange = repoConfig.recordChange.map(_ => mock[RecordChangeRepository])
    val zoneChange = repoConfig.zoneChange.map(_ => mock[ZoneChangeRepository])
    val zone = repoConfig.zone.map(_ => mock[ZoneRepository])
    val batchChange = repoConfig.batchChange.map(_ => mock[BatchChangeRepository])

    IO.pure(
      DataStore(
        user,
        group,
        membership,
        groupChange,
        recordSet,
        recordChange,
        zoneChange,
        zone,
        batchChange)
    )
  }

}

class AlternateMockDataStoreProvider extends MockDataStoreProvider

class FailDataStoreProvider extends DataStoreProvider {
  def load(config: DataStoreConfig, crypto: CryptoAlgebra): IO[DataStore] =
    IO.raiseError(new RuntimeException("ruh roh"))
}
