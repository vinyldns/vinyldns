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

import cats.effect.{ContextShift, IO}
import cats.implicits._
import controllers.LdapAuthenticator.LdapByDomainAuthenticator
import controllers.VinylDNS.UserDetails
import javax.naming.Context
import javax.naming.directory._
import org.slf4j.LoggerFactory
import vinyldns.core.domain.membership.User
import vinyldns.core.health.HealthCheck._
import java.io.{PrintWriter, StringWriter}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class LdapUserDetails(
    nameInNamespace: String,
    username: String,
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String]
) extends UserDetails

object LdapUserDetails {
  private def getValue(attributes: Attributes, attributeName: String): Option[String] =
    Option(attributes.get(attributeName)).map(_.get.toString)

  def apply(rawResult: SearchResult, userNameField: String): LdapUserDetails = {
    val attributes = rawResult.getAttributes
    val nameInNamespace = rawResult.getNameInNamespace
    val username = attributes.get(userNameField).get.toString
    val email = getValue(attributes, "mail")
    val firstName = getValue(attributes, "givenName")
    val lastName = getValue(attributes, "sn")

    new LdapUserDetails(nameInNamespace, username, email, firstName, lastName)
  }
}

case class ServiceAccount(domain: String, name: String, password: String)

// $COVERAGE-OFF$
object LdapAuthenticator {
  type ContextCreator = (String, String) => Either[LdapException, DirContext]
  private val logger = LoggerFactory.getLogger("LdapAuthenticator")

  /**
    * contains the functionality to build an LDAP directory context; essentially isolates LDAP connection stuff from
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

      val envHashtable = new util.Hashtable[String, String](env.asJava)

      logger.info(s"LDAP Creating Context user='$username'")

      Try(new InitialDirContext(envHashtable)) match {
        case Success(dirContext) => Right(dirContext)
        case Failure(authEx: javax.naming.AuthenticationException) =>
          logger.error("LDAP Authentication Exception", authEx)
          Left(InvalidCredentials(username))
        case Failure(e) =>
          Left(LdapServiceException(e.toString))
      }
    }
  // $COVERAGE-ON$

  private[controllers] object LdapByDomainAuthenticator {
    def apply(settings: Settings): LdapByDomainAuthenticator =
      new LdapByDomainAuthenticator(settings, createContext(settings))
  }

  /**
    * Although this extra abstraction seems silly at first, it basically helps us remove one extra branch in our unit
    * tests, making everything far more testable
    *
    * @param createContext function that creates the ldap connection / [DirContext]
    */
  private[controllers] class LdapByDomainAuthenticator(
      settings: Settings,
      createContext: ContextCreator
  ) {

    private val SEARCH_BASE = settings.ldapSearchBase
      .map(searchDomain ⇒ searchDomain.organization → searchDomain.domainName)
      .toMap

    def searchContext(
        dirContext: DirContext,
        organization: String,
        lookupUserName: String
    ): Either[LdapException, LdapUserDetails] =
      try {
        val searchControls = new SearchControls()
        searchControls.setSearchScope(2)

        logger.info(
          s"LDAP Search: org='${SEARCH_BASE(organization)}'; userName='$lookupUserName'; field='${settings.ldapUserNameAttribute}'"
        )

        val result = dirContext.search(
          SEARCH_BASE(organization),
          s"(${settings.ldapUserNameAttribute}=$lookupUserName)",
          searchControls
        )

        if (result.hasMore) Right(LdapUserDetails(result.next(), settings.ldapUserNameAttribute))
        else Left(UserDoesNotExistException(s"[$lookupUserName] LDAP entity does not exist"))
      } catch {
        case unexpectedError: Throwable =>
          val errorMessage = new StringWriter
          unexpectedError.printStackTrace(new PrintWriter(errorMessage))
          logger.error(
            s"LDAP Unexpected Error searching for user; userName='$lookupUserName'. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}"
          )
          Left(LdapServiceException(unexpectedError.getMessage))
      } finally {
        Try(dirContext.close())
      }

    private[controllers] def authenticate(
        searchDomain: LdapSearchDomain,
        username: String,
        password: String
    ): Either[LdapException, LdapUserDetails] = {

      // Login as the service account
      val qualifiedName =
        if (settings.ldapDomain.isEmpty) settings.ldapUser
        else s"${settings.ldapDomain}\\${settings.ldapUser}"

      logger.info(s"LDAP authenticate attempt for user $qualifiedName")

      // 1. Login as the service account
      // 2. Find the user information (if it is in this search domain)
      // 3. Login as the user that was found (if the user was found) to authenticate
      for {
        ctx <- createContext(qualifiedName, settings.ldapPwd)
        user <- searchContext(ctx, searchDomain.organization, username)
        _ <- createContext(user.nameInNamespace, password)
      } yield user
    }

    private[controllers] def lookup(
        searchDomain: LdapSearchDomain,
        user: String,
        serviceAccount: ServiceAccount
    ): Either[LdapException, LdapUserDetails] = {

      // User lookup is done using the service account
      val qualifiedName =
        if (serviceAccount.domain.isEmpty) serviceAccount.name
        else s"${serviceAccount.domain}\\${serviceAccount.name}"
      createContext(qualifiedName, serviceAccount.password)
        .flatMap { context =>
          searchContext(context, searchDomain.organization, user)
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
          serviceAccount
        )
      )
    else
      new LdapAuthenticator(
        settings.ldapSearchBase,
        LdapByDomainAuthenticator(settings),
        serviceAccount
      )
  }
}

