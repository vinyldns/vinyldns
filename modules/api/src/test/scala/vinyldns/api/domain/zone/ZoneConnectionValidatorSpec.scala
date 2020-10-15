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

import cats.scalatest.{EitherMatchers, EitherValues}
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.core.domain.record._
import vinyldns.api.{ResultHelpers, VinylDNSConfig}
import cats.effect._
import org.mockito.Matchers.any
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.zone.{ConfiguredDnsConnections, LegacyDnsBackend, Zone, ZoneConnection}

import scala.concurrent.duration._

class ZoneConnectionValidatorSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with EitherMatchers
    with EitherValues {

  private val mockBackend = mock[Backend]
  private val mockZoneView = mock[ZoneView]
  private val mockBackendResolver = mock[BackendResolver]

  override protected def beforeEach(): Unit =
    reset(mockBackend, mockZoneView)

  private def testLoadDns(zone: Zone) = zone.name match {
    case "error." => IO.raiseError(new RuntimeException("transfer connection failure!"))
    case "timeout." =>
      IO {
        Thread.sleep(200)
        mockZoneView
      }
    case _ =>
      IO.pure(mockZoneView)
  }

  private def generateZoneView(zone: Zone, recordSets: RecordSet*): ZoneView =
    ZoneView(
      zone = zone,
      recordSets = recordSets.toList
    )

  class TestConnectionValidator()
      extends ZoneConnectionValidator(mockBackendResolver, VinylDNSConfig.approvedNameServers) {
    override val opTimeout: FiniteDuration = 10.milliseconds
    override def loadDns(zone: Zone): IO[ZoneView] = testLoadDns(zone)
    override def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit] =
      Right(())
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
    List(SOAData(Fqdn("something"), "other", 1, 2, 3, 5, 6))
  )

  private val successNS = RecordSet(
    testZone.id,
    testZone.name,
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData(Fqdn("some.test.ns.")))
  )

  private val failureNs = RecordSet(
    testZone.id,
    testZone.name,
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData(Fqdn("some.test.ns.")), NSData(Fqdn("not.approved.")))
  )

  private val delegatedNS = RecordSet(
    testZone.id,
    s"sub.${testZone.name}",
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(NSData(Fqdn("sub.some.test.ns.")))
  )

  val zc = ZoneConnection("zc.", "zc.", "zc", "10.1.1.1")
  val transfer = ZoneConnection("transfer.", "transfer.", "transfer", "10.1.1.1")
  val backend = LegacyDnsBackend(
    "some-backend-id",
    zc.copy(name = "backend-conn"),
    transfer.copy(name = "backend-transfer")
  )
  val connections = ConfiguredDnsConnections(zc, transfer, List(backend))

  "ConnectionValidator" should {
    "respond with a success if the connection is resolved" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone, successSoa, successNS, delegatedNS).recordSetsMap)
        .when(mockZoneView)
        .recordSetsMap
      doReturn(IO.pure(true)).when(mockBackend).zoneExists(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = awaitResultOf(underTest.validateZoneConnections(testZone).value)
      result should be(right)
    }

    "respond with a failure if NS records are not in the approved server list" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone, successSoa, failureNs, delegatedNS).recordSetsMap)
        .when(mockZoneView)
        .recordSetsMap
      doReturn(IO.pure(true)).when(mockBackend).zoneExists(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = leftResultOf(underTest.validateZoneConnections(testZone).value)
      result shouldBe ZoneValidationFailed(
        testZone,
        List(s"Name Server not.approved. is not an approved name server."),
        "Zone could not be loaded due to validation errors."
      )
    }

    "respond with a failure if no records are returned from the backend" in {
      doReturn(testZone).when(mockZoneView).zone
      doReturn(generateZoneView(testZone).recordSetsMap).when(mockZoneView).recordSetsMap
      doReturn(IO.pure(List.empty[RecordSet]))
        .when(mockBackend)
        .resolve(testZone.name, testZone.name, RecordType.SOA)
      doReturn(IO.pure(true)).when(mockBackend).zoneExists(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = leftResultOf(underTest.validateZoneConnections(testZone).value)
      result shouldBe a[ZoneValidationFailed]
      result shouldBe ZoneValidationFailed(
        testZone,
        List("Missing apex NS record"),
        "Zone could not be loaded due to validation errors."
      )
    }

    "respond with a failure if connection cant be made" in {
      val badZone = Zone(
        "error.",
        "test@test.com",
        connection =
          Some(ZoneConnection("error.", "error.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")),
        transferConnection =
          Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1"))
      )
      doReturn(IO.pure(true)).when(mockBackend).zoneExists(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = leftResultOf(underTest.validateZoneConnections(badZone).value)
      result shouldBe a[ConnectionFailed]
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

      doReturn(IO.pure(true)).when(mockBackend).zoneExists(any[Zone])
      doReturn(mockBackend).when(mockBackendResolver).resolve(any[Zone])

      val result = leftResultOf(underTest.validateZoneConnections(badZone).value)
      result shouldBe a[ConnectionFailed]
      result.getMessage should include("transfer connection failure!")
    }

    "isValidBackendId" should {
      doReturn(true).when(mockBackendResolver).isRegistered("some-test-backend")
      doReturn(false).when(mockBackendResolver).isRegistered("bad")

      val underTest =
        new ZoneConnectionValidator(mockBackendResolver, Nil)

      "return success if the backendId exists" in {
        underTest.isValidBackendId(Some("some-test-backend")) shouldBe right
      }
      "return success on None" in {
        underTest.isValidBackendId(None) shouldBe right
      }
      "return failure if the backendId does not exist" in {
        underTest.isValidBackendId(Some("bad")) shouldBe left
      }
    }
  }
}
