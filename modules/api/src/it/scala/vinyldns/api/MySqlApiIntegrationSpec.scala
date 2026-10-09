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
import scalikejdbc.DB
import vinyldns.mysql.MySqlIntegrationSpec

trait MySqlApiIntegrationSpec extends MySqlIntegrationSpec {
  val mysqlConfig: Config = ConfigFactory.load().getConfig("vinyldns.mysql")

  def clearRecordSetRepo(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM recordset")
    }

  def clearZoneRepo(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone")
    }

  def clearGenerateZoneRepo(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM generate_zone")
    }

  def clearGroupRepo(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM `groups`")
    }
}
