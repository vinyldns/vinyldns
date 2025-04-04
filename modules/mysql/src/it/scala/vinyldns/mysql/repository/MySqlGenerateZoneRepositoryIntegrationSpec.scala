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

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.TestZoneData.{generateBindZoneAuthorized, generatePdnsZoneAuthorized}
import vinyldns.core.domain.zone._
import vinyldns.mysql.{TestMySqlInstance, TransactionProvider}


class MySqlGenerateZoneRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with TransactionProvider {

  private var repo: GenerateZoneRepository = _

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.generateZoneRepository

  override protected def beforeEach(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM generate_zone")
    }

  "MySqlZoneRepository" should {
    "return the generate bind zone response when it is saved" in {
      repo.save(generateBindZoneAuthorized).unsafeRunSync() shouldBe generateBindZoneAuthorized
    }
    "return the generate powerDNS zone response when it is saved" in {
      repo.save(generatePdnsZoneAuthorized).unsafeRunSync() shouldBe generatePdnsZoneAuthorized
    }
  }
}
