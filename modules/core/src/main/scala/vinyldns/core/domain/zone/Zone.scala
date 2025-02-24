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

package vinyldns.core.domain.zone

import java.util.UUID
import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}

import java.time.temporal.ChronoUnit
import java.time.Instant
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.CannotConvert
import pureconfig.generic.auto._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.{Encrypted, Encryption}

import scala.collection.JavaConverters._

object ZoneStatus extends Enumeration {
  type ZoneStatus = Value
  val Active, Deleted, Syncing = Value
}

import vinyldns.core.domain.zone.ZoneStatus._

final case class Zone(
    name: String,
    email: String,
    status: ZoneStatus = ZoneStatus.Active,
    created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
    updated: Option[Instant] = None,
    id: String = UUID.randomUUID().toString,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None,
    account: String = "system",
    shared: Boolean = false,
    acl: ZoneACL = ZoneACL(),
    adminGroupId: String = "system",
    recurrenceSchedule: Option[String] = None,
    scheduleRequestor: Option[String] = None,
    latestSync: Option[Instant] = None,
    isTest: Boolean = false,
    backendId: Option[String] = None
) {
  val isIPv4: Boolean = name.toLowerCase.endsWith("in-addr.arpa.")
  val isIPv6: Boolean = name.toLowerCase.endsWith("ip6.arpa.")
  val isReverse: Boolean = isIPv4 || isIPv6

  def addACLRule(rule: ACLRule): Zone =
    this.copy(acl = acl.addRule(rule))

  def deleteACLRule(rule: ACLRule): Zone =
    this.copy(acl = acl.deleteRule(rule))

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("Zone: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("name=\"").append(name).append("\"; ")
    sb.append("account=\"").append(account).append("\"; ")
    sb.append("adminGroupId=\"").append(adminGroupId).append("\"; ")
    sb.append("status=\"").append(status.toString).append("\"; ")
    sb.append("shared=\"").append(shared).append("\"; ")
    sb.append("connection=\"").append(connection.toString).append("\"; ")
    sb.append("transferConnection=\"").append(transferConnection.toString).append("\"; ")
    sb.append("reverse=\"").append(isReverse).append("\"; ")
    sb.append("isTest=\"").append(isTest).append("\"; ")
    sb.append("created=\"").append(created).append("\"; ")
    recurrenceSchedule.map(sb.append("recurrenceSchedule=\"").append(_).append("\"; "))
    scheduleRequestor.map(sb.append("scheduleRequestor=\"").append(_).append("\"; "))
    updated.map(sb.append("updated=\"").append(_).append("\"; "))
    latestSync.map(sb.append("latestSync=\"").append(_).append("\"; "))
    sb.append("]")
    sb.toString
  }
}

object Zone {
  def apply(connectZoneInput: ConnectZoneInput, isTest: Boolean): Zone = {
    import connectZoneInput._

    Zone(
      name,
      email,
      connection = connection,
      transferConnection = transferConnection,
      shared = shared,
      acl = acl,
      adminGroupId = adminGroupId,
      backendId = backendId,
      isTest = isTest,
      recurrenceSchedule = recurrenceSchedule,
      scheduleRequestor = scheduleRequestor
    )
  }

  def apply(updateZoneInput: UpdateZoneInput, currentZone: Zone): Zone = {
    import updateZoneInput._

    currentZone.copy(
      name = name,
      email = email,
      connection = connection,
      transferConnection = transferConnection,
      shared = shared,
      acl = acl,
      adminGroupId = adminGroupId,
      backendId = backendId,
      recurrenceSchedule = recurrenceSchedule,
      scheduleRequestor = scheduleRequestor
    )
  }
}
final case class GenerateZone(
                               groupId: String,
                               provider: String, // "powerdns", "cloudflare", "google", "bind"
                               zoneName: String,
                               status: String,
                               serverId: Option[String] = None, // The ID of the sever (PowerDNS)
                               kind: Option[String] = None, // Zone type (PowerDNS/Cloudflare/Bind)
                               masters: Option[List[String]] = None, // Master servers (for slave zones, PowerDNS)
                               nameservers: Option[List[String]] = None, // NS records (PowerDNS)
                               description: Option[String] = None, // description (Google)
                               visibility: Option[String] = None, // Public or Private (Google)
                               accountId: Option[String] = None, // Account ID (Cloudflare)
                               projectId: Option[String] = None, // GCP Project ID (Google)
                               ns_ipaddress: Option[List[String]] = None, // NS IpAddress (Bind)
                               admin_email: Option[String] = None, // NS IpAddress (Bind)
                               ttl: Option[Int] = None, // TTL (Bind)
                               refresh: Option[Int] = None, // Refresh (Bind)
                               retry: Option[Int] = None, // Retry (Bind)
                               expire: Option[Int] = None, // Expire (Bind)
                               negative_cache_ttl: Option[Int] = None, // Negative Cache TTL (Bind)
                               response: Option[ZoneGenerationResponse] = None,
                               id: String = UUID.randomUUID().toString
                     )

