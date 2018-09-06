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

import cats.scalatest.{EitherMatchers, EitherValues}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mockito.MockitoSugar
import vinyldns.core.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.{DataAccessor, DataStore, DataStoreConfig, RepositoriesConfig}

import scala.collection.JavaConverters._

class DataStoreLoaderSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with EitherValues
    with EitherMatchers {

  val placeholderConfig: Config = ConfigFactory.parseMap(Map[String, String]().asJava)
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
    enabled
  )

  val allDisabledReposConfig = RepositoriesConfig(
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )

  val goodConfig = DataStoreConfig(
    "vinyldns.api.repository.MockDataStoreProvider",
    placeholderConfig,
    allEnabledReposConfig)

  "loadAll" should {
    "return a data accessor for valid config for one datastore" in {
      val loadCall = DataStoreLoader.loadAll(List(goodConfig))
      noException should be thrownBy loadCall.unsafeRunSync()
    }

    "return a data accessor for valid config for multiple datastores" in {
      val config1 = DataStoreConfig(
        "vinyldns.api.repository.MockDataStoreProvider",
        placeholderConfig,
        allEnabledReposConfig.copy(user = None))

      val config2 = DataStoreConfig(
        "vinyldns.api.repository.AlternateMockDataStoreProvider",
        placeholderConfig,
        allDisabledReposConfig.copy(user = enabled))

      val loadCall = DataStoreLoader.loadAll(List(config1, config2))
      noException should be thrownBy loadCall.unsafeRunSync()
    }

    "throw an exception if getValidatedConfigs fails" in {
      val loadCall =
        DataStoreLoader.loadAll(List(goodConfig.copy(repositories = allDisabledReposConfig)))
      val thrown = the[DataStoreStartupError] thrownBy loadCall.unsafeRunSync()
      thrown.msg should include("Config validation error")
    }

    "throw an exception if load fails" in {
      val loadCall = DataStoreLoader.loadAll(
        List(goodConfig.copy(className = "vinyldns.api.repository.FailDataStoreProvider")))
      val thrown = the[RuntimeException] thrownBy loadCall.unsafeRunSync()
      thrown.getMessage should include("ruh roh")
    }
  }

  "getValidatedConfigs" should {
    "return all non-empty configs if all repos are defined once" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig.copy(zone = None))
      val config2 =
        goodConfig.copy(repositories = allDisabledReposConfig.copy(zone = enabled))
      val emptyConfig = goodConfig.copy(repositories = allDisabledReposConfig)

      val outcome = DataStoreLoader.getValidatedConfigs(List(config1, config2, emptyConfig))
      outcome.value should contain(config1)
      outcome.value should contain(config2)
      outcome.value should not contain emptyConfig
    }
    "fail if any config is defined multiple times" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig)
      val config2 = goodConfig.copy(
        repositories = allDisabledReposConfig
          .copy(membership = enabled, group = enabled))

      val outcome = DataStoreLoader.getValidatedConfigs(List(config1, config2))
      val message = outcome.leftValue.getMessage
      message should include("May not have more than one repo of type membership")
      message should include("May not have more than one repo of type group")
    }
    "fail if any config is defined zero times" in {
      val config = goodConfig.copy(repositories = allEnabledReposConfig.copy(user = None))

      val outcome = DataStoreLoader.getValidatedConfigs(List(config))
      val message = outcome.leftValue.getMessage
      message shouldBe "Config validation error: Must have one repo of type user"
    }
  }

  "load" should {
    "succeed if properly configured" in {
      noException should be thrownBy DataStoreLoader.load(goodConfig).unsafeRunSync()
    }
    "fail if it cant find the class defined" in {
      val config = goodConfig.copy(className = "something.undefined")

      val call = DataStoreLoader.load(config)
      a[java.lang.ClassNotFoundException] should be thrownBy call.unsafeRunSync()
    }
    "fail the defined class is not a DataStoreProvider" in {
      val config =
        goodConfig.copy(className = "vinyldns.api.repository.DataStoreLoaderSpec")

      val call = DataStoreLoader.load(config)
      a[java.lang.ClassCastException] should be thrownBy call.unsafeRunSync()
    }
    "fail if the providers load method fails" in {
      val config =
        goodConfig.copy(className = "vinyldns.api.repository.FailDataStoreProvider")

      val call = DataStoreLoader.load(config)
      a[RuntimeException] should be thrownBy call.unsafeRunSync()
    }
  }

  "generateAccessor" should {
    val user = mock[UserRepository]
    val group = mock[GroupRepository]
    val membership = mock[MembershipRepository]
    val groupChange = mock[GroupChangeRepository]
    val recordSet = mock[RecordSetRepository]
    val recordChange = mock[RecordChangeRepository]
    val zoneChange = mock[ZoneChangeRepository]
    val zone = mock[ZoneRepository]
    val batchChange = mock[BatchChangeRepository]

    val config1 = DataStoreConfig(
      "store1",
      placeholderConfig,
      allDisabledReposConfig.copy(user = enabled, group = enabled, groupChange = enabled))

    val config2 = DataStoreConfig(
      "store2",
      placeholderConfig,
      allDisabledReposConfig.copy(
        membership = enabled,
        recordSet = enabled,
        recordChange = enabled,
        zoneChange = enabled,
        zone = enabled,
        batchChange = enabled)
    )
    val store1 = new DataStore(Some(user), Some(group), None, Some(groupChange))

    val store2 = new DataStore(
      None,
      None,
      Some(membership),
      None,
      Some(recordSet),
      Some(recordChange),
      Some(zoneChange),
      Some(zone),
      Some(batchChange))

    "combine DataStores into a single accessor" in {
      val outcome = DataStoreLoader.generateAccessor(List((config1, store1), (config2, store2)))
      outcome.value shouldBe DataAccessor(
        user,
        group,
        membership,
        groupChange,
        recordSet,
        recordChange,
        zoneChange,
        zone,
        batchChange)
    }

    "allow DataStores to return extra repos" in {
      val dataStore = new DataStore(
        Some(user),
        Some(group),
        Some(membership),
        Some(groupChange),
        Some(recordSet),
        Some(recordChange),
        Some(zoneChange),
        Some(zone),
        Some(batchChange))

      val outcome =
        DataStoreLoader.generateAccessor(List((config1, dataStore), (config2, dataStore)))
      outcome should be(right)
    }
    "fail if repositories configured on are not returned by external load" in {
      val empty = new DataStore()
      val outcome =
        DataStoreLoader.generateAccessor(List((config1, empty), (config2, store2)))
      outcome.leftValue shouldBe a[DataStoreStartupError]

      val message = outcome.leftValue.msg
      List("user", "group", "groupChange").foreach { repo =>
        message should include(s"Repo $repo was not returned by configured database: store1")
      }
      List("membership", "recordSet", "recordChange", "zone", "batchChange").foreach { repo =>
        (message should not).include(s"Repo $repo was not returned by configured database: store1")
      }
    }
    "return an DataStoreInitializationError if a repository is undefined" in {
      val store = new DataStore(Some(user), Some(group), None, Some(groupChange))

      val outcome =
        DataStoreLoader.generateAccessor(List((goodConfig, store)))
      outcome.leftValue shouldBe a[DataStoreStartupError]
    }
  }
}
