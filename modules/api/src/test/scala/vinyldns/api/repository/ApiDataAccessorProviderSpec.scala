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

import cats.scalatest.ValidatedMatchers
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.core.domain.record.{
  RecordChangeRepository,
  RecordSetCacheRepository,
  RecordSetRepository
}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.{DataStore, DataStoreConfig, RepositoriesConfig}

class ApiDataAccessorProviderSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ValidatedMatchers {

  "ApiDataAccessorProvider" should {

    val placeholderConfig: Config = ConfigFactory.parseString("{}")
    val enabled = Some(placeholderConfig)

    val allEnabledReposConfig = RepositoriesConfig(
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      enabled,
      None,
      enabled
    )

    val user = mock[UserRepository]
    val group = mock[GroupRepository]
    val membership = mock[MembershipRepository]
    val groupChange = mock[GroupChangeRepository]
    val recordSet = mock[RecordSetRepository]
    val recordChange = mock[RecordChangeRepository]
    val recordSetCache = mock[RecordSetCacheRepository]

    val zoneChange = mock[ZoneChangeRepository]
    val zone = mock[ZoneRepository]
    val batchChange = mock[BatchChangeRepository]

    "create an accessor if all repos can be pulled from the right datastore" in {
      val enabledConfig =
        DataStoreConfig("some.datastore", placeholderConfig, allEnabledReposConfig)

      val enabledDataStore = DataStore(
        Some(user),
        Some(group),
        Some(membership),
        Some(groupChange),
        Some(recordSet),
        Some(recordChange),
        Some(recordSetCache),
        Some(zoneChange),
        Some(zone),
        Some(batchChange),
        None
      ) // API doesn't use Task repository

      ApiDataAccessorProvider.create(List((enabledConfig, enabledDataStore))) should be(valid)
    }
    "error if a required repo is not configured" in {
      val enabledConfig =
        DataStoreConfig(
          "some.datastore",
          placeholderConfig,
          allEnabledReposConfig.copy(user = None)
        )

      val store = DataStore(
        Some(user),
        Some(group),
        Some(membership),
        Some(groupChange),
        Some(recordSet),
        Some(recordChange),
        Some(recordSetCache),
        Some(zoneChange),
        Some(zone),
        Some(batchChange)
      )

      ApiDataAccessorProvider.create(List((enabledConfig, store))) should be(invalid)
    }
    "error if a repo was not properly created" in {
      val enabledConfig =
        DataStoreConfig("some.datastore", placeholderConfig, allEnabledReposConfig)

      val store = DataStore(
        Some(user),
        Some(group),
        Some(membership),
        Some(groupChange),
        Some(recordSet),
        Some(recordChange),
        Some(recordSetCache),
        Some(zoneChange),
        None,
        None
      ) // API doesn't use Task repository

      ApiDataAccessorProvider.create(List((enabledConfig, store))) should be(invalid)
    }
  }

}