object GenerateZone {
  def apply(zoneGenerationInput: ZoneGenerationInput): GenerateZone = {
    import zoneGenerationInput._

    GenerateZone(
      groupId,
      provider,
      zoneName,
      status,
      serverId,
      kind,
      masters,
      nameservers,
      description,
      visibility,
      accountId,
      projectId,
      ns_ipaddress,
      admin_email,
      ttl,
      refresh,
      retry,
      expire,
      negative_cache_ttl,
      response
    )
  }

  def apply(updateGenerateZoneInput: UpdateGenerateZoneInput, currentGenerateZone: GenerateZone): GenerateZone = {
    import updateGenerateZoneInput._

    currentGenerateZone.copy(
      groupId,
      provider,
      zoneName ,
      status,
      serverId,
      kind,
      masters,
      nameservers,
      description,
      visibility,
      accountId,
      projectId,
      ns_ipaddress,
      admin_email,
      ttl,
      refresh,
      retry,
      expire,
      negative_cache_ttl,
      response
    )
  }
}

case class PowerDNSResponse(
                             account: String,
                             api_rectify: Boolean,
                             catalog: String,
                             dnssec: Boolean,
                             edited_serial: Long,
                             id: String,
                             kind: String,
                             last_check: Int,
                             master_tsig_key_ids: List[String],
                             masters: List[String],
                             name: String,
                             notified_serial: Int,
                             nsec3narrow: Boolean,
                             nsec3param: String,
                             rrsets: List[RRSet],
                             serial: Long,
                             slave_tsig_key_ids: List[String],
                             soa_edit: String,
                             soa_edit_api: String,
                             url: String
                           )

case class RRSet(
                  comments: List[String],
                  name: String,
                  records: List[Record],
                  ttl: Int,
                  `type`: String
                )

case class Record(
    content: String,
    disabled: Boolean
)

final case class ConnectZoneInput(
    name: String,
    email: String,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None,
    shared: Boolean = false,
    acl: ZoneACL = ZoneACL(),
    adminGroupId: String,
    backendId: Option[String] = None,
    recurrenceSchedule: Option[String] = None,
    scheduleRequestor: Option[String] = None
)

final case class UpdateZoneInput(
    id: String,
    name: String,
    email: String,
    connection: Option[ZoneConnection] = None,
    transferConnection: Option[ZoneConnection] = None,
    shared: Boolean = false,
    acl: ZoneACL = ZoneACL(),
    adminGroupId: String,
    recurrenceSchedule: Option[String] = None,
    scheduleRequestor: Option[String] = None,
    backendId: Option[String] = None
)

final case class UpdateGenerateZoneInput(
                                  groupId: String,
                                  provider: String, // "powerdns", "cloudflare", "google", "bind"
                                  zoneName: String,
                                  status: String,
                                  serverId: Option[String] = None, // The ID of the sever (PowerDNS)
                                  kind: Option[String] = None, // Zone type (PowerDNS/Cloudflare/Bind)
                                  masters: Option[List[String]] = None, // Master servers (for slave zones, PowerDNS)
                                  nameservers: Option[List[String]] = None, // NS records (PowerDNS)
                                  description: Option[String] = None, // description (Google)
                                  visibility: Option[String] = None, // Public or Private (Google)
                                  accountId: Option[String] = None, // Account ID (Cloudflare)
                                  projectId: Option[String] = None, // GCP Project ID (Google)
                                  ns_ipaddress: Option[List[String]] = None, // NS IpAddress (Bind)
                                  admin_email: Option[String] = None, // NS IpAddress (Bind)
                                  ttl: Option[Int] = None, // TTL (Bind)
                                  refresh: Option[Int] = None, // Refresh (Bind)
                                  retry: Option[Int] = None, // Retry (Bind)
                                  expire: Option[Int] = None, // Expire (Bind)
                                  negative_cache_ttl: Option[Int] = None, // Negative Cache TTL (Bind)
                                  response: Option[ZoneGenerationResponse] = None,
                                  id: String = UUID.randomUUID().toString,

                                )

case class ZoneGenerationResponse(
                                   provider: String,
                                   responseCode: Int,
                                   status: String,
                                   message: String
                                 )

