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

package vinyldns.dynamodb.repository

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.repository.{
  DataStoreConfig,
  DataStoreStartupError,
  RepositoriesConfig,
  RepositoryName
}
import vinyldns.dynamodb.DynamoTestConfig
import pureconfig._
import pureconfig.generic.auto._

class DynamoDBDataStoreProviderSpec extends AnyWordSpec with Matchers {

  private val underTest = new DynamoDBDataStoreProvider()
  private val crypto = new NoOpCrypto()

  "load" should {
    // Note: success here will actually startup the repos, just testing failure in unit tests
    "Fail if a required setting is not included" in {
      val badConfig = ConfigFactory.parseString(
        """
          |    class-name = "vinyldns.dynamodb.repository.DynamoDbDataStoreProvider"
          |
          |    settings {
          |      key = "vinyldnsTest"
          |      secret = "notNeededForDynamoDbLocal"
          |    }
          |
          |    repositories {
          |      record-change {
          |        table-name = "test"
          |        provisioned-reads = 30
          |        provisioned-writes = 30
          |      }
          |    }
          |    """.stripMargin
      )

      val badSettings = ConfigSource.fromConfig(badConfig).loadOrThrow[DataStoreConfig]

      a[pureconfig.error.ConfigReaderException[DynamoDBDataStoreSettings]] should be thrownBy underTest
        .load(badSettings, crypto)
        .unsafeRunSync()
    }
  }
  "validateRepos" should {
    "Return successfully if all configured repos are implemented" in {
      noException should be thrownBy underTest
        .validateRepos(DynamoTestConfig.dynamoDBConfig.repositories)
        .unsafeRunSync()
    }
    "Fail if an unimplemented repo is enabled" in {
      val placeHolder = ConfigFactory.parseString("test=test")
      val badRepos = DynamoTestConfig.dynamoDBConfig.repositories.copy(zone = Some(placeHolder))

      val thrown = the[DataStoreStartupError] thrownBy underTest
        .validateRepos(badRepos)
        .unsafeRunSync()

      thrown.msg shouldBe "Invalid config provided to dynamodb; unimplemented repos included: Set(zone)"
    }
  }
  "loadRepoConfigs" should {
    "Return a map of configured repos are properly configured" in {
      val enabledRepoConf: Config =
        ConfigFactory.parseString("""
                             |{
                             |   table-name = "someName"
                             |   provisioned-reads = 20
                             |   provisioned-writes = 30
                             | }
                            """.stripMargin)
      val repoSettings =
        RepositoriesConfig(
          Some(enabledRepoConf),
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
      val response = underTest
        .loadRepoConfigs(repoSettings)
        .unsafeRunSync()

      response shouldBe Map(RepositoryName.user -> DynamoDBRepositorySettings("someName", 20, 30))
    }
    "Return an error if a repo isnt configured correctly" in {
      val badRepoConf: Config =
        ConfigFactory.parseString("""
                             |{
                             |   provisioned-reads = 20
                             |   provisioned-writes = 30
                             | }
                            """.stripMargin)
      val repoSettings =
        RepositoriesConfig(
          Some(badRepoConf),
          Some(badRepoConf),
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

      a[pureconfig.error.ConfigReaderException[DynamoDBRepositorySettings]] should be thrownBy underTest
        .loadRepoConfigs(repoSettings)
        .unsafeRunSync()
    }
  }
}
