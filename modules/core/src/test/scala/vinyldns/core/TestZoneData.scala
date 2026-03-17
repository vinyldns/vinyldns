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

package vinyldns.core

import vinyldns.core.domain.zone._
import TestMembershipData._
import org.json4s.{JArray, JInt, JString, JValue}
import vinyldns.core.domain.Encrypted
import org.json4s.JsonDSL._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, ProtocolException, URL}
import java.time.Instant
import java.time.temporal.ChronoUnit

object TestZoneData {

  /* ZONE CONNECTIONS */
  val testConnection: Option[ZoneConnection] = Some(
    ZoneConnection("vinyldns.", "vinyldns.", Encrypted("nzisn+4G2ldMn0q1CV3vsg=="), "10.1.1.1")
  )

  /* ZONES */
  val okZone: Zone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    adminGroupId = okGroup.id,
    connection = testConnection
  )
  val dottedZone: Zone = Zone("dotted.xyz.", "dotted@xyz.com", adminGroupId = xyzGroup.id)
  val dotZone: Zone = Zone("dot.xyz.", "dotted@xyz.com", adminGroupId = xyzGroup.id)
  val abcZone: Zone = Zone("abc.zone.recordsets.", "test@test.com", adminGroupId = abcGroup.id)
  val xyzZone: Zone = Zone("xyz.", "abc@xyz.com", adminGroupId = xyzGroup.id)
  val zoneIp4: Zone = Zone("0.162.198.in-addr.arpa.", "test@test.com", adminGroupId = abcGroup.id)
  val zoneIp6: Zone =
    Zone("1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.", "test@test.com", adminGroupId = abcGroup.id)

  val zoneActive: Zone = Zone(
    "some.zone.name.",
    "test@test.com",
    adminGroupId = okGroup.id,
    status = ZoneStatus.Active,
    connection = testConnection
  )

  val abcZoneDeleted: Zone = Zone("abc.zone.recordsets.", "test@test.com", adminGroupId = abcGroup.id, status = ZoneStatus.Deleted)
  val xyzZoneDeleted: Zone = Zone("xyz.zone.recordsets.", "abc@xyz.com", adminGroupId = xyzGroup.id, status = ZoneStatus.Deleted)

  val zoneDeleted: Zone = Zone(
    "some.deleted.zone.",
    "test@test.com",
    adminGroupId = abcGroup.id,
    status = ZoneStatus.Deleted,
    connection = testConnection
  )

  val zoneDeletedOkGroup: Zone = Zone(
    "some.deleted.zone.",
    "test@test.com",
    adminGroupId = okGroup.id,
    status = ZoneStatus.Deleted,
    connection = testConnection
  )

  val zoneNotAuthorized: Zone = Zone("not.auth.zone.", "test@test.com", adminGroupId = "no-id")

  val sharedZone: Zone =
    zoneActive.copy(id = "sharedZoneId", shared = true, adminGroupId = abcGroup.id)



  val mockPowerDNSProviderApiConnection = DnsProviderApiConnection(
    providers = Map(
      "powerdns" -> DnsProviderConfig(
        endpoints = Map(
          "create-zone" -> "http://localhost:19005/api/v1/servers/localhost/zones",
          "delete-zone" -> "http://localhost:19005/api/v1/servers/localhost/zones/{{zoneName}}",
          "update-zone" -> "http://localhost:19005/api/v1/servers/localhost/zones/{{zoneName}}"
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
          "create-zone" -> """{
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "PowerDNS Create Zone",
                    "type": "object",
                    "required": ["kind", "nameservers"],
                    "properties": {
                      "kind": {
                        "type": "string",
                        "enum": ["Native", "Master"]
                      },
                      "nameservers": {
                        "type": "array",
                        "minItems": 1,
                        "items": { "type": "string", "pattern": "^[a-zA-Z0-9.-]+\\.$" }
                      },
                      "masters": {
                        "type": "array",
                        "items": { "type": "string" }
                      }
                    },
                    "additionalProperties": false
                  }""",
          "update-zone" -> """{
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "PowerDNS Update Zone",
                    "type": "object",
                    "properties": {
                      "kind": {
                        "type": "string",
                        "enum": ["Native", "Master"]
                      },
                      "masters": {
                        "type": "array",
                        "items": { "type": "string" }
                      }
                    },
                    "additionalProperties": false
                  }"""
        ),
        apiKey = "test-api-key"
      )
    ),
    nameServers = List("ns1.parent.com.,ns2.parent.com."),
    allowedProviders = List("powerdns")
  )

  /* ACL RULES */
  val userAclRule: ACLRule = ACLRule(AccessLevel.Read, userId = Some("someUser"))

  val userAclRuleInfo: ACLRuleInfo = ACLRuleInfo(userAclRule, None)

  val groupAclRule: ACLRule = ACLRule(AccessLevel.Read, groupId = Some("someGroup"))

  val baseAclRuleInfo: ACLRuleInfo =
    ACLRuleInfo(AccessLevel.Read, Some("desc"), None, Some("group"), None, Set.empty)
  val baseAclRule: ACLRule = ACLRule(baseAclRuleInfo)

  /* ZONE CHANGES */
  val zoneChangePending: ZoneChange =
    ZoneChange(okZone, "ok", ZoneChangeType.Update, ZoneChangeStatus.Pending)

  val zoneCreate: ZoneChange = ZoneChange(
    okZone,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Synced,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusMillis(1000)
  )

  val zoneUpdate: ZoneChange = zoneChangePending.copy(status = ZoneChangeStatus.Synced)

  val abcDeletedZoneChange: ZoneChange = ZoneChange(
    abcZoneDeleted,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Synced,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusMillis(1000)
  )

  val xyzDeletedZoneChange: ZoneChange = ZoneChange(
    xyzZoneDeleted,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Synced,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusMillis(1000)
  )

  def makeTestPendingZoneChange(zone: Zone): ZoneChange =
    ZoneChange(zone, "userId", ZoneChangeType.Update, ZoneChangeStatus.Pending)


