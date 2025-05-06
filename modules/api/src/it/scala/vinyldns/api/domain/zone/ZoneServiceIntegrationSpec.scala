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

import cats.data.NonEmptyList
import cats.effect._

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.Mockito.doReturn
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import scalikejdbc.DB
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.membership.MembershipService
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.engine.TestMessageQueue
import vinyldns.mysql.TransactionProvider
import vinyldns.api.{MySqlApiIntegrationSpec, ResultHelpers}
import vinyldns.core.TestMembershipData.{abcAuth, okAuth, okGroup, okUser}
import vinyldns.core.TestZoneData.{abcZone, bindZoneGenerationResponse, okZone}
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.backend.BackendResolver
import vinyldns.core.domain.membership.{GroupRepository, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ZoneServiceIntegrationSpec
    extends AnyWordSpec
    with ScalaFutures
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with MySqlApiIntegrationSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with TransactionProvider {

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  private val recordSetRepo = recordSetRepository
  private val zoneRepo: ZoneRepository = zoneRepository
  private val mockMembershipService = mock[MembershipService]
  private val mockDnsProviderApiConnection = DnsProviderApiConnection("test","test","test","test",List("name-servers"),List("bind","powerdns"))
  private val mockGenerateZoneRepository: GenerateZoneRepository = generateZoneRepository
  private var testZoneService: ZoneServiceAlgebra = _

  private val badAuth = AuthPrincipal(okUser, Seq())

  private val generateBindZoneAuthorized = GenerateZone(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    nameservers=Some(List("bind_ns")),

    admin_email=Some("test@test.com"),
    ttl=Some(3600),
    refresh=Some(6048000),
    retry=Some(86400),
    expire=Some(24192000),
    negative_cache_ttl=Some(6048000),
    response=Some(bindZoneGenerationResponse)
  )

  private val testRecordSOA = RecordSet(
    zoneId = okZone.id,
    name = "vinyldns",
    typ = RecordType.SOA,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records =
      List(SOAData(Fqdn("172.17.42.1."), "admin.test.com.", 1439234395, 10800, 3600, 604800, 38400))
  )
  private val testRecordNS = RecordSet(
    zoneId = okZone.id,
    name = "vinyldns",
    typ = RecordType.NS,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(NSData(Fqdn("172.17.42.1.")))
  )
  private val testRecordA = RecordSet(
    zoneId = okZone.id,
    name = "jenkins",
    typ = RecordType.A,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    records = List(AData("10.1.1.1"))
  )

  private val changeSetSOA = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordSOA, okZone))
  private val changeSetNS = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordNS, okZone))
  private val changeSetA = ChangeSet(RecordSetChangeGenerator.forAdd(testRecordA, okZone))

  private val mockBackendResolver = mock[BackendResolver]

  override protected def beforeEach(): Unit = {
    clearRecordSetRepo()
    clearZoneRepo()
    clearGenerateZoneRepo()

    waitForSuccess(zoneRepo.save(okZone))
    waitForSuccess(mockGenerateZoneRepository.save(generateBindZoneAuthorized))
    // Seeding records in DB
    executeWithinTransaction { db: DB =>
      IO {
        waitForSuccess(recordSetRepo.apply(db, changeSetSOA))
        waitForSuccess(recordSetRepo.apply(db, changeSetNS))
        waitForSuccess(recordSetRepo.apply(db, changeSetA))
      }
    }
    doReturn(NonEmptyList.one("func-test-backend")).when(mockBackendResolver).ids

    testZoneService = new ZoneService(
      zoneRepo,
      mock[GroupRepository],
      mock[UserRepository],
      mock[ZoneChangeRepository],
      mock[ZoneConnectionValidator],
      TestMessageQueue,
      new ZoneValidations(1000),
      new AccessValidations(),
      mockBackendResolver,
      NoOpCrypto.instance,
      mockMembershipService,
      mockDnsProviderApiConnection,
      mockGenerateZoneRepository
    )
  }

  override protected def afterAll(): Unit = {
    clearZoneRepo()
    clearRecordSetRepo()
  }

  "ZoneEntity" should {
    "reject a DeleteZone with bad auth" in {
      val result =
        testZoneService
          .deleteZone(okZone.id, badAuth)
          .value
          .unsafeToFuture()
      whenReady(result) { out =>
        leftValue(out) shouldBe a[NotAuthorizedError]
      }
    }
    "accept a DeleteZone" in {
      val removeARecord = ChangeSet(RecordSetChangeGenerator.forDelete(testRecordA, okZone))
      executeWithinTransaction { db: DB =>
        IO {
          waitForSuccess(recordSetRepo.apply(db, removeARecord))
        }
      }
      val result =
        testZoneService
          .deleteZone(okZone.id, okAuth)
          .value
          .unsafeToFuture()
          .mapTo[Either[Throwable, ZoneChange]]
      whenReady(result, timeout) { out =>
        out.isRight shouldBe true
        val change = out.toOption.get
        change.zone.id shouldBe okZone.id
        change.changeType shouldBe ZoneChangeType.Delete
      }
    }
  }

  "Generate Zone" should {
    "return a zone with appropriate response" in {
      val result =
        testZoneService
          .getGenerateZoneByName(okZone.name, okAuth)
          .value
          .unsafeRunSync()
      result shouldBe Right(generateBindZoneAuthorized)
    }

    "return a ZoneNotFoundError for zone does not exists" in {
      val result =
        testZoneService
          .getGenerateZoneByName(abcZone.name, abcAuth)
          .value
          .unsafeRunSync()
      result shouldBe Left(ZoneNotFoundError("Zone with name abc.zone.recordsets. does not exists"))
    }

    "return a name servers with appropriate response" in {
      val result =
        testZoneService
          .dnsNameServers()
          .value
          .unsafeRunSync()
      result shouldBe Right(
        List("name-servers")
      )
    }
    "return a allowed providers with appropriate response" in {
      val result =
        testZoneService
          .allowedDNSProviders()
          .value
          .unsafeRunSync()
      result shouldBe Right(
        List("bind","powerdns")
      )
    }
  }

  "getBackendIds" should {
    "return backend ids in config" in {
      testZoneService.getBackendIds().value.unsafeRunSync() shouldBe Right(
        List("func-test-backend")
      )
    }
  }

  private def waitForSuccess[T](f: => IO[T]): T = {
    val waiting = f.unsafeToFuture().recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
