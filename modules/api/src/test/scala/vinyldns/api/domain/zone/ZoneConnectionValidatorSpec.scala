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

package vinyldns.api.domain.zone

import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.Interfaces._
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.domain.dns.DnsProtocol.{TypeNotFound, Unrecoverable}
import vinyldns.core.domain.record._
import vinyldns.api.{ResultHelpers, VinylDNSTestData}
import cats.effect._
import vinyldns.core.domain.zone.{Zone, ZoneConnection}
import vinyldns.core.health.HealthCheck.HealthCheckError

import scala.concurrent.duration._

class ZoneConnectionValidatorSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with VinylDNSTestData
    with BeforeAndAfterEach
    with ResultHelpers
    with EitherMatchers {

  private val mockDnsConnection = mock[DnsConnection]
  private val mockZoneView = mock[ZoneView]

  override protected def beforeEach(): Unit =
    reset(mockDnsConnection, mockZoneView)

  private def testDnsConnection(conn: ZoneConnection) =
    if (conn.keyName == "error.") {
      throw new RuntimeException("main connection failure!")
    } else {
      mockDnsConnection
    }

  private def testLoadDns(zone: Zone) = zone.name match {
    case "error." => IO.raiseError(new RuntimeException("transfer connection failure!"))
    case "timeout." =>
      IO {
        Thread.sleep(100)
        mockZoneView
      }
    case _ =>
      IO.pure(mockZoneView)
  }

  private def testDefaultConnection = mock[ZoneConnection]

  private def generateZoneView(zone: Zone, recordSets: RecordSet*): ZoneView =
    ZoneView(
      zone = zone,
      recordSets = recordSets.toList
    )

  class TestConnectionValidator() extends ZoneConnectionValidator(testDefaultConnection) {
    override val opTimeout: FiniteDuration = 10.milliseconds
    override def dnsConnection(conn: ZoneConnection): DnsConnection = testDnsConnection(conn)
    override def loadDns(zone: Zone): IO[ZoneView] = testLoadDns(zone)
  }

  private val underTest = new TestConnectionValidator()

  private val testZone = Zone(
    "vinyldns.",
    "test@test.com",
    connection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")),
    transferConnection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))
  )

  private val successSoa = RecordSet(
    testZone.id,
    testZone.name,
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SOAData("something", "other", 1, 2, 3, 5, 6)))

  private val successNS = RecordSet(
    testZone.id,
    testZone.name,
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData("some.test.ns.")))

  private val failureNs = RecordSet(
    testZone.id,
    testZone.name,
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData("some.test.ns."), NSData("not.approved.")))

  private val mockRecordSet = mock[RecordSet]

  "ConnectionValidator" should {
    "respond with a success if the connection is resolved" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone, successSoa, successNS).recordSetsMap)
        .when(mockZoneView)
        .recordSetsMap
      doReturn(List(successSoa).toResult)
        .when(mockDnsConnection)
        .resolve(testZone.name, testZone.name, RecordType.SOA)

      val result = awaitResultOf(underTest.validateZoneConnections(testZone).value)
      result should be(right)
    }

    "respond with a failure if NS records are not in the approved server list" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone, successSoa, failureNs).recordSetsMap)
        .when(mockZoneView)
        .recordSetsMap
      doReturn(List(successSoa).toResult)
        .when(mockDnsConnection)
        .resolve(testZone.name, testZone.name, RecordType.SOA)

      val result = leftResultOf(underTest.validateZoneConnections(testZone).value)
      result shouldBe a[ZoneValidationFailed]
    }

    "respond with a failure if no records are returned from the backend" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone).recordSetsMap).when(mockZoneView).recordSetsMap
      doReturn(List.empty[RecordSet].toResult)
        .when(mockDnsConnection)
        .resolve(testZone.name, testZone.name, RecordType.SOA)

      val result = leftResultOf(underTest.validateZoneConnections(testZone).value)
      result shouldBe a[ConnectionFailed]
    }

    "respond with a failure if any failure is returned from the backend" in {
      doReturn(result(TypeNotFound("fail")))
        .when(mockDnsConnection)
        .resolve(testZone.name, testZone.name, RecordType.SOA)

      val error = leftResultOf(underTest.validateZoneConnections(testZone).value)
      error shouldBe a[ConnectionFailed]
    }

    "respond with a failure if connection cant be made" in {
      val badZone = Zone(
        "vinyldns.",
        "test@test.com",
        connection =
          Some(ZoneConnection("error.", "error.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")),
        transferConnection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))
      )

      val result = leftResultOf(underTest.validateZoneConnections(badZone).value)
      result shouldBe a[ConnectionFailed]
      result.getMessage should include("main connection failure!")
    }

    "respond with a failure if loadDns throws an error" in {
      val badZone = Zone(
        "error.",
        "test@test.com",
        connection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")),
        transferConnection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))
      )

      doReturn(List(mockRecordSet).toResult)
        .when(mockDnsConnection)
        .resolve(badZone.name, badZone.name, RecordType.SOA)

      val result = leftResultOf(underTest.validateZoneConnections(badZone).value)
      result shouldBe a[ConnectionFailed]
      result.getMessage should include("transfer connection failure!")
    }

    "respond with a failure if loadDns times out" in {
      val badZone = Zone(
        "timeout.",
        "test@test.com",
        connection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")),
        transferConnection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))
      )

      doReturn(List(mockRecordSet).toResult)
        .when(mockDnsConnection)
        .resolve(badZone.name, badZone.name, RecordType.SOA)

      val result = leftResultOf(underTest.validateZoneConnections(badZone).value)
      result shouldBe a[ConnectionFailed]
      result.getMessage should include("Transfer connection invalid")
    }

    "respond with a success if health check passes" in {
      doReturn(IO(Right(List(mockRecordSet))).toResult)
        .when(mockDnsConnection)
        .resolve("maybe-exists-record", "vinyldns.", RecordType.A)

      val result = underTest.healthCheck().unsafeRunSync()
      result should beRight(())
    }

    "respond with a failure if health check fails" in {
      val failure = Unrecoverable("unrecoverable error")

      doReturn(IO(Left(failure)).toResult)
        .when(mockDnsConnection)
        .resolve("maybe-exists-record", "vinyldns.", RecordType.A)

      val result = underTest.healthCheck().unsafeRunSync()
      result should beLeft(HealthCheckError(failure.message))
    }
  }
}