sealed abstract class LdapException(message: String) extends Exception(message)

final case class UserDoesNotExistException(message: String) extends LdapException(message)
final case class LdapServiceException(errorMessage: String)
    extends LdapException(s"Encountered error communicating with LDAP service: $errorMessage")
final case class InvalidCredentials(username: String)
    extends LdapException(s"Provided credentials were invalid for user [$username].")
final case class NoLdapSearchDomainsConfigured()
    extends LdapException("No LDAP search domains were configured so user lookup is impossible.")

/**
  * Top level ldap authenticator that tries authenticating on multiple domains. Authentication is
  * delegated to [LdapByDomainAuthenticator]
  *
  * @param authenticator does authentication by domain
  */
class LdapAuthenticator(
    searchBase: List[LdapSearchDomain],
    authenticator: LdapByDomainAuthenticator,
    serviceAccount: ServiceAccount
) extends Authenticator {

  private val logger = LoggerFactory.getLogger(classOf[LdapAuthenticator])
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /**
    * Attempts to search for user in specified LDAP domains. Attempts all LDAP domains that are specified in order; in
    * the event that user details are not found in any of the domains, returns an error based on whether all
    * LDAP search domains had successful connections (to distinguish between user not existing vs LDAP service being
    * unreachable)
    *
    * @param domains List of domains in LDAP to lookup user
    * @param userName Username to lookup
    * @param f Function from (username, password) => DirContext
    * @param allDomainConnectionsUp Connectivity to all LDAP domains via the specified provider are up as expected
    * @return User details or exception encountered (eg. UserDoesNotExistException or LdapServiceException) depending
    *         on cause
    */
  private def findUserDetails(
      domains: List[LdapSearchDomain],
      userName: String,
      f: LdapSearchDomain => Either[LdapException, LdapUserDetails],
      allDomainConnectionsUp: Boolean
  ): Either[LdapException, LdapUserDetails] =
    domains match {
      case Nil =>
        if (allDomainConnectionsUp)
          Left(UserDoesNotExistException(s"[$userName] LDAP entity does not exist"))
        else
          Left(
            LdapServiceException(
              "Unable to successfully perform search in at least one LDAP domain"
            )
          )
      case h :: t =>
        f(h).recoverWith {
          case _: UserDoesNotExistException =>
            logger.info(s"user='$userName' not found in search context $h")
            findUserDetails(t, userName, f, allDomainConnectionsUp)
          case other =>
            logger.error(s"Unexpected error finding user details; user='$userName'", other)
            findUserDetails(t, userName, f, false)
        }
    }

  def authenticate(username: String, password: String): Either[LdapException, LdapUserDetails] =
    // Need to check domains here due to recursive nature of findUserDetails
    if (searchBase.isEmpty) Left(NoLdapSearchDomainsConfigured())
    else
      findUserDetails(searchBase, username, authenticator.authenticate(_, username, password), true)

  def lookup(username: String): Either[LdapException, LdapUserDetails] =
    // Need to check domains here due to recursive nature of findUserDetails
    if (searchBase.isEmpty) Left(NoLdapSearchDomainsConfigured())
    else
      findUserDetails(searchBase, username, authenticator.lookup(_, username, serviceAccount), true)

  def healthCheck(): HealthCheck =
    IO {
      searchBase.headOption
        .map(authenticator.lookup(_, "healthlookup", serviceAccount)) match {
        case Some(Left(_: UserDoesNotExistException)) => ().asRight
        case Some(Left(e)) => e.asLeft
        case _ => ().asRight
      }
    }.asHealthCheck(classOf[LdapAuthenticator])

  // List[User] => List[Either[LdapException, LdapUserDetails]] => List[User]
  def getUsersNotInLdap(users: List[User]): IO[List[User]] =
    users
      .map { u =>
        IO(lookup(u.userName)).map {
          case Left(_: UserDoesNotExistException) => Some(u) // Only grab users that do not exist
          case _ => None
        }
      }
      .parSequence
      .map(_.flatten)
}

