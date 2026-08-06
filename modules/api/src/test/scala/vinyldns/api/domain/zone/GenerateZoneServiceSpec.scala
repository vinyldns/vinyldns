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

import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{doReturn, reset}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import cats.effect._
import org.json4s.JString
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import vinyldns.api.config.ValidEmailConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.membership.MembershipService
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.api.repository.TestDataLoader
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._
import vinyldns.core.domain.zone.generate._
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.Encrypted

import java.net.HttpURLConnection

class GenerateZoneServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with EitherValues {

  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupRepo = mock[GroupRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMembershipRepo = mock[MembershipRepository]
  private val mockGroupChangeRepo = mock[GroupChangeRepository]
  private val mockRecordSetRepo = mock[RecordSetRepository]
  private val mockGenerateZoneRepository = mock[GenerateZoneRepository]
  private val mockValidEmailConfig = ValidEmailConfig(valid_domains = List("test.com", "*dummy.com"), 2)
  private val abcGeneratedZoneSummary = GenerateZoneSummaryInfo(abcGenerateZone, abcGroup.name, AccessLevel.Delete)
  private val xyzGeneratedZoneSummary = GenerateZoneSummaryInfo(xyzGenerateZone, xyzGroup.name, AccessLevel.NoAccess)

  private val mockMembershipService = new MembershipService(
    mockGroupRepo,
    mockUserRepo,
    mockMembershipRepo,
    mockZoneRepo,
    mockGroupChangeRepo,
    mockRecordSetRepo,
    mockValidEmailConfig
  )

  private val underTest = new GenerateZoneService(
    mockZoneRepo,
    mockGroupRepo,
    mockGenerateZoneRepository,
    new ZoneValidations(1000),
    new AccessValidations(),
    NoOpCrypto.instance,
    mockMembershipService,
    mockPowerDNSProviderApiConnection
  ) {
    override def createConnection(endpoint: String): HttpURLConnection = mockConnection
  }

  // No createConnection override so the real timeout-configured connection is exercised.
  private val underTestNew = new GenerateZoneService(
    mockZoneRepo,
    mockGroupRepo,
    mockGenerateZoneRepository,
    new ZoneValidations(1000),
    new AccessValidations(),
    NoOpCrypto.instance,
    mockMembershipService,
    mockPowerDNSProviderApiConnection
  )

  // Builds a GenerateZoneService backed by a specific provider connection, with createConnection
  // stubbed so tests never open real sockets.
  private def zoneServiceWith(conn: DnsProviderApiConnection): GenerateZoneService =
    new GenerateZoneService(
      mockZoneRepo,
      mockGroupRepo,
      mockGenerateZoneRepository,
      new ZoneValidations(1000),
      new AccessValidations(),
      NoOpCrypto.instance,
      mockMembershipService,
      conn
    ) {
      override def createConnection(endpoint: String): HttpURLConnection = mockConnection
    }

  override protected def beforeEach(): Unit = {
    reset(mockGroupRepo, mockZoneRepo)
    doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
  }

  "createDnsZoneService" should {
    "return a valid HttpURLConnection on successful zone creation" in {
      val dnsApiKey = "test-api-key"
      val dnsOperation = "create-zone"
      val request = """{"zone": "example.com"}"""

      val result = underTest.createDnsZoneService(dnsApiKey,dnsOperation, Some(request), mockConnection).toOption.get
      result shouldBe a[HttpURLConnection]
      result.getResponseCode shouldBe 200
    }

    "return an error connection on failure" in {
      val dnsApiKey = "test-api-key"
      val dnsOperation = "create-zone"
      val request = """{"zone": "example.com"}"""

      val result = underTest.createDnsZoneService(dnsApiKey,dnsOperation, Some(request), mockInvalidConnection).toOption.get
      result.getResponseCode shouldBe 400
    }
  }

  "createConnection" should {
    "set connect and read timeouts so a hung provider cannot block indefinitely" in {
      val connection = underTestNew.createConnection("http://localhost:65500/zones")
      connection.getConnectTimeout should be > 0
      connection.getReadTimeout should be > 0
    }
  }

  "Generating Zones" should {
    "reject the request when the provider has no schema for the operation (fail closed)" in {
      val noSchemaConnection = DnsProviderApiConnection(
        providers = Map(
          "powerdns" -> DnsProviderConfig(
            endpoints = Map("create-zone" -> "http://localhost:19005/zones"),
            requestTemplates = Map.empty,
            schemas = Map.empty, // no schema configured for create-zone
            apiKey = Encrypted("test-api-key")
          )
        ),
        nameServers = List.empty,
        allowedProviders = List("powerdns")
      )
      val svc = zoneServiceWith(noSchemaConnection)

      val result =
        svc
          .handleGenerateZoneRequest(generatePdnsZoneAuthorized.copy(groupId = okGroup.id), okAuth)
          .value
          .unsafeRunSync()
          .swap
          .toOption
          .get
      result.getMessage should include("No request-validation schema is configured")
    }

    "return an error response for provider not supported" in {
      doReturn(IO.pure(None)).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      val result =
        underTest.handleGenerateZoneRequest(generateBindZoneAuthorized, okAuth).value.unsafeRunSync().swap.toOption.get
      result shouldBe InvalidRequest(s"Unsupported DNS provider: ${generateBindZoneAuthorized.provider}")
    }

    "fail cleanly when the provider has no endpoint configured for the operation" in {
      val pdnsConfig = mockPowerDNSProviderApiConnection.providers("powerdns")
      val noEndpointConnection = mockPowerDNSProviderApiConnection.copy(
        providers = Map("powerdns" -> pdnsConfig.copy(endpoints = Map.empty))
      )
      val result =
        zoneServiceWith(noEndpointConnection)
          .handleGenerateZoneRequest(generatePdnsZoneAuthorized, okAuth)
          .value
          .unsafeRunSync()
          .swap
          .toOption
          .get
      result.getMessage should include("No endpoint is configured for operation 'create-zone'")
    }

    "reject an endpoint template with unresolved placeholders instead of calling the provider" in {
      val pdnsConfig = mockPowerDNSProviderApiConnection.providers("powerdns")
      val badEndpointConnection = mockPowerDNSProviderApiConnection.copy(
        providers = Map(
          "powerdns" -> pdnsConfig.copy(
            endpoints = Map("create-zone" -> "http://localhost:19005/zones/{{missingKey}}")
          )
        )
      )
      val result =
        zoneServiceWith(badEndpointConnection)
          .handleGenerateZoneRequest(generatePdnsZoneAuthorized, okAuth)
          .value
          .unsafeRunSync()
          .swap
          .toOption
          .get
      result.getMessage should include("unresolved placeholders")
      result.getMessage should include("{{missingKey}}")
    }

    "return an valid response for valid request" in {
      doReturn(IO.pure(None)).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(None)).when(mockZoneRepo).getZoneByName(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .save(any[GenerateZone])

      val result =
        underTest.handleGenerateZoneRequest(generatePdnsZoneAuthorized, okAuth).value.unsafeRunSync().toOption.get

      result.zoneName shouldBe generatePdnsZoneAuthorized.zoneName
      result.providerParams shouldBe generatePdnsZoneAuthorized.providerParams
      result.provider shouldBe generatePdnsZoneAuthorized.provider
    }

    "reject generating a zone that collides with an existing VinylDNS-managed zone" in {
      doReturn(IO.pure(None)).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(Some(okZone))).when(mockZoneRepo).getZoneByName(anyString)

      val result =
        underTest.handleGenerateZoneRequest(generatePdnsZoneAuthorized, okAuth).value.unsafeRunSync().swap.toOption.get
      result shouldBe a[ZoneAlreadyExistsError]
    }

    "return an error response for invalid request" in {
      doReturn(IO.pure(None)).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .save(any[GenerateZone])

      val result =
        underTest.handleGenerateZoneRequest(generatePdnsInvalidZone, okAuth).value.unsafeRunSync().swap.toOption.get

      result shouldBe InvalidRequest("" +
        "JSON schema validation error: $: " +
        "property 'admin_email' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'expire' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'refresh' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'ttl' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'retry' is not defined in the schema and the schema does not allow additional properties; $: required " +
        "property 'kind' not found; $: property 'negative_cache_ttl' is not defined in the schema and the schema does not allow additional properties")
    }
  }

