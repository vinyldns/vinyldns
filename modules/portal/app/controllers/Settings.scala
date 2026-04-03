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

package controllers

import java.net.URI

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import play.api.{ConfigLoader, Configuration}
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import vinyldns.core.repository.DataStoreConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._

// $COVERAGE-OFF$
class Settings(private val config: Configuration) {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  lazy val ldapUser: String = config.get[String]("LDAP.user")
  lazy val ldapPwd: String = config.get[String]("LDAP.password")
  lazy val ldapDomain: String = config.get[String]("LDAP.domain")

  lazy val ldapSearchBase: List[LdapSearchDomain] = config.get[List[LdapSearchDomain]]("LDAP.searchBase")
  lazy val ldapCtxFactory: String = config.get[String]("LDAP.context.initialContextFactory")
  lazy val ldapSecurityAuthentication: String = config.get[String]("LDAP.context.securityAuthentication")
  lazy val ldapProviderUrl: URI = new URI(config.get[String]("LDAP.context.providerUrl"))
  lazy val ldapUserNameAttribute: String =
    config.getOptional[String]("LDAP.userNameAttribute").getOrElse("sAMAccountName")

  val ldapSyncEnabled: Boolean =
    config.getOptional[Boolean]("LDAP.user-sync.enabled").getOrElse(false)
  val ldapSyncPollingInterval: FiniteDuration = config
    .getOptional[Int]("LDAP.user-sync.hours-polling-interval")
    .getOrElse(24)
    .hours

  val userSyncProvider: String =
    config.getOptional[String]("user-sync.provider").getOrElse("none")

  val userSyncPollingInterval: FiniteDuration = config
    .getOptional[Int]("user-sync.polling-interval-hours")
    .getOrElse(24)
    .hours

  val graphApiUsernameAttribute: String =
    config.getOptional[String]("user-sync.graph-api.username-attribute")
      .getOrElse("onPremisesSamAccountName")

  val oidcEnabled: Boolean =
    config.getOptional[Boolean]("oidc.enabled").getOrElse(false)

  lazy val oidcTenantId: String =
    config.get[String]("oidc.tenant-id")

  lazy val oidcClientId: String =
    config.get[String]("oidc.client-id")

  lazy val oidcSecret: String =
    config.get[String]("oidc.secret")

  val portalTestLogin: Boolean = config.getOptional[Boolean]("portal.test_login").getOrElse(false)

  val dataStoreConfigs: IO[List[DataStoreConfig]] =
    Blocker[IO].use { blocker =>
      ConfigSource
        .fromConfig(config.underlying)
        .at("data-stores")
        .loadF[IO, List[String]](blocker)
        .flatMap { lst =>
          lst
            .map(
              ConfigSource.fromConfig(config.underlying).at(_).loadF[IO, DataStoreConfig](blocker)
            )
            .parSequence
        }
    }

  val cryptoConfig = IO(config.get[Config]("crypto"))

  def validateLdapConfig(): Unit = {
    ldapUser; ldapPwd; ldapDomain; ldapSearchBase
    ldapCtxFactory; ldapSecurityAuthentication; ldapProviderUrl
    ldapUserNameAttribute
  }

  def validateOidcConfig(): Unit =
    try {
      oidcTenantId; oidcClientId; oidcSecret
    } catch {
      case e: com.typesafe.config.ConfigException.Missing =>
        throw new IllegalArgumentException(
          "OIDC configuration incomplete for graph-api user sync. " +
            "The oidc.tenant-id, oidc.client-id, and oidc.secret settings are all required " +
            s"when user-sync.provider is set to 'graph-api': ${e.getMessage}"
        )
    }

  implicit def ldapSearchDomainLoader: ConfigLoader[List[LdapSearchDomain]] =
    new ConfigLoader[List[LdapSearchDomain]] {
      def load(config: Config, path: String): List[LdapSearchDomain] = {
        val domains = config.getConfigList(path).asScala.map { domainConfig ⇒
          val org = domainConfig.getString("organization")
          val domain = domainConfig.getString("domainName")
          LdapSearchDomain(org, domain)
        }
        domains.toList
      }
    }
}
// $COVERAGE-ON$
object Settings extends Settings(Configuration(ConfigFactory.load()))
