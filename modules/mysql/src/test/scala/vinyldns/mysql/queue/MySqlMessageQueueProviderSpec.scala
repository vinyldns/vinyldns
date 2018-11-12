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

package vinyldns.mysql.queue

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.queue.MessageQueueConfig
import vinyldns.mysql.MySqlConnectionConfig

class MySqlMessageQueueProviderSpec extends WordSpec with Matchers {

  val undertest = new MySqlMessageQueueProvider()

  "MySqlMessageQueueProvider" should {
    "fail with invalid config" in {
      val badConfig =
        ConfigFactory.parseString("""
            |  class-name = "vinyldns.mysql.queue.MySqlMessageQueueProvider"
            |    polling-interval = 250.millis
            |    messages-per-poll = 10
            |
            |  settings = {
            |    name = "vinyldns"
            |    driver = "org.mariadb.jdbc.Driver"
            |    password = "pass"
            |    }
            |
            |    """.stripMargin)

      val badSettings = pureconfig.loadConfigOrThrow[MessageQueueConfig](badConfig)

      a[pureconfig.error.ConfigReaderException[MySqlConnectionConfig]] should be thrownBy undertest
        .load(badSettings)
        .unsafeRunSync()
    }
  }
}
