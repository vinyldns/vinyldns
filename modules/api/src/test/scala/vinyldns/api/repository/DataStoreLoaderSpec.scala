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

import cats.effect.IO
import cats.scalatest.{EitherMatchers, EitherValues}
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mockito.MockitoSugar
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.{ZoneChangeRepository, ZoneRepository}

import scala.collection.JavaConverters._

class DataStoreLoaderSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with EitherMatchers
    with EitherValues {

  val placeholderConfig = ConfigFactory.parseMap(Map[String, String]().asJava)

  val allEnabledReposConfig = RepositoriesConfig(
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig),
    Some(placeholderConfig)
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

  val baseLoader = new DataStoreLoader()

  class LoaderWithOverrides(
      loadOverride: DataStoreConfig => IO[DataStore] = baseLoader.load,
      getValidatedConfigsOverride: List[DataStoreConfig] => Either[
        DataStoreInitializationError,
        List[DataStoreConfig]] = baseLoader.getValidatedConfigs,
      generateAccessorOverride: List[DataStore] => Either[
        DataStoreInitializationError,
        DataAccessor] = baseLoader.generateAccessor)
      extends DataStoreLoader {

    override def load(config: DataStoreConfig): IO[DataStore] = loadOverride(config)

    override def getValidatedConfigs(configs: List[DataStoreConfig])
      : Either[DataStoreInitializationError, List[DataStoreConfig]] =
      getValidatedConfigsOverride(configs)

    override def generateAccessor(
        dataStores: List[DataStore]): Either[DataStoreInitializationError, DataAccessor] =
      generateAccessorOverride(dataStores)
  }

  "loadAll" should {
    "return a data accessor for valid config for one datastore" in {
      val loadCall = baseLoader.loadAll(List(goodConfig))
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
        allDisabledReposConfig.copy(user = Some(placeholderConfig)))

      val loadCall = baseLoader.loadAll(List(config1, config2))
      noException should be thrownBy loadCall.unsafeRunSync()
    }

    "throw an exception if getValidatedConfigs fails" in {
      val error: DataStoreInitializationError = DataStoreInitializationError("oh no!")
      val loaderWithOverrides =
        new LoaderWithOverrides(getValidatedConfigsOverride = _ => Left(error))

      val loadCall = loaderWithOverrides.loadAll(List(goodConfig))
      val thrown = the[DataStoreInitializationError] thrownBy loadCall.unsafeRunSync()
      thrown shouldBe error
    }

    "throw an exception if load fails" in {
      val error = new RuntimeException("this is bad!")
      val loaderWithOverrides =
        new LoaderWithOverrides(loadOverride = _ => IO.raiseError(error))

      val loadCall = loaderWithOverrides.loadAll(List(goodConfig))
      val thrown = the[Exception] thrownBy loadCall.unsafeRunSync()
      thrown shouldBe error
    }

    "throw an exception if generateAccessor fails" in {
      val error = DataStoreInitializationError("wuut!")
      val loaderWithOverrides =
        new LoaderWithOverrides(generateAccessorOverride = _ => Left(error))

      val loadCall = loaderWithOverrides.loadAll(List(goodConfig))
      val thrown = the[DataStoreInitializationError] thrownBy loadCall.unsafeRunSync()
      thrown shouldBe error
    }
  }

  "getValidatedConfigs" should {
    "return all non-empty configs if all repos are defined once" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig.copy(zone = None))
      val config2 =
        goodConfig.copy(repositories = allDisabledReposConfig.copy(zone = Some(placeholderConfig)))
      val emptyConfig = goodConfig.copy(repositories = allDisabledReposConfig)

      val outcome = baseLoader.getValidatedConfigs(List(config1, config2, emptyConfig))
      outcome.value should contain(config1)
      outcome.value should contain(config2)
      outcome.value should not contain (emptyConfig)
    }
    "fail if any config is defined multiple times" in {
      val config1 = goodConfig.copy(repositories = allEnabledReposConfig)
      val config2 = goodConfig.copy(
        repositories = allDisabledReposConfig
          .copy(membership = Some(placeholderConfig), group = Some(placeholderConfig)))

      val outcome = baseLoader.getValidatedConfigs(List(config1, config2))
      val message = outcome.leftValue.getMessage
      message should include("May not have more than one repo of type membership")
      message should include("May not have more than one repo of type group")
    }
    "fail if any config is defined zero times" in {
      val config = goodConfig.copy(repositories = allEnabledReposConfig.copy(user = None))

      val outcome = baseLoader.getValidatedConfigs(List(config))
      val message = outcome.leftValue.getMessage
      message shouldBe "Config error: Must have one repo of type user"
    }
  }

  "load" should {
    "succeed if properly configured" in {
      noException should be thrownBy baseLoader.load(goodConfig).unsafeRunSync()
    }
    "fail if it cant find the class defined" in {
      val config = goodConfig.copy(className = "something.undefined")

      val call = baseLoader.load(config)
      a[java.lang.ClassNotFoundException] should be thrownBy call.unsafeRunSync()
    }
    "fail the defined class is not a DataStoreProvider" in {
      val config =
        goodConfig.copy(className = "vinyldns.api.repository.DataStoreLoaderSpec")

      val call = baseLoader.load(config)
      a[java.lang.ClassCastException] should be thrownBy call.unsafeRunSync()
    }
    "fail if the providers load method fails" in {
      val config =
        goodConfig.copy(className = "vinyldns.api.repository.FailDataStoreProvider")

      val call = baseLoader.load(config)
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

    "combine DataStores into a single accessor" in {
      val store1 = DataStore(Some(user), Some(group), None, Some(groupChange))
      val store2 = DataStore(
        None,
        None,
        Some(membership),
        None,
        Some(recordSet),
        Some(recordChange),
        Some(zoneChange),
        Some(zone),
        Some(batchChange))

      val outcome = baseLoader.generateAccessor(List(store1, store2))
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

    "return an DataStoreInitializationError if a datastore is undefined" in {
      val store1 = DataStore(Some(user), Some(group), None, Some(groupChange))
      val store2 = DataStore(None, None, Some(membership))

      val outcome = baseLoader.generateAccessor(List(store1, store2))
      outcome.leftValue shouldBe a[DataStoreInitializationError]
    }
  }
}
