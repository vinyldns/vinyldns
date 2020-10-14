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

package vinyldns.api.route53

import cats.data.NonEmptyList
import cats.effect.IO
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import scalikejdbc.DB
import vinyldns.api.engine.ZoneSyncHandler
import vinyldns.api.{MySqlApiIntegrationSpec, ResultHelpers}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.{NameSort, RecordType}
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneChangeType}
import vinyldns.core.health.HealthCheck.HealthCheck
import vinyldns.route53.backend.{Route53Backend, Route53BackendConfig}

class Route53ApiIntegrationSpec
    extends MySqlApiIntegrationSpec
    with AnyWordSpecLike
    with ScalaFutures
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  private val testZone = Zone("example.com.", "test@test.com", backendId = Some("test"))
  private def testConnection: Route53Backend =
    Route53Backend
      .load(
        Route53BackendConfig("test", "access", "secret", "http://127.0.0.1:19009", "us-east-1")
      )
      .unsafeRunSync()

  override def afterEach(): Unit = clear()

  private def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM record_change")
      s.executeUpdate("DELETE FROM recordset")
      s.executeUpdate("DELETE FROM zone_access")
      s.executeUpdate("DELETE FROM zone")
      ()
    }

  "Route53 backend" should {
    "connect to an existing route53 zone" in {
      val conn = testConnection

      val backendResolver = new BackendResolver {
        override def resolve(zone: Zone): Backend = conn

        override def healthCheck(timeout: Int): HealthCheck = IO.pure(Right(()))

        override def isRegistered(backendId: String): Boolean = true

        override def ids: NonEmptyList[String] = NonEmptyList.one("r53")
      }
      conn.createZone(testZone).unsafeRunSync()

      val addRecord = makeTestAddChange(rsOk, testZone)
      conn.applyChange(addRecord).unsafeRunSync()

      // Simulate a create in Vinyl
      val syncHandler = ZoneSyncHandler.apply(
        recordSetRepository,
        recordChangeRepository,
        zoneChangeRepository,
        zoneRepository,
        backendResolver
      )
      val zoneSync = ZoneChange(testZone, "system", ZoneChangeType.Create)
      syncHandler.apply(zoneSync).unsafeRunSync()

      // We should have both the record we created above as well as at least one NS record
      val results = recordSetRepository
        .listRecordSets(Some(testZone.id), None, None, None, None, None, NameSort.ASC)
        .unsafeRunSync()
      results.recordSets.map(_.typ).distinct should contain theSameElementsAs List(
        rsOk.typ,
        RecordType.NS
      )
      results.recordSets.map(_.name) should contain(rsOk.name)
    }
  }
}
