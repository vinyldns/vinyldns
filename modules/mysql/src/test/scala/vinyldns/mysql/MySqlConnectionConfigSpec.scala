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

package vinyldns.mysql

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig._
import pureconfig.generic.auto._
import vinyldns.core.repository.DataStoreConfig

class MySqlConnectionConfigSpec extends AnyWordSpec with Matchers {
  val mySqlConfig: Config = ConfigFactory.load().getConfig("mysql")
  val dataStoreSettings: DataStoreConfig =
    ConfigSource.fromConfig(mySqlConfig).loadOrThrow[DataStoreConfig]

  implicit val mySqlPropertiesReader: ConfigReader[Map[String, AnyRef]] =
    MySqlConnectionConfig.mySqlPropertiesReader

  "loading MySqlConnectionConfig" should {
    "include all specified properties" in {
      val configProperties = Map[String, AnyRef](
        "cachePrepStmts" -> java.lang.Boolean.TRUE,
        "rewriteBatchedStatements" -> java.lang.Boolean.TRUE,
        "prepStmtCacheSize" -> Integer.valueOf(250),
        "prepStmtCacheSqlLimit" -> Integer.valueOf(2048)
      )

      val settingsConfig =
        ConfigSource.fromConfig(dataStoreSettings.settings).loadOrThrow[MySqlConnectionConfig]
      settingsConfig.mySqlProperties shouldBe configProperties
    }

    "store an empty map as mySqlProperties if excluded" in {

      val conf = ConfigFactory.parseString("""
          |  {
          |    name = "vinyldns"
          |    driver = "some.test.driver"
          |    migration-url = "migration-url"
          |    url = "url"
          |    user = "some-user"
          |    password = "some-pass"
          |  }
          |  """.stripMargin)

      val settingsConfig = ConfigSource.fromConfig(conf).loadOrThrow[MySqlConnectionConfig]
      settingsConfig.mySqlProperties shouldBe Map()
    }
  }
}