  "Update generated Zones" should {
    "return an error response for provider params not supported the correct provider" in {
      doReturn(IO.pure(Some(generateBindZone))).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      val result =
        underTest.handleUpdateGeneratedZoneRequest(generateBindZoneAuthorized.copy(groupId = okGroup.id), okAuth).value.unsafeRunSync().swap.toOption.get
      result shouldBe InvalidRequest(s"Unsupported DNS provider: ${generateBindZoneAuthorized.provider}")
    }

    "return an valid response for valid request" in {
      doReturn(IO.pure(Some(generatePdnsZone))).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .save(any[GenerateZone])

      val result =
        underTest.handleUpdateGeneratedZoneRequest(updatePdnsZoneAuthorized.copy(groupId = okGroup.id,providerParams = Map(
          "kind"-> JString("Native")
        )), okAuth).value.unsafeRunSync().toOption.get
      result.zoneName shouldBe updatePdnsZoneAuthorized.zoneName
      // PUT semantics: request providerParams replace the stored set (nameservers dropped).
      result.providerParams shouldBe Map("kind" -> JString("Native"))
      result.provider shouldBe updatePdnsZoneAuthorized.provider
      result.groupId shouldBe okGroup.id
    }

    "return an error response for invalid request in provider params" in {
      doReturn(IO.pure(Some(generatePdnsZone))).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .save(any[GenerateZone])

      val result =
        underTest.handleUpdateGeneratedZoneRequest(generatePdnsInvalidZone.copy(groupId = okGroup.id), okAuth).value.unsafeRunSync().swap.toOption.get

      result shouldBe InvalidRequest("" +
        "JSON schema validation error: $: " +
        "property 'admin_email' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'nameservers' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'expire' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'refresh' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'ttl' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'retry' is not defined in the schema and the schema does not allow additional properties; $: " +
        "property 'negative_cache_ttl' is not defined in the schema and the schema does not allow additional properties")
    }
    "reject changing the provider of an existing generated zone" in {
      doReturn(IO.pure(Some(generatePdnsZone))).when(mockGenerateZoneRepository).getGenerateZoneByName(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .save(any[GenerateZone])

      val result =
        underTest.handleUpdateGeneratedZoneRequest(updatePdnsZoneAuthorized.copy(provider = "bind"), okAuth).value.unsafeRunSync().swap.toOption.get
      result shouldBe InvalidRequest(
        s"Cannot change the DNS provider of an existing generated zone " +
          s"(current: '${generatePdnsZone.provider}', requested: 'bind')."
      )
    }
  }

  "Deleting Generated Zones" should {
    "return an delete zone response" in {
      doReturn(IO.pure(Some(generatePdnsZone))).when(mockGenerateZoneRepository).getGenerateZoneById(anyString)
      doReturn(IO.pure(generatePdnsZone))
        .when(mockGenerateZoneRepository)
        .delete(any[GenerateZone])

      val result =
        underTest.handleDeleteGeneratedZoneRequest(generatePdnsZone.id, okAuth).value.unsafeRunSync().toOption.get
      result.zoneName shouldBe generatePdnsZoneAuthorized.zoneName
    }

    "return an error if the user is not authorized for the zone" in {
      doReturn(IO.pure(Some(generatePdnsZone))).when(mockGenerateZoneRepository).getGenerateZoneById(anyString)

      val noAuth = AuthPrincipal(TestDataLoader.okUser, Seq())
      val error =
        underTest.handleDeleteGeneratedZoneRequest(generatePdnsZone.id, noAuth).value.unsafeRunSync().swap.toOption.get
      error shouldBe a[NotAuthorizedError]
    }
  }

  "ListGeneratedZones" should {
    "not fail with no zones returned" in {
      doReturn(IO.pure(ListGeneratedZonesResults(List())))
        .when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse = underTest.listGeneratedZones(abcAuth).value.unsafeRunSync().toOption.get
      result.zones shouldBe List()
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "return the appropriate zones" in {
      doReturn(IO.pure(ListGeneratedZonesResults(List(abcGenerateZone))))
        .when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse = underTest.listGeneratedZones(abcAuth).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "return the appropriate zones for a multi-zone result" in {
      doReturn(IO.pure(ListGeneratedZonesResults(List(abcGenerateZone, xyzGenerateZone))))
        .when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "name filter must be used to return zones by admin group name, when search by admin group option is true" in {
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroupsByName(any[String])
      doReturn(IO.pure(ListGeneratedZonesResults(List(abcGenerateZone), zonesFilter = Some("abcGroup"))))
        .when(mockGenerateZoneRepository)
        .listGeneratedZonesByAdminGroupIds(abcAuth, None, 100, Set(abcGroup.id))
      doReturn(IO.pure(Set(abcGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      // When searchByAdminGroup is true, zones are filtered by admin group name given in nameFilter
      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, Some("abcGroup"), None, 100, searchByAdminGroup = true).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe Some("abcGroup")
      result.nextId shouldBe None
    }

    "name filter must be used to return zone by zone name, when search by admin group option is false" in {
      doReturn(IO.pure(Set(abcGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])
      doReturn(IO.pure(ListGeneratedZonesResults(List(abcGenerateZone), zonesFilter = Some("abcZone"))))
        .when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, Some("abcZone"), None, 100)

      // When searchByAdminGroup is false, zone name given in nameFilter is returned
      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, Some("abcZone"), None, 100, searchByAdminGroup = false).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary)
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe Some("abcZone")
      result.nextId shouldBe None
    }

    "return Unknown group name if zone admin group cannot be found" in {
      doReturn(IO.pure(ListGeneratedZonesResults(List(abcGenerateZone, xyzGenerateZone))))
        .when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, None, 100)
      doReturn(IO.pure(Set(okGroup))).when(mockGroupRepo).getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth).value.unsafeRunSync().toOption.get
      val expectedZones =
        List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary).map(_.copy(groupName = "Unknown group name"))
      result.zones shouldBe expectedZones
      result.maxItems shouldBe 100
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe None
    }

    "set the nextId appropriately" in {
      doReturn(
        IO.pure(
          ListGeneratedZonesResults(
            List(abcGenerateZone, xyzGenerateZone),
            maxItems = 2,
            nextId = Some("zone2.")
          )
        )
      ).when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, None, 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary)
      result.maxItems shouldBe 2
      result.startFrom shouldBe None
      result.nameFilter shouldBe None
      result.nextId shouldBe Some("zone2.")
    }

    "set the nameFilter when provided" in {
      doReturn(
        IO.pure(
          ListGeneratedZonesResults(
            List(abcGenerateZone, xyzGenerateZone),
            zonesFilter = Some("foo"),
            maxItems = 2,
            nextId = Some("zone2.")
          )
        )
      ).when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, Some("foo"), None, 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, nameFilter = Some("foo"), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary)
      result.nameFilter shouldBe Some("foo")
      result.nextId shouldBe Some("zone2.")
      result.maxItems shouldBe 2
    }

