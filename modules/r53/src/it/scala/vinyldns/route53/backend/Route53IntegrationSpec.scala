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

package vinyldns.route53.backend

import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._
import org.scalatest.OptionValues._
import org.scalatest.EitherValues._
import vinyldns.core.domain.backend.BackendResponse
import vinyldns.core.domain.{Fqdn, record}
import vinyldns.core.domain.record.{RecordSet, RecordType}

class Route53IntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._

  private val testZone = Zone("example.com.", "test@test.com", backendId = Some("test"))

  override def beforeAll(): Unit = {
    deleteZone()
    createZone()
  }

  private def testConnection: Route53Backend =
    Route53Backend
      .load(
        Route53BackendConfig(
          "test",
          Option("access"),
          Option("secret"),
          None,
          None,
          sys.env.getOrElse("R53_SERVICE_ENDPOINT", "http://localhost:19003"),
          "us-east-1"
        )
      )
      .unsafeRunSync()

  private def deleteZone(): Unit = {
    val zoneIds = testConnection.client.listHostedZones().getHostedZones.asScala.map(_.getId).toList
    zoneIds.foreach { id =>
      testConnection.client.deleteHostedZone(new DeleteHostedZoneRequest().withId(id))
    }
  }

  private def createZone(): Unit =
    testConnection.createZone(testZone).unsafeRunSync()

  private def checkRecordExists(rs: RecordSet, zone: Zone): Unit = {
    val resolveResult =
      testConnection.resolve(rs.name, zone.name, rs.typ).unsafeRunSync().headOption.value
    resolveResult.records should contain theSameElementsAs rs.records
    resolveResult.name shouldBe rs.name
    resolveResult.ttl shouldBe rs.ttl
    resolveResult.typ shouldBe rs.typ
  }

  private def checkRecordNotExists(rs: RecordSet, zone: Zone): Unit =
    testConnection.resolve(rs.name, zone.name, rs.typ).unsafeRunSync() shouldBe empty

  private def testRecordSet(rs: RecordSet, zone: Zone): Unit = {
    val conn = testConnection
    val testRecord = rs.copy(zoneId = zone.id)
    val change = makeTestAddChange(testRecord, zone, "test-user")
    val result = conn.applyChange(change).unsafeRunSync()
    result shouldBe a[BackendResponse.NoError]

    // We should be able to resolve now
    checkRecordExists(testRecord, zone)

    val del = makePendingTestDeleteChange(testRecord, zone, "test-user")
    conn.applyChange(del).unsafeRunSync()

    // Record should not be found
    checkRecordNotExists(testRecord, zone)
  }

  "Route53 Connections" should {
    "return nothing if the zone does not exist" in {
      testConnection.resolve("foo", "bar", RecordType.A).unsafeRunSync() shouldBe empty
    }
    "work for a" in {
      testRecordSet(rsOk, testZone)
    }
    "work for aaaa" in {
      testRecordSet(aaaa, testZone)
    }
    "work for cname" in {
      testRecordSet(cname, testZone)
    }
    "work for naptr" in {
      testRecordSet(naptr, testZone)
    }
    "work for mx" in {
      val testMxData = record.MXData(10, Fqdn("mx.example.com."))
      val testMx = mx.copy(records = List(testMxData))
      testRecordSet(testMx, testZone)
    }
    "work for txt" in {
      testRecordSet(txt, testZone)
    }
    "check if zone exists" in {
      val notFound = Zone("blah.foo.", "test@test.com", backendId = Some("test"))
      testConnection.zoneExists(notFound).unsafeRunSync() shouldBe false
      testConnection.zoneExists(testZone).unsafeRunSync() shouldBe true
    }
    "fail when applying the change and it does not exist" in {
      val testRecord = aaaa
      val testZone = okZone
      val change = makeTestAddChange(testRecord, testZone, "test-user")
      val result = testConnection.applyChange(change).attempt.unsafeRunSync()
      result.left.value shouldBe a[Route53BackendResponse.ZoneNotFoundError]
    }
  }
}
