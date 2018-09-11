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

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.repository.{DataStoreConfig, DataStoreStartupError}

class MySqlDataStoreProviderSpec extends WordSpec with Matchers {

  val mySqlConfig: Config = ConfigFactory.parseString(
    """
      |    class-name = "vinyldns.api.repository.mysql.MySqlDataStoreProvider"
      |
      |    settings {
      |      name = "test-database"
      |      driver = "org.mariadb.jdbc.Driver"
      |      migration-url = "test-url"
      |      url = "test-url"
      |      user = "test-user"
      |      password = "test-pass"
      |      pool-initial-size = 10
      |      pool-max-size = 20
      |      connection-timeout-millis = 1000
      |      max-life-time = 600000
      |    }
      |
      |    repositories {
      |      zone {},
      |      batch-change {}
      |    }
      |    """.stripMargin)

  val dataStoreSettings: DataStoreConfig =
    pureconfig.loadConfigOrThrow[DataStoreConfig](mySqlConfig)

  val underTest = new MySqlDataStoreProvider()

  "validateRepos" should {
    "Return successfully if all configured repos are implemented" in {
      noException should be thrownBy underTest
        .validateRepos(dataStoreSettings.repositories)
        .unsafeRunSync()
    }
    "Fail if an unimplemented repo is enabled" in {
      val placeHolder = ConfigFactory.parseString("test=test")
      val badRepos = dataStoreSettings.repositories.copy(user = Some(placeHolder))

      val thrown = the[DataStoreStartupError] thrownBy underTest
        .validateRepos(badRepos)
        .unsafeRunSync()

      thrown.msg shouldBe "Invalid config provided to mysql; unimplemented repos included: Set(user)"
    }
  }
  "load" should {
    // Note: success here will actually startup the repos. if the integration tests pass, that is working
    // as those are calling MySqlDataStoreProvider.load
    "Fail if a required setting is not included" in {
      val badConfig = ConfigFactory.parseString(
        """
          |    class-name = "vinyldns.api.repository.mysql.MySqlDataStoreProvider"
          |
          |    settings {
          |      name = "test-database"
          |      driver = "org.mariadb.jdbc.Driver"
          |      migration-url = "test-url"
          |      pool-initial-size = 10
          |      pool-max-size = 20
          |      connection-timeout-millis = 1000
          |      max-life-time = 600000
          |    }
          |
          |    repositories {
          |      zone {},
          |      batch-change {}
          |    }
          |    """.stripMargin)

      val badSettings = pureconfig.loadConfigOrThrow[DataStoreConfig](badConfig)

      a[pureconfig.error.ConfigReaderException[MySqlDataStoreSettings]] should be thrownBy underTest
        .load(badSettings)
        .unsafeRunSync()
    }
    "Fail if validateRepos fails" in {
      val placeHolder = ConfigFactory.parseString("test=test")
      val badRepos = dataStoreSettings.repositories.copy(user = Some(placeHolder))
      val badSettings = dataStoreSettings.copy(repositories = badRepos)

      a[DataStoreStartupError] should be thrownBy underTest
        .load(badSettings)
        .unsafeRunSync()
    }
  }
}