val createZoneAuthorized = ConnectZoneInput(
    "ok.zone.recordsets.",
    "test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  val bindZoneGenerationResponse: ZoneGenerationResponse =
    ZoneGenerationResponse(Some(200),Some("bind"), Some(("response" -> "success"): JValue), GenerateZoneChangeType.Create)
  val pdnsZoneGenerationResponse: ZoneGenerationResponse =
    ZoneGenerationResponse(Some(200),Some("powerdns"), Some(("response" -> "success"): JValue), GenerateZoneChangeType.Create)

  val bindProviderParams: Map[String, JValue] = Map(
    "nameservers" -> JArray(List(JString("ns1.parent.com."))),
    "admin_email" -> JString("test@test.com"),
    "ttl" -> JInt(3600),
    "refresh" -> JInt(6048000),
    "retry" -> JInt(86400),
    "expire" -> JInt(24192000),
    "negative_cache_ttl" -> JInt(6048000)
  )

  val powerDNSProviderParams: Map[String, JValue] = Map(
    "nameservers" -> JArray(List(JString("ns1.parent.com."))),
    "kind"-> JString("Master"),
  )

  val updatePowerDNSProviderParams: Map[String, JValue] = Map(
    "kind"-> JString("Master")
  )

  val generateBindZoneAuthorized = ZoneGenerationInput(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse)
  )

  val generatePdnsZoneAuthorized = ZoneGenerationInput(
    okGroup.id,
    "test@test.com",
    "powerdns",
    okZone.name,
    providerParams = powerDNSProviderParams,
    response=Some(pdnsZoneGenerationResponse)
  )

  val updatePdnsZoneAuthorized = ZoneGenerationInput(
    okGroup.id,
    "test@test.com",
    "powerdns",
    okZone.name,
    providerParams = updatePowerDNSProviderParams,
    response=Some(pdnsZoneGenerationResponse)
  )

  val generatePdnsInvalidZone = ZoneGenerationInput(
    okGroup.id,
    "test@test.com",
    "powerdns",
    okZone.name,
    providerParams = bindProviderParams,
    response=Some(pdnsZoneGenerationResponse)
  )

val updateZoneAuthorized = UpdateZoneInput(
    okZone.id,
    "ok.zone.recordsets.",
    "test@test.com",
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  val generateBindZone: GenerateZone = GenerateZone(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse),
    id = "bindZoneId"
  )
  val generatePdnsZone: GenerateZone = GenerateZone(
    okGroup.id,
    "test@test.com",
    "powerdns",
    okZone.name,
    providerParams = powerDNSProviderParams,
    response=Some(pdnsZoneGenerationResponse),
    id = "pDnsZoneId"
  )

  val updateBindZone: UpdateGenerateZoneInput = UpdateGenerateZoneInput(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse),
    id = "bindZoneId"
  )

  val inputBindZone: ZoneGenerationInput = ZoneGenerationInput(
    okGroup.id,
    "test@test.com",
    "bind",
    okZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse),
    id = "bindZoneId"
  )

  val abcGenerateZone = GenerateZone(
    abcGroup.id,
    "test@test.com",
    "bind",
    abcZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse)
  )

  val xyzGenerateZone = GenerateZone(
    xyzGroup.id,
    "test@test.com",
    "bind",
    xyzZone.name,
    providerParams = bindProviderParams,
    response=Some(bindZoneGenerationResponse)
  )

val mockConnection = new HttpURLConnection(new URL("http://valid-url")) {
    private val responseJson = """{"message": "Zone response success"}"""

    private var requestMethod: String = _
    private val outputBuffer = new ByteArrayOutputStream()

    override def disconnect(): Unit = {}
    override def usingProxy(): Boolean = false
    override def connect(): Unit = {}

    override def setRequestMethod(method: String): Unit = {
      requestMethod = method
    }

    override def getRequestMethod: String = requestMethod

    override def setDoOutput(flag: Boolean): Unit = {
      doOutput = flag
    }

    override def getOutputStream: OutputStream = {
      if (!doOutput) throw new ProtocolException("Output not enabled")
      outputBuffer
    }

    override def getResponseCode: Int = {
      // Simulate success for POST/PUT, error otherwise
      if (Set("POST", "PUT", "DELETE").contains(requestMethod)) 200 else 500
    }

    override def getInputStream: InputStream = {
      new ByteArrayInputStream(responseJson.getBytes("UTF-8"))
    }
    override def getResponseMessage: String = {
      if (getResponseCode == 200) "OK"
      else "Internal Server Error"
    }
  }

val mockInvalidConnection = new HttpURLConnection(new URL("http://invalid-url")) {
    private val errorJson = """{"error": "Invalid request: Unsupported DNS provider"}"""
    private val outputBuffer = new ByteArrayOutputStream()

    override def disconnect(): Unit = {}
    override def usingProxy(): Boolean = false
    override def connect(): Unit = {}

    override def getRequestMethod: String = "INVALID"

    override def setDoOutput(flag: Boolean): Unit = {
      doOutput = flag
    }

    override def getOutputStream: OutputStream = {
      if (!doOutput) throw new ProtocolException("Output not enabled")
      outputBuffer
    }

    override def getResponseCode: Int = 400

    override def getResponseMessage: String = "Bad Request"

    override def getInputStream: InputStream = {
      new ByteArrayInputStream(errorJson.getBytes("UTF-8"))
    }
  }

}
