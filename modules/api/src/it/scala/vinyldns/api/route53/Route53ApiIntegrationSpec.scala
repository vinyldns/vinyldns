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
import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import scalikejdbc.DB
import vinyldns.api.domain.zone.ZoneConnectionValidator
import vinyldns.api.engine.ZoneSyncHandler
import vinyldns.api.{MySqlApiIntegrationSpec, ResultHelpers}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.record.{NameSort, RecordType, RecordTypeSort}
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneChangeType}
import vinyldns.core.health.HealthCheck.HealthCheck
import vinyldns.route53.backend.{Route53Backend, Route53BackendConfig}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

class Route53ApiIntegrationSpec
  extends AnyWordSpec
    with ScalaFutures
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MySqlApiIntegrationSpec {

  private val testZone = Zone("example.com.", "test@test.com", backendId = Some("test"))

  private def testConnection: Route53Backend =
    Route53Backend
      .load(
        Route53BackendConfig(
          "test",
          Some("access"),
          Some("secret"),
          sys.env.getOrElse("R53_SERVICE_ENDPOINT", "http://localhost:19003"),
          "us-east-1"
        )
      )
      .unsafeRunSync()

  override def beforeAll(): Unit = {
    deleteZone()
    createZone()
  }

  override def afterAll(): Unit = clear()

  private def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM record_change")
      s.executeUpdate("DELETE FROM recordset")
      s.executeUpdate("DELETE FROM zone_access")
      s.executeUpdate("DELETE FROM zone")
      ()
    }

  private def deleteZone(): Unit = {
    val zoneIds = testConnection.client.listHostedZones().getHostedZones.asScala.map(_.getId).toList
    zoneIds.foreach { id =>
      testConnection.client.deleteHostedZone(new DeleteHostedZoneRequest().withId(id))
    }
  }

  private def createZone(): Unit =
    testConnection.createZone(testZone).unsafeRunSync()

  private val backendResolver = new BackendResolver {
    override def resolve(zone: Zone): Backend = testConnection

    override def healthCheck(timeout: Int): HealthCheck = IO.pure(Right(()))

    override def isRegistered(backendId: String): Boolean = true

    override def ids: NonEmptyList[String] = NonEmptyList.one("r53")
  }

  "Route53 backend" should {
    "connect to an existing route53 zone" in {
      val conn = testConnection
      val addRecord = makeTestAddChange(rsOk, testZone)
      conn.applyChange(addRecord).unsafeRunSync()

      // Simulate a create in Vinyl
      val syncHandler = ZoneSyncHandler.apply(
        recordSetRepository,
        recordChangeRepository,
        recordSetCacheRepository,
        zoneChangeRepository,
        zoneRepository,
        backendResolver,
        10000
      )
      val zoneSync = ZoneChange(testZone, "system", ZoneChangeType.Create)
      syncHandler.apply(zoneSync).unsafeRunSync()

      // We should have both the record we created above as well as at least one NS record
      val results = recordSetRepository
        .listRecordSets(Some(testZone.id), None, None, None, None, None, NameSort.ASC, RecordTypeSort.ASC)
        .unsafeRunSync()
      results.recordSets.map(_.typ).distinct should contain theSameElementsAs List(
        rsOk.typ,
        RecordType.NS
      )
      results.recordSets.map(_.name) should contain(rsOk.name)

      // Ensure that the NS record matches the zone name
      val ns = results.recordSets.find(_.typ == RecordType.NS).head
      ns.name shouldBe testZone.name
    }

    "be valid in the ZoneConnectionValidator" in {
      // Check that the ZoneConnectionValidator can connect to the zone
      val zcv = new ZoneConnectionValidator(backendResolver, List(new Regex(".*")), 10000)
      zcv.isValidBackendId(Some("r53")) shouldBe Right(())
      val result = zcv.validateZoneConnections(testZone).value.unsafeRunSync()
      result shouldBe Right(())
    }
  }
}