case class ZoneGenerationInput(
    groupId: String,
    provider: String, // "powerdns", "cloudflare", "google", "bind"
    zoneName: String,
    status: String,
    serverId: Option[String] = None, // The ID of the sever (PowerDNS)
    kind: Option[String] = None, // Zone type (PowerDNS/Cloudflare/Bind)
    masters: Option[List[String]] = None, // Master servers (for slave zones, PowerDNS)
    nameservers: Option[List[String]] = None, // NS records (PowerDNS)
    description: Option[String] = None, // description (Google)
    visibility: Option[String] = None, // Public or Private (Google)
    accountId: Option[String] = None, // Account ID (Cloudflare)
    projectId: Option[String] = None, // GCP Project ID (Google)
    ns_ipaddress: Option[List[String]] = None, // NS IpAddress (Bind)
    admin_email: Option[String] = None, // NS IpAddress (Bind)
    ttl: Option[Int] = None, // TTL (Bind)
    refresh: Option[Int] = None, // Refresh (Bind)
    retry: Option[Int] = None, // Retry (Bind)
    expire: Option[Int] = None, // Expire (Bind)
    negative_cache_ttl: Option[Int] = None, // Negative Cache TTL (Bind)
    response: Option[ZoneGenerationResponse] = None,
    id: String = UUID.randomUUID().toString
                              ) {
  override def toString: String = {
    val sb = new StringBuilder
    sb.append("ZoneGenerationInput: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("groupId=\"").append(groupId).append("\"; ")
    sb.append("provider=\"").append(provider).append("\"; ")
    sb.append("zoneName=\"").append(zoneName).append("\"; ")
    sb.append("status=\"").append(zoneName).append("\"; ")
    sb.append("serverId=\"").append(serverId.toString).append("\"; ")
    sb.append("kind=\"").append(kind.toString).append("\"; ")
    sb.append("masters=\"").append(masters.toString).append("\"; ")
    sb.append("nameservers=\"").append(nameservers.toString).append("\"; ")
    sb.append("description=\"").append(description.toString).append("\"; ")
    sb.append("visibility=\"").append(visibility.toString).append("\"; ")
    sb.append("accountId=\"").append(accountId.toString).append("\"; ")
    sb.append("projectId=\"").append(projectId.toString).append("\"; ")
    sb.append("ns_ipaddress=\"").append(ns_ipaddress.toString).append("\"; ")
    sb.append("ttl=\"").append(ttl).append("\"; ")
    sb.append("refresh=\"").append(refresh).append("\"; ")
    sb.append("retry=\"").append(retry).append("\"; ")
    sb.append("expire=\"").append(expire).append("\"; ")
    sb.append("negative_cache_ttl=\"").append(negative_cache_ttl).append("\"; ")
    sb.append("]")
    sb.toString
  }
}

final case class ZoneACL(rules: Set[ACLRule] = Set.empty) {

  def addRule(newRule: ACLRule): ZoneACL = copy(rules = rules + newRule)

  def deleteRule(rule: ACLRule): ZoneACL = copy(rules = rules - rule)
}

sealed abstract class Algorithm(val name: String) {
  override def toString: String = name
}
object Algorithm {
  case object HMAC_MD5 extends Algorithm("HMAC-MD5")
  case object HMAC_SHA1 extends Algorithm("HMAC-SHA1")
  case object HMAC_SHA224 extends Algorithm("HMAC-SHA224")
  case object HMAC_SHA256 extends Algorithm("HMAC-SHA256")
  case object HMAC_SHA384 extends Algorithm("HMAC-SHA384")
  case object HMAC_SHA512 extends Algorithm("HMAC-SHA512")

  val Values = List(HMAC_MD5, HMAC_SHA1, HMAC_SHA224, HMAC_SHA256, HMAC_SHA384, HMAC_SHA512)
  val Map = Values.map(v => v.name -> v).toMap

  def fromString(name: String): Either[String, Algorithm] =
    Map
      .get(name)
      .toRight[String](s"Unsupported algorithm $name, must be one of ${Values.mkString(",")}")

  implicit val algorithmReader: ConfigReader[Algorithm] =
    ConfigReader.fromCursor[Algorithm](cur =>
      cur.asString.flatMap(alg =>
        Algorithm.fromString(alg).fold(
          errMsg => cur.failed(CannotConvert(alg, "Algorithm", errMsg)),
          algObj => Right(algObj)
        )
      )
    )
}