trait Authenticator {
  def authenticate(username: String, password: String): Either[LdapException, LdapUserDetails]
  def lookup(username: String): Either[LdapException, LdapUserDetails]
  def getUsersNotInLdap(usernames: List[User]): IO[List[User]]
  def healthCheck(): HealthCheck
}

/**
  * Top level authenticator that has a bypass user for testing
  *
  * @param authenticator the real authenticator for when the user is not the test user
  */
class TestAuthenticator(authenticator: Authenticator) extends Authenticator {
  private val testUserDetails = LdapUserDetails(
    "O=test,OU=testdata,CN=testuser",
    "testuser",
    Some("test@test.test"),
    Some("Test"),
    Some("User")
  )
  private val recordPagingTestUserDetails = LdapUserDetails(
    "O=test,OU=testdata,CN=recordPagingTestUser",
    "recordPagingTestUser",
    Some("test@test.test"),
    Some("Test"),
    Some("User")
  )
  private val supportTestUserDetails = LdapUserDetails(
    "O=test,OU=testdata,CN=supportTestUser",
    "support-user",
    Some("test@test.test"),
    Some("Support"),
    Some("User")
  )

  def authenticate(username: String, password: String): Either[LdapException, LdapUserDetails] =
    (username, password) match {
      case ("recordPagingTestUser", "testpassword") => Right(recordPagingTestUserDetails)
      case ("testuser", "testpassword") => Right(testUserDetails)
      case ("supportUser", "testpassword") => Right(supportTestUserDetails)
      case _ => authenticator.authenticate(username, password)
    }

  def lookup(username: String): Either[LdapException, LdapUserDetails] =
    username match {
      case "recordPagingTestUser" => Right(recordPagingTestUserDetails)
      case "testuser" => Right(testUserDetails)
      case "supportUser" => Right(supportTestUserDetails)
      case _ => authenticator.lookup(username)
    }

  def healthCheck(): HealthCheck = authenticator.healthCheck()

  def getUsersNotInLdap(users: List[User]): IO[List[User]] =
    authenticator.getUsersNotInLdap(users)
}

case class LdapSearchDomain(organization: String, domainName: String)
