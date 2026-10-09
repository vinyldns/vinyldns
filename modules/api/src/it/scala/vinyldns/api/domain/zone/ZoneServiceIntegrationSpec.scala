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
import org.json4s.{JArray, JInt, JString, JValue}
import org.json4s.JsonDSL._

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
import vinyldns.api.config.ValidEmailConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.membership.MembershipService
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.engine.TestMessageQueue
import vinyldns.mysql.TransactionProvider
import vinyldns.api.{MySqlApiIntegrationSpec, ResultHelpers}
import vinyldns.core.TestMembershipData.{abcAuth, okAuth, okGroup, okUser}
import vinyldns.core.TestZoneData.{abcZone, okZone}
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.{Encrypted, Fqdn}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.backend.BackendResolver
import vinyldns.core.domain.membership.{GroupChangeRepository, GroupRepository, MembershipRepository, UserRepository}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.domain.zone.generate._

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
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)
  // it-tests run inside the vinyldns-api-integration container (see test/api/integration/Makefile),
  // which attaches to the same docker network as the vinyldns-pdns-auth sibling container started
  // by that Makefile's start-pdns target - so the sibling's hostname is used, not localhost.
  val mockDnsProviderApiConnection = DnsProviderApiConnection(
    providers = Map(
      "powerdns" -> DnsProviderConfig(
        endpoints = Map(
          "create-zone" -> "http://vinyldns-pdns-auth:19005/api/v1/servers/localhost/zones",
          "delete-zone" -> "http://vinyldns-pdns-auth:19005/api/v1/servers/localhost/zones/{{zoneName}}",
          "update-zone" -> "http://vinyldns-pdns-auth:19005/api/v1/servers/localhost/zones/{{zoneName}}"
        ),
        requestTemplates = Map(
          "create-zone" -> """
        {
          "name": "{{zoneName}}",
          "kind": "{{kind}}",
          "masters": "{{masters}}",
          "nameservers": "{{nameservers}}"
        }
        """,
          "update-zone" -> """
        {
          "name": "{{zoneName}}",
          "kind": "{{kind}}",
          "masters": "{{masters}}",
          "nameservers": "{{nameservers}}"
        }
        """
        ),
        schemas = Map(
          "create-zone" -> """{ "$schema": "https://json-schema.org/draft/2020-12/schema", "title": "PowerDNS Create Zone", "type": "object", "required": ["kind", "nameservers"], "properties": { "kind": { "type": "string", "enum": ["Native", "Master"] }, "nameservers": { "type": "array", "minItems": 1, "items": { "type": "string", "pattern": "^[a-zA-Z0-9.-]+\\.$" } }, "masters": { "type": "array", "items": { "type": "string" } } }, "additionalProperties": false }""",
          "update-zone" -> """{ "$schema": "https://json-schema.org/draft/2020-12/schema", "title": "PowerDNS Update Zone", "type": "object", "properties": { "kind": { "type": "string", "enum": ["Native", "Master"] }, "masters": { "type": "array", "items": { "type": "string" } } }, "additionalProperties": false }"""
        ),
        apiKey = Encrypted("vinyldns_pdns_auth_api_key")
      )
    ),

    nameServers = List("ns1.example.com.", "ns2.example.com."),
    allowedProviders = List("powerdns")
  )

  // The bind zones API (utils/manage_vinyldns_zones_bind_api.py) runs colocated with this test
  // suite on localhost:19000, and bind's own port is also published to the host by docker-compose.
  val mockBindDnsProviderApiConnection = DnsProviderApiConnection(
    providers = Map(
      "bind" -> DnsProviderConfig(
        endpoints = Map(
          "create-zone" -> "http://localhost:19000/api/zones/generate",
          "delete-zone" -> "http://localhost:19000/api/zones/delete?zoneName={{zoneName}}",
          "update-zone" -> "http://localhost:19000/api/zones/update"
        ),
        requestTemplates = Map(
          "create-zone" -> """
        {
          "zoneName": "{{zoneName}}",
          "nameservers": "{{nameservers}}",
          "admin_email": "{{admin_email}}"
        }
        """,
          "update-zone" -> """
        {
          "zoneName": "{{zoneName}}",
          "nameservers": "{{nameservers}}",
          "admin_email": "{{admin_email}}"
        }
        """
        ),
        schemas = Map(
          "create-zone" -> """{ "$schema": "https://json-schema.org/draft/2020-12/schema", "title": "BIND Create Zone Request", "type": "object", "required": ["nameservers", "admin_email"], "properties": { "nameservers": { "type": "array", "items": { "type": "string" }, "minItems": 1 }, "admin_email": { "type": "string", "format": "email" } }, "additionalProperties": false }""",
          "update-zone" -> """{ "$schema": "https://json-schema.org/draft/2020-12/schema", "title": "BIND Update Zone Request", "type": "object", "required": ["nameservers", "admin_email"], "properties": { "nameservers": { "type": "array", "items": { "type": "string" }, "minItems": 1 }, "admin_email": { "type": "string", "format": "email" } }, "additionalProperties": false }"""
        ),
        apiKey = Encrypted("bind-api-key")
      )
    ),
    nameServers = List("172.17.42.1."),
    allowedProviders = List("bind")
  )

  private val mockGenerateZoneRepository: GenerateZoneRepository = generateZoneRepository
  private var testZoneService: ZoneServiceAlgebra = _
  private var testGenerateZoneService: GenerateZoneServiceAlgebra = _
  private var testGenerateZoneServiceBind: GenerateZoneServiceAlgebra = _

  private val badAuth = AuthPrincipal(okUser, Seq())

  // Real (not mocked) MembershipService so emailValidation actually runs for the bind
  // create/update/delete network flow tests below; the repos it touches otherwise aren't exercised.
  private val mockGroupRepoForGenerate = mock[GroupRepository]
  private val realMembershipServiceForGenerate = new MembershipService(
    mockGroupRepoForGenerate,
    mock[UserRepository],
    mock[MembershipRepository],
    zoneRepo,
    mock[GroupChangeRepository],
    recordSetRepo,
    ValidEmailConfig(valid_domains = List("test.com"), 2)
  )

  val bindZoneGenerationResponse: ZoneGenerationResponse =
    ZoneGenerationResponse(Some(200),Some("bind"), Some(("response" -> "success"): JValue), GenerateZoneChangeType.Create)
  val pdnsZoneGenerationResponse: ZoneGenerationResponse =
    ZoneGenerationResponse(Some(200),Some("powerdns"), Some(("response" -> "success"): JValue), GenerateZoneChangeType.Create)

  val bindProviderParams: Map[String, JValue] = Map(
    "nameservers" -> JArray(List(JString("bind_ns"))),
    "admin_email" -> JString("test@test.com"),
    "ttl" -> JInt(3600),
    "refresh" -> JInt(6048000),
    "retry" -> JInt(86400),
    "expire" -> JInt(24192000),
    "negative_cache_ttl" -> JInt(6048000)
  )

  val powerDNSProviderParams: Map[String, JValue] = Map(
    "nameservers" -> JArray(List(JString("bind_ns"))),
    "kind"-> JString("Master"),
  )

  private val generateBindZoneAuthorized = GenerateZone(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    providerParams = bindProviderParams,
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

    doReturn(IO.pure(Some(okGroup))).when(mockGroupRepoForGenerate).getGroup(okGroup.id)

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
      mockMembershipService
    )
    testGenerateZoneService = new GenerateZoneService(
      zoneRepo,
      mockGroupRepoForGenerate,
      mockGenerateZoneRepository,
      new ZoneValidations(1000),
      new AccessValidations(),
      NoOpCrypto.instance,
      realMembershipServiceForGenerate,
      mockDnsProviderApiConnection,
      GenerateZoneService.providerBlocker()
    )
    testGenerateZoneServiceBind = new GenerateZoneService(
      zoneRepo,
      mockGroupRepoForGenerate,
      mockGenerateZoneRepository,
      new ZoneValidations(1000),
      new AccessValidations(),
      NoOpCrypto.instance,
      realMembershipServiceForGenerate,
      mockBindDnsProviderApiConnection,
      GenerateZoneService.providerBlocker()
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
        testGenerateZoneService
          .getGenerateZoneByName(okZone.name, okAuth)
          .value
          .unsafeRunSync()
      result shouldBe Right(generateBindZoneAuthorized)
    }

    "return a ZoneNotFoundError for zone does not exists" in {
      val result =
        testGenerateZoneService
          .getGenerateZoneByName(abcZone.name, abcAuth)
          .value
          .unsafeRunSync()
      result shouldBe Left(ZoneNotFoundError("Zone with name abc.zone.recordsets. does not exists"))
    }

    "return a name servers with appropriate response" in {
      val result =
        testGenerateZoneService
          .dnsNameServers()
          .value
          .unsafeRunSync()
      result shouldBe Right(
        List("ns1.example.com.", "ns2.example.com.")
      )
    }
    "return a allowed providers with appropriate response" in {
      val result =
        testGenerateZoneService
          .allowedDNSProviders()
          .value
          .unsafeRunSync()
      result shouldBe Right(
        List("powerdns")
      )
    }

    // Exercises the full network round-trip against the real pdns-auth container over the
    // compose network (see the caveat on mockDnsProviderApiConnection above).
    "create, update, and delete a powerdns zone end-to-end over the network" in {
      val pdnsZoneName = "it-pdns-network-test.zone."
      val pdnsProviderParams: Map[String, JValue] = Map(
        "kind" -> JString("Native"),
        "nameservers" -> JArray(List(JString("ns1.example.com.")))
      )
      val createInput =
        ZoneGenerationInput(okGroup.id, "test@test.com", "powerdns", pdnsZoneName, pdnsProviderParams)

      // Regardless of where an assertion below fails, make sure the pdns-auth zone doesn't linger
      // and cause a 409 Conflict on the next run.
      try {
        val createResult =
          testGenerateZoneService.handleGenerateZoneRequest(createInput, okAuth).value.unsafeRunSync()
        withClue(s"create failed: ${createResult.swap.toOption}") {
          createResult.isRight shouldBe true
        }
        val created = createResult.toOption.get
        created.response.flatMap(_.responseCode) shouldBe Some(201)
        created.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Create)

        // PowerDNS's update-zone schema only allows kind/masters, not nameservers.
        val updatedParams: Map[String, JValue] = Map("kind" -> JString("Master"))
        val updateInput = createInput.copy(providerParams = updatedParams)
        val updateResult =
          testGenerateZoneService.handleUpdateGeneratedZoneRequest(updateInput, okAuth).value.unsafeRunSync()
        withClue(s"update failed: ${updateResult.swap.toOption}") {
          updateResult.isRight shouldBe true
        }
        val updated = updateResult.toOption.get
        updated.response.flatMap(_.responseCode) shouldBe Some(204)
        updated.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Update)

        val deleteResult =
          testGenerateZoneService.handleDeleteGeneratedZoneRequest(created.id, okAuth).value.unsafeRunSync()
        withClue(s"delete failed: ${deleteResult.swap.toOption}") {
          deleteResult.isRight shouldBe true
        }
        val deleted = deleteResult.toOption.get
        deleted.response.flatMap(_.responseCode) shouldBe Some(204)
        deleted.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Delete)

        mockGenerateZoneRepository.getGenerateZoneByName(pdnsZoneName).unsafeRunSync() shouldBe None
      } finally {
        // Best-effort cleanup so a failed assertion above doesn't leave the zone in pdns-auth,
        // which would 409 Conflict on the next run.
        mockGenerateZoneRepository.getGenerateZoneByName(pdnsZoneName).unsafeRunSync().foreach { existing =>
          testGenerateZoneService.handleDeleteGeneratedZoneRequest(existing.id, okAuth).value.unsafeRunSync()
        }
      }
    }

    // Exercises the full network round-trip against the real bind zones API (localhost:19000),
    // which runs colocated with this test suite - unlike pdns-auth, which is a separate container.
    "create, update, and delete a bind zone end-to-end over the network" in {
      val bindZoneName = "it-bind-network-test.zone."
      val bindProviderParams: Map[String, JValue] = Map(
        "nameservers" -> JArray(List(JString("172.17.42.1."), JString("ns1.example.com."))),
        "admin_email" -> JString("admin@test.com")
      )
      val createInput =
        ZoneGenerationInput(okGroup.id, "test@test.com", "bind", bindZoneName, bindProviderParams)

      // Regardless of where an assertion below fails, make sure the bind zone file/config doesn't
      // linger and interfere with the next run.
      try {
        val createResult =
          testGenerateZoneServiceBind.handleGenerateZoneRequest(createInput, okAuth).value.unsafeRunSync()
        withClue(s"create failed: ${createResult.swap.toOption}") {
          createResult.isRight shouldBe true
        }
        val created = createResult.toOption.get
        created.response.flatMap(_.responseCode) shouldBe Some(200)
        created.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Create)

        val updatedParams = bindProviderParams + ("admin_email" -> JString("updated@test.com"))
        val updateInput = createInput.copy(providerParams = updatedParams)
        val updateResult =
          testGenerateZoneServiceBind.handleUpdateGeneratedZoneRequest(updateInput, okAuth).value.unsafeRunSync()
        withClue(s"update failed: ${updateResult.swap.toOption}") {
          updateResult.isRight shouldBe true
        }
        val updated = updateResult.toOption.get
        updated.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Update)
        updated.providerParams shouldBe updatedParams

        val deleteResult =
          testGenerateZoneServiceBind.handleDeleteGeneratedZoneRequest(created.id, okAuth).value.unsafeRunSync()
        withClue(s"delete failed: ${deleteResult.swap.toOption}") {
          deleteResult.isRight shouldBe true
        }
        deleteResult.toOption.get.response.map(_.changeType) shouldBe Some(GenerateZoneChangeType.Delete)

        mockGenerateZoneRepository.getGenerateZoneByName(bindZoneName).unsafeRunSync() shouldBe None
      } finally {
        mockGenerateZoneRepository.getGenerateZoneByName(bindZoneName).unsafeRunSync().foreach { existing =>
          testGenerateZoneServiceBind.handleDeleteGeneratedZoneRequest(existing.id, okAuth).value.unsafeRunSync()
        }
      }
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