case class ZoneConnection(
    name: String,
    keyName: String,
    key: Encrypted,
    primaryServer: String,
    algorithm: Algorithm = Algorithm.HMAC_MD5
) {

  def encrypted(crypto: CryptoAlgebra): ZoneConnection =
    copy(key = Encryption.apply(crypto, key.value))

  def decrypted(crypto: CryptoAlgebra): ZoneConnection =
    copy(key = Encrypted(Encryption.decrypt(crypto, key)))

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("ZoneConnection: [")
    sb.append("name=\"").append(name).append("\"; ")
    sb.append("keyName=\"").append(keyName).append("\"; ")
    sb.append("primaryServer=\"").append(primaryServer).append("\"; ")
    sb.append("]")
    sb.toString
  }
}

final case class LegacyDnsBackend(
    id: String,
    zoneConnection: ZoneConnection,
    transferConnection: ZoneConnection
) {

  def encrypted(crypto: CryptoAlgebra): LegacyDnsBackend = copy(
    zoneConnection = zoneConnection.encrypted(crypto),
    transferConnection = transferConnection.encrypted(crypto)
  )
}

final case class DnsProviderApiConnection(
                                           bindCreateZoneApi: String,
                                           powerDnsCreateZoneApi: String,
                                           powerDnsApiKey: String,
                                           bindApiKey: String
                                         )


final case class ConfiguredDnsConnections(
    defaultZoneConnection: ZoneConnection,
    defaultTransferConnection: ZoneConnection,
    dnsBackends: List[LegacyDnsBackend],
    dnsProviderApiConnection : DnsProviderApiConnection
)
object ConfiguredDnsConnections {
  def load(config: Config, cryptoConfig: Config): IO[ConfiguredDnsConnections] =
    CryptoAlgebra.load(cryptoConfig).map { crypto =>
      val defaultZoneConnection = {
        val connectionConfig = config.getConfig("vinyldns.defaultZoneConnection")
        val name = connectionConfig.getString("name")
        val keyName = connectionConfig.getString("keyName")
        val key = connectionConfig.getString("key")
        val primaryServer = connectionConfig.getString("primaryServer")
        val algorithm =
          if (connectionConfig.hasPath("algorithm"))
            Algorithm.Map.getOrElse(connectionConfig.getString("algorithm"), Algorithm.HMAC_MD5)
          else Algorithm.HMAC_MD5
        ZoneConnection(name, keyName, Encrypted(key), primaryServer, algorithm).encrypted(crypto)
      }

      val defaultTransferConnection = {
        val connectionConfig = config.getConfig("vinyldns.defaultTransferConnection")
        val name = connectionConfig.getString("name")
        val keyName = connectionConfig.getString("keyName")
        val key = connectionConfig.getString("key")
        val primaryServer = connectionConfig.getString("primaryServer")
        val algorithm =
          if (connectionConfig.hasPath("algorithm"))
            Algorithm.Map.getOrElse(connectionConfig.getString("algorithm"), Algorithm.HMAC_MD5)
          else Algorithm.HMAC_MD5
        ZoneConnection(name, keyName, Encrypted(key), primaryServer, algorithm).encrypted(crypto)
      }

      val dnsBackends = {
        if (config.hasPath("vinyldns.backends")) {
          config
            .getConfigList("vinyldns.backends")
            .asScala
            .map {
              ConfigSource.fromConfig(_).loadOrThrow[LegacyDnsBackend]
            }
            .toList
            .map(_.encrypted(crypto))
        } else List.empty
      }

      val dnsProviderCreateZoneApiConfig = {
        if (config.hasPath("vinyldns.backend.backend-providers")) {
          val createZoneUri= config
            .getConfigList("vinyldns.backend.backend-providers")
            .asScala
            .find(_.hasPath("settings.dns-provider-api.create-zone-uri"))
            .map(_.getConfig("settings.dns-provider-api.create-zone-uri"))
            .getOrElse(ConfigFactory.empty())
          val apiKey= config
            .getConfigList("vinyldns.backend.backend-providers")
            .asScala
            .find(_.hasPath("settings.dns-provider-api.api-key"))
            .map(_.getConfig("settings.dns-provider-api.api-key"))
            .getOrElse(ConfigFactory.empty())
          val bindCreateZoneApi = createZoneUri.getString("bind")
          val powerdnsCreateZoneApi = createZoneUri.getString("powerdns")
          val powerdnsApiKey = apiKey.getString("powerdns")
          val bindApiKey = apiKey.getString("bind")

          DnsProviderApiConnection(bindCreateZoneApi, powerdnsCreateZoneApi, powerdnsApiKey, bindApiKey)

        } else DnsProviderApiConnection("", "", "", "")
      }

      ConfiguredDnsConnections(defaultZoneConnection, defaultTransferConnection, dnsBackends, dnsProviderCreateZoneApiConfig)
    }
}