    "set the startFrom when provided" in {
      doReturn(
        IO.pure(
          ListGeneratedZonesResults(
            List(abcGenerateZone, xyzGenerateZone),
            startFrom = Some("zone4."),
            maxItems = 2
          )
        )
      ).when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, Some("zone4."), 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary)
      result.startFrom shouldBe Some("zone4.")
    }

    "set the nextId to be the current result set size plus the start from" in {
      doReturn(
        IO.pure(
          ListGeneratedZonesResults(
            List(abcGenerateZone, xyzGenerateZone),
            startFrom = Some("zone4."),
            maxItems = 2,
            nextId = Some("zone6.")
          )
        )
      ).when(mockGenerateZoneRepository)
        .listGenerateZones(abcAuth, None, Some("zone4."), 2)
      doReturn(IO.pure(Set(abcGroup, xyzGroup)))
        .when(mockGroupRepo)
        .getGroups(any[Set[String]])

      val result: ListGeneratedZonesResponse =
        underTest.listGeneratedZones(abcAuth, startFrom = Some("zone4."), maxItems = 2).value.unsafeRunSync().toOption.get
      result.zones shouldBe List(abcGeneratedZoneSummary, xyzGeneratedZoneSummary)
      result.nextId shouldBe Some("zone6.")
    }
  }
}
