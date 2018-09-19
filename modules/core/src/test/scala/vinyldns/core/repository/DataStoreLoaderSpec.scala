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

import cats.data._
import cats.implicits._
import cats.scalatest.{EitherMatchers, EitherValues, ValidatedMatchers}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership.UserRepository
import vinyldns.core.repository.RepositoryName._

import scala.collection.JavaConverters._

class DataStoreLoaderSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with EitherValues
    with EitherMatchers
    with ValidatedMatchers {

  val crypto: CryptoAlgebra = new NoOpCrypto()
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
    None,
    None
  )

  val goodConfig = DataStoreConfig(
    "vinyldns.core.repository.MockDataStoreProvider",
    placeholderConfig,
    allEnabledReposConfig)

  class TestDataAccessor extends DataAccessor
  object TestAccessorProvider extends DataAccessorProvider[TestDataAccessor] {
    override def repoNames: List[RepositoryName] = RepositoryName.values.toList

    override def create(
        responses: List[(DataStoreConfig, DataStore)]): ValidatedNel[String, TestDataAccessor] =
      new TestDataAccessor().validNel
  }
  object FailAccessorProvider extends DataAccessorProvider[TestDataAccessor] {
    override def repoNames: List[RepositoryName] = RepositoryName.values.toList

    override def create(
        responses: List[(DataStoreConfig, DataStore)]): ValidatedNel[String, TestDataAccessor] =
      "create failure".invalidNel[TestDataAccessor]
  }

  "loadAll" should {
    "return a data accessor for valid config for one datastore" in {
      val loadCall = DataStoreLoader.loadAll(List(goodConfig), crypto, TestAccessorProvider)
      loadCall.unsafeRunSync() shouldBe a[TestDataAccessor]
    }

    "return a data accessor for valid config for multiple datastores" in {
      val config1 = DataStoreConfig(
        "vinyldns.core.repository.MockDataStoreProvider",
        placeholderConfig,
        allEnabledReposConfig.copy(user = None))

      val config2 = DataStoreConfig(
        "vinyldns.core.repository.AlternateMockDataStoreProvider",
        placeholderConfig,
        allDisabledReposConfig.copy(user = enabled))

      val loadCall = DataStoreLoader.loadAll(List(config1, config2), crypto, TestAccessorProvider)
      loadCall.unsafeRunSync() shouldBe a[TestDataAccessor]
    }

    "throw an exception if getValidatedConfigs fails" in {
      val loadCall =
        DataStoreLoader.loadAll(
          List(goodConfig.copy(repositories = allDisabledReposConfig)),
          crypto,
          TestAccessorProvider)
      val thrown = the[DataStoreStartupError] thrownBy loadCall.unsafeRunSync()
      thrown.msg should include("Config validation error")
    }

    "throw an exception if datastore load fails" in {
      val loadCall = DataStoreLoader.loadAll(
        List(goodConfig.copy(className = "vinyldns.core.repository.FailDataStoreProvider")),
        crypto,
        TestAccessorProvider)
      val thrown = the[RuntimeException] thrownBy loadCall.unsafeRunSync()
      thrown.getMessage should include("ruh roh")
    }

    "throw an exception if accessor create fails" in {
      val loadCall = DataStoreLoader.loadAll(List(goodConfig), crypto, FailAccessorProvider)
      val thrown = the[DataStoreStartupError] thrownBy loadCall.unsafeRunSync()
      thrown.getMessage shouldBe "create failure"
    }
  }

  "getValidatedConfigs" should {
    "return all non-empty configs if all specified repos are defined once" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig.copy(zone = None))
      val config2 =
        goodConfig.copy(repositories = allDisabledReposConfig.copy(zone = enabled))
      val emptyConfig = goodConfig.copy(repositories = allDisabledReposConfig)

      val outcome =
        DataStoreLoader.getValidatedConfigs(
          List(config1, config2, emptyConfig),
          TestAccessorProvider.repoNames)
      outcome.value should contain(config1)
      outcome.value should contain(config2)
      outcome.value should not contain emptyConfig
    }
    "fail if any config is defined multiple times" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig)
      val config2 = goodConfig.copy(
        repositories = allDisabledReposConfig
          .copy(membership = enabled, group = enabled))

      val outcome =
        DataStoreLoader.getValidatedConfigs(List(config1, config2), List(user, membership, group))
      val message = outcome.leftValue.getMessage
      message should include("May not have more than one repo of type membership")
      message should include("May not have more than one repo of type group")
    }
    "fail if any config is defined zero times" in {
      val config = goodConfig.copy(repositories = allEnabledReposConfig.copy(user = None))

      val outcome = DataStoreLoader.getValidatedConfigs(List(config), List(user, membership, group))
      val message = outcome.leftValue.getMessage
      message shouldBe "Config validation error: Must have one repo of type user"
    }
  }

  "load" should {
    "succeed if properly configured" in {
      noException should be thrownBy DataStoreLoader.load(goodConfig, crypto).unsafeRunSync()
    }
    "fail if it cant find the class defined" in {
      val config = goodConfig.copy(className = "something.undefined")

      val call = DataStoreLoader.load(config, crypto)
      a[java.lang.ClassNotFoundException] should be thrownBy call.unsafeRunSync()
    }
    "fail the defined class is not a DataStoreProvider" in {
      val config =
        goodConfig.copy(className = "vinyldns.core.repository.DataStoreLoaderSpec")

      val call = DataStoreLoader.load(config, crypto)
      a[java.lang.ClassCastException] should be thrownBy call.unsafeRunSync()
    }
    "fail if the providers load method fails" in {
      val config =
        goodConfig.copy(className = "vinyldns.core.repository.FailDataStoreProvider")

      val call = DataStoreLoader.load(config, crypto)
      a[RuntimeException] should be thrownBy call.unsafeRunSync()
    }
  }

  "getRepoOf" should {
    val mockUserRepo = mock[UserRepository]
    "succeed if properly configured and loaded for repo" in {
      val config = DataStoreConfig(
        "some.class.name",
        placeholderConfig,
        allDisabledReposConfig.copy(user = enabled))
      val store = DataStore(Some(mockUserRepo))

      val outcome = DataStoreLoader.getRepoOf[UserRepository](List((config, store)), user)
      outcome should beValid(mockUserRepo)
    }
    "fail if not included in configuration" in {
      val config = DataStoreConfig("some.class.name", placeholderConfig, allDisabledReposConfig)
      val store = DataStore(Some(mockUserRepo))

      val outcome = DataStoreLoader
        .getRepoOf[UserRepository](List((config, store)), user)
      outcome should
        haveInvalid(
          "Repo user was not returned by configured database: Unknown Configured Database")
    }
    "fail if not returned by datastore" in {
      val config = DataStoreConfig(
        "some.class.name",
        placeholderConfig,
        allDisabledReposConfig.copy(user = enabled))
      val store = DataStore()

      val outcome = DataStoreLoader
        .getRepoOf[UserRepository](List((config, store)), user)
      outcome should
        haveInvalid("Repo user was not returned by configured database: some.class.name")
    }
  }
}
