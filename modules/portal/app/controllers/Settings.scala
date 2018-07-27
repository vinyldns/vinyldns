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

import com.typesafe.config.{Config, ConfigFactory}
import play.api.{ConfigLoader, Configuration}

import scala.collection.JavaConverters._

class Settings(private val config: Configuration) {

  val ldapUser: String = config.get[String]("LDAP.user")
  val ldapPwd: String = config.get[String]("LDAP.password")
  val ldapDomain: String = config.get[String]("LDAP.domain")

  val ldapSearchBase: List[LdapSearchDomain] = config.get[List[LdapSearchDomain]]("LDAP.searchBase")
  val ldapCtxFactory: String = config.get[String]("LDAP.context.initialContextFactory")
  val ldapSecurityAuthentication: String = config.get[String]("LDAP.context.securityAuthentication")
  val ldapProviderUrl: URI = new URI(config.get[String]("LDAP.context.providerUrl"))

  val portalTestLogin: Boolean = config.getOptional[Boolean]("portal.test_login").getOrElse(false)

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

object Settings extends Settings(Configuration(ConfigFactory.load()))
