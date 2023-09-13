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

package vinyldns.mysql.repository

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.repository.DataStoreConfig
import vinyldns.mysql.MySqlConnectionConfig
import pureconfig._
import pureconfig.generic.auto._

class MySqlDataStoreProviderSpec extends AnyWordSpec with Matchers {
  val mySqlConfig: Config = ConfigFactory.load().getConfig("mysql")

  val dataStoreSettings: DataStoreConfig =
    ConfigSource.fromConfig(mySqlConfig).loadOrThrow[DataStoreConfig]

  val underTest = new MySqlDataStoreProvider()

  val crypto: CryptoAlgebra = new NoOpCrypto()

  "load" should {
    // Note: success here will actually startup the repos. if the integration tests pass, that is working
    // as those are calling MySqlDataStoreProvider.load
    "Fail if a required setting is not included" in {
      val badConfig = ConfigFactory.parseString(
        """
          |    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
          |
          |    settings {
          |      name = "test-database"
          |      driver = "org.mariadb.jdbc.Driver"
          |      migration-url = "test-url"
          |      maximum-pool-size = 20
          |      connection-timeout-millis = 1000
          |      max-lifetime = 600000
          |    }
          |
          |    repositories {
          |      zone {},
          |      batch-change {}
          |    }
          |    """.stripMargin
      )

      val badSettings = ConfigSource.fromConfig(badConfig).loadOrThrow[DataStoreConfig]

      a[pureconfig.error.ConfigReaderException[MySqlConnectionConfig]] should be thrownBy underTest
        .load(badSettings, crypto)
        .unsafeRunSync()
    }
  }
}
