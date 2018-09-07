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

import java.util

import controllers.LdapAuthenticator.LdapByDomainAuthenticator
import javax.naming.Context
import javax.naming.directory._

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

case class UserDetails(
    nameInNamespace: String,
    username: String,
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String])

object UserDetails {
  private def getValue(attributes: Attributes, attributeName: String): Option[String] =
    Option(attributes.get(attributeName)).map(_.get.toString)

  def apply(rawResult: SearchResult): UserDetails = {
    val attributes = rawResult.getAttributes
    val nameInNamespace = rawResult.getNameInNamespace
    val username = attributes.get("sAMAccountName").get.toString
    val email = getValue(attributes, "mail")
    val firstName = getValue(attributes, "givenName")
    val lastName = getValue(attributes, "sn")

    new UserDetails(nameInNamespace, username, email, firstName, lastName)
  }
}

case class ServiceAccount(domain: String, name: String, password: String)

// $COVERAGE-OFF$
object LdapAuthenticator {
  type ContextCreator = (String, String) => Try[DirContext]

  /**
    * contains the functionality to build an ldap  directory context; essentially isolates ldap connection stuff from
    * unit testable code
    */
  val createContext: Settings => ContextCreator = (settings: Settings) =>
    (username: String, password: String) => {
      val env: Map[String, String] = Map(
        Context.INITIAL_CONTEXT_FACTORY -> settings.ldapCtxFactory,
        Context.SECURITY_AUTHENTICATION -> settings.ldapSecurityAuthentication,
        Context.SECURITY_PRINCIPAL -> username,
        Context.SECURITY_CREDENTIALS -> password,
        Context.PROVIDER_URL -> settings.ldapProviderUrl.toString
      )

      val searchScope = 2 //recursive
      val searchControls = new SearchControls()
      searchControls.setSearchScope(searchScope)

      val envHashtable = new util.Hashtable[String, String](env.asJava)

      Try(new InitialDirContext(envHashtable))
  }
  // $COVERAGE-ON$

  private[controllers] object LdapByDomainAuthenticator {
    def apply(settings: Settings): LdapByDomainAuthenticator =
      new LdapByDomainAuthenticator(settings, createContext(settings))

    def apply(): LdapByDomainAuthenticator = LdapByDomainAuthenticator(Settings)
  }

  /**
    * Although this extra abstraction seems silly at first, it basically helps us remove one extra branch in our unit
    * tests, making everything far more testable
    *
    * @param createContext function that creates the ldap connection / [DirContext]
    */
  private[controllers] class LdapByDomainAuthenticator(
      settings: Settings,
      createContext: ContextCreator) {

    private val SEARCH_BASE = settings.ldapSearchBase
      .map(searchDomain ⇒ searchDomain.organization → searchDomain.domainName)
      .toMap

    private[controllers] def authenticate(
        searchDomain: LdapSearchDomain,
        username: String,
        password: String): Try[UserDetails] =
      createContext(s"${searchDomain.organization}\\$username", password).map { context =>
        try {
          val searchControls = new SearchControls()
          searchControls.setSearchScope(2)
          val result = context.search(
            SEARCH_BASE(searchDomain.organization),
            s"(sAMAccountName=$username)",
            searchControls)
          if (!result.hasMore)
            throw new UserDoesNotExistException(
              s"[$username] can authenticate but LDAP entity " +
                s"does not exist")

          UserDetails(result.next())

        } finally {
          // try to close but don't care if anything happens
          Try(context.close())
        }
      }

    private[controllers] def lookup(
        searchDomain: LdapSearchDomain,
        user: String,
        serviceAccount: ServiceAccount): Try[UserDetails] =
      createContext(s"${serviceAccount.domain}\\${serviceAccount.name}", serviceAccount.password)
        .map { context =>
          try {
            val searchControls = new SearchControls()
            searchControls.setSearchScope(2)
            val result = context.search(
              SEARCH_BASE(searchDomain.organization),
              s"(sAMAccountName=$user)",
              searchControls)
            if (!result.hasMore)
              throw new UserDoesNotExistException(s"[$user] LDAP entity does not exist")

            UserDetails(result.next())

          } finally {
            // try to close but don't care if anything happens
            Try(context.close())
          }
        }
  }

  def apply(settings: Settings): Authenticator = {
    val testLogin = settings.portalTestLogin
    val serviceUser = settings.ldapUser
    val servicePass = settings.ldapPwd
    val serviceDomain = settings.ldapDomain
    val serviceAccount = ServiceAccount(serviceDomain, serviceUser, servicePass)

    if (testLogin)
      new TestAuthenticator(
        new LdapAuthenticator(
          settings.ldapSearchBase,
          LdapByDomainAuthenticator(settings),
          serviceAccount))
    else
      new LdapAuthenticator(
        settings.ldapSearchBase,
        LdapByDomainAuthenticator(settings),
        serviceAccount)
  }
}

class UserDoesNotExistException(message: String) extends Exception(message: String)
class UserIsNotAnAdminException(message: String) extends Exception(message: String)
class UserAccountIsLockedException(message: String) extends Exception(message: String)

/**
  * Top level ldap authenticator that tries authenticating on both the cable and corphq domains. Authentication is
  * delegated to [LdapByDomainAuthenticator]
  *
  * @param authenticator does authentication by domain (ie cable vs corphq)
  */
class LdapAuthenticator(
    searchBase: List[LdapSearchDomain],
    authenticator: LdapByDomainAuthenticator,
    serviceAccount: ServiceAccount)
    extends Authenticator {

  private def findUserDetails(
      domains: List[LdapSearchDomain],
      userName: String,
      f: LdapSearchDomain => Try[UserDetails]): Try[UserDetails] = domains match {
    case Nil => Failure(new UserDoesNotExistException(s"[$userName] LDAP entity does not exist"))
    case h :: t => f(h).recoverWith { case _ => findUserDetails(t, userName, f) }
  }

  def authenticate(username: String, password: String): Try[UserDetails] =
    findUserDetails(searchBase, username, authenticator.authenticate(_, username, password))

  def lookup(username: String): Try[UserDetails] =
    findUserDetails(searchBase, username, authenticator.lookup(_, username, serviceAccount))

}

trait Authenticator {
  def authenticate(username: String, password: String): Try[UserDetails]
  def lookup(username: String): Try[UserDetails]
}

/**
  * Top level authenticator that has a bypass user for testing
  *
  * @param authenticator the real authenticator for when the user is not the test user
  */
class TestAuthenticator(authenticator: Authenticator) extends Authenticator {
  private val testUserDetails = UserDetails(
    "O=test,OU=testdata,CN=testuser",
    "testuser",
    Some("test@test.test"),
    Some("Test"),
    Some("User"))
  private val recordPagingTestUserDetails = UserDetails(
    "O=test,OU=testdata,CN=recordPagingTestUser",
    "recordPagingTestUser",
    Some("test@test.test"),
    Some("Test"),
    Some("User"))

  def authenticate(username: String, password: String): Try[UserDetails] =
    (username, password) match {
      case ("recordPagingTestUser", "testpassword") => Try(recordPagingTestUserDetails)
      case ("testuser", "testpassword") => Try(testUserDetails)
      case _ => authenticator.authenticate(username, password)
    }

  def lookup(username: String): Try[UserDetails] =
    username match {
      case "recordPagingTestUser" => Try(recordPagingTestUserDetails)
      case "testuser" => Try(testUserDetails)
      case _ => authenticator.lookup(username)
    }
}

case class LdapSearchDomain(organization: String, domainName: String)
