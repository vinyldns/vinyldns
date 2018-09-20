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

package vinyldns.api
import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.mysql.repository.MySqlTestTrait

trait MySqlApiIntegrationSpec extends MySqlTestTrait {
  val mysqlConfig: Config = ConfigFactory.parseString(s"""
    |  mysql {
    |    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
    |
    |    settings {
    |      name = "vinyldns"
    |      driver = "org.mariadb.jdbc.Driver"
    |      migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"
    |      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"
    |      user = "root"
    |      password = "pass"
    |      pool-max-size = 20
    |      connection-timeout-millis = 1000
    |      max-life-time = 600000
    |    }
    |
    |    repositories {
    |      batch-change {}
    |      zone {}
    |    }
    |  }
     """.stripMargin)
}
