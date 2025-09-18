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

import cats.effect.IO
import javax.naming.NamingEnumeration
import javax.naming.directory._
import controllers.LdapAuthenticator.{ContextCreator, LdapByDomainAuthenticator}
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.ArgumentCapture
import org.specs2.mutable.Specification
import play.api.{Configuration, Environment}
import vinyldns.core.domain.Encrypted
import vinyldns.core.health.HealthCheck._
import vinyldns.core.domain.membership.User
import vinyldns.core.health.HealthCheck.HealthCheckError

class LdapAuthenticatorSpec extends Specification with Mockito {

  case class Mocks(
      contextCreator: ContextCreator,
      context: DirContext,
      searchResults: NamingEnumeration[SearchResult],
      searchNext: SearchResult,
      byDomainAuthenticator: LdapByDomainAuthenticator,
      attributes: Attributes
  )

  /**
    * creates a container holding all mocks necessary to create
    * @return
    */
  def createMocks: Mocks = {
    val contextCreator: ContextCreator = mock[ContextCreator]
    val context = mock[DirContext]
    contextCreator.apply(anyString, anyString).returns(Right(context))
    val searchResults = mock[NamingEnumeration[SearchResult]]
    searchResults.hasMore.returns(true)
    val mockAttribute = mock[Attribute]
    mockAttribute.get().returns("")
    val mockAttributes = mock[Attributes]
    mockAttributes.get(anyString).returns(mockAttribute)
    val searchNext = mock[SearchResult]
    searchResults.next.returns(searchNext)
    searchNext.getNameInNamespace.returns("")
    searchNext.getAttributes.returns(mockAttributes)
    context.search(anyString, anyString, any[SearchControls]).returns(searchResults)
    val byDomainAuthenticator = new LdapByDomainAuthenticator(Settings, contextCreator)

    Mocks(contextCreator, context, searchResults, searchNext, byDomainAuthenticator, mockAttributes)
  }

  val testDomain1 = LdapSearchDomain("someDomain", "DC=test,DC=test,DC=com")
  val testDomain2 = LdapSearchDomain("anotherDomain", "DC=test,DC=com")
  val nonexistentUser = User("does-not-exist", "accessKey", Encrypted("secretKey"))

  "LdapAuthenticator" should {
    "apply method must create an LDAP Authenticator" in {
      val testConfig: Configuration =
        Configuration.load(Environment.simple()).withFallback(
          Configuration.from(Map("portal.test_login" -> false))
        )
      val underTest = LdapAuthenticator.apply(new Settings(testConfig))
      underTest must beAnInstanceOf[LdapAuthenticator]
    }
    "apply method must create a Test Authenticator if selected" in {
      val testConfig: Configuration =
        Configuration.load(Environment.simple()).withFallback(
          Configuration.from(Map("portal.test_login" -> true))
        )
      val underTest = LdapAuthenticator.apply(new Settings(testConfig))
      underTest must beAnInstanceOf[TestAuthenticator]
    }
    ".authenticate" should {
      "authenticate first with 1st domain" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        byDomainAuthenticator
          .authenticate(testDomain1, "foo", "bar")
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator =
          new LdapAuthenticator(List(testDomain1), byDomainAuthenticator, mock[ServiceAccount])
        val response = authenticator.authenticate("foo", "bar")

        there.was(one(byDomainAuthenticator).authenticate(testDomain1, "foo", "bar"))
        there.was(no(byDomainAuthenticator).authenticate(testDomain2, "foo", "bar"))

        "and return Right if authenticated" in {
          response must beRight
        }
      }

      "authenticate with 2nd domain if 1st fails with UserDoesNotExistException" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        byDomainAuthenticator
          .authenticate(testDomain1, "foo", "bar")
          .returns(Left(UserDoesNotExistException("first failed")))
        byDomainAuthenticator
          .authenticate(testDomain2, "foo", "bar")
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator = new LdapAuthenticator(
          List(testDomain1, testDomain2),
          byDomainAuthenticator,
          mock[ServiceAccount]
        )

        val response = authenticator.authenticate("foo", "bar")

        there.was(one(byDomainAuthenticator).authenticate(testDomain1, "foo", "bar"))
        there.was(one(byDomainAuthenticator).authenticate(testDomain2, "foo", "bar"))

        "and return a Success if authenticated" in {
          response must beRight
        }
      }

      "return an error if all domains lookups fail but services are up" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        byDomainAuthenticator
          .authenticate(testDomain1, "foo", "bar")
          .returns(Left(UserDoesNotExistException("first failed")))
        byDomainAuthenticator
          .authenticate(testDomain2, "foo", "bar")
          .returns(Left(UserDoesNotExistException("second failed")))
        val authenticator = new LdapAuthenticator(
          List(testDomain1, testDomain2),
          byDomainAuthenticator,
          mock[ServiceAccount]
        )

        val response = authenticator.authenticate("foo", "bar")

        there.was(one(byDomainAuthenticator).authenticate(testDomain1, "foo", "bar"))
        there.was(one(byDomainAuthenticator).authenticate(testDomain2, "foo", "bar"))

        "and return error message" in {
          response must beLeft
        }
      }

      "return an error if all domains fail with at least one LDAP connectivity issue" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        byDomainAuthenticator
          .authenticate(testDomain1, "foo", "bar")
          .returns(Left(UserDoesNotExistException("first failed")))
        byDomainAuthenticator
          .authenticate(testDomain2, "foo", "bar")
          .returns(Left(LdapServiceException("some bad exception")))
        val authenticator = new LdapAuthenticator(
          List(testDomain1, testDomain2),
          byDomainAuthenticator,
          mock[ServiceAccount]
        )

        val response = authenticator.authenticate("foo", "bar")

        there.was(one(byDomainAuthenticator).authenticate(testDomain1, "foo", "bar"))
        there.was(one(byDomainAuthenticator).authenticate(testDomain2, "foo", "bar"))

        "and return error message" in {
          response must beLeft
        }
      }

      "return an error if no LDAP search domains are provided" in {
        val noDomainsLdapAuthenticator =
          new LdapAuthenticator(List(), mock[LdapByDomainAuthenticator], mock[ServiceAccount])

        noDomainsLdapAuthenticator.authenticate("someUserName", "somePassword") must beLeft
      }
    }
    ".lookup" should {
      "lookup first with 1st domain" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = ServiceAccount("first", "foo", "bar")
        byDomainAuthenticator
          .lookup(testDomain1, "foo", serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        val response = authenticator.lookup("foo")

        there.was(one(byDomainAuthenticator).lookup(testDomain1, "foo", serviceAccount))
        there.was(no(byDomainAuthenticator).authenticate(testDomain2, "foo", "bar"))

        "and return details if authenticated" in {
          response must beRight
        }

        "return an error if no LDAP search domains are provided" in {
          val noDomainsLdapAuthenticator =
            new LdapAuthenticator(List(), mock[LdapByDomainAuthenticator], mock[ServiceAccount])

          noDomainsLdapAuthenticator.lookup("someUserName") must beLeft
        }
      }

      "lookup with 2nd domain if 1st fails with UserDoesNotExistException" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "foo", serviceAccount)
          .returns(Left(UserDoesNotExistException("first failed")))
        byDomainAuthenticator
          .lookup(testDomain2, "foo", serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        val response = authenticator.lookup("foo")

        there.was(one(byDomainAuthenticator).lookup(testDomain1, "foo", serviceAccount))
        there.was(one(byDomainAuthenticator).lookup(testDomain2, "foo", serviceAccount))

        "and return None if authenticated" in {
          response must beRight
        }
      }

      "return an error if all domains lookups fail but services are up" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "foo", serviceAccount)
          .returns(Left(UserDoesNotExistException("first failed")))
        byDomainAuthenticator
          .lookup(testDomain2, "foo", serviceAccount)
          .returns(Left(UserDoesNotExistException("second failed")))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        val response = authenticator.lookup("foo")

        there.was(one(byDomainAuthenticator).lookup(testDomain1, "foo", serviceAccount))
        there.was(one(byDomainAuthenticator).lookup(testDomain2, "foo", serviceAccount))

        "and return error message" in {
          response must beLeft
        }
      }

      "return error if all lookups fail with at least one LDAP connectivity issue" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "foo", serviceAccount)
          .returns(Left(LdapServiceException("some LDAP connectivity issue")))
        byDomainAuthenticator
          .lookup(testDomain2, "foo", serviceAccount)
          .returns(Left(UserDoesNotExistException("second failed")))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        val response = authenticator.lookup("foo")

        there.was(one(byDomainAuthenticator).lookup(testDomain1, "foo", serviceAccount))
        there.was(one(byDomainAuthenticator).lookup(testDomain2, "foo", serviceAccount))

        "and return error message" in {
          response must beLeft
        }
      }
    }
    ".healthCheck" should {
      "fail if there is some unexpected error" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "healthlookup", serviceAccount)
          .returns(Left(LdapServiceException("some failure")))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        authenticator.healthCheck()
        val response = authenticator.healthCheck().unsafeRunSync()

        response should beLeft[HealthCheckError]
      }
      "succeed if the dummy user cant be found" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "healthlookup", serviceAccount)
          .returns(Left(UserDoesNotExistException("does not exist")))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        authenticator.healthCheck()
        val response = authenticator.healthCheck().unsafeRunSync()

        response should beRight[Unit]
      }
      "succeed if the dummy user can be found" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "healthlookup", serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        authenticator.healthCheck()
        val response = authenticator.healthCheck().unsafeRunSync()

        response should beRight[Unit]
      }
    }
    ".getUsersNotInLdap" should {
      "return a list of users not found in LDAP" in {
        val byDomainAuthenticator = mock[LdapByDomainAuthenticator]
        val serviceAccount = mock[ServiceAccount]
        byDomainAuthenticator
          .lookup(testDomain1, "does-not-exist", serviceAccount)
          .returns(Left(UserDoesNotExistException("does not exist")))
        byDomainAuthenticator
          .lookup(testDomain2, "does-not-exist", serviceAccount)
          .returns(Left(UserDoesNotExistException("does not exist")))
        byDomainAuthenticator
          .lookup(testDomain1, "existing-user", serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        byDomainAuthenticator
          .lookup(testDomain1, "another-existing-user", serviceAccount)
          .returns(Left(UserDoesNotExistException("does not exist")))
        byDomainAuthenticator
          .lookup(testDomain2, "another-existing-user", serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        byDomainAuthenticator
          .lookup(testDomain1, serviceAccount.name, serviceAccount)
          .returns(Right(LdapUserDetails("", "", None, None, None)))
        val authenticator =
          new LdapAuthenticator(
            List(testDomain1, testDomain2),
            byDomainAuthenticator,
            serviceAccount
          )

        authenticator
          .getUsersNotInLdap(
            List(
              nonexistentUser,
              nonexistentUser.copy(userName = "existing-user"),
              nonexistentUser.copy(userName = "another-existing-user")
            )
          )
          .unsafeRunSync() must
          beEqualTo(List(nonexistentUser))
      }
    }
  }

  "LdapByDomainAuthenticator" should {
    "return an error message if authenticated but no LDAP record is found" in {
      val mocks = createMocks
      mocks.searchResults.hasMore.returns(false)
      val response = mocks.byDomainAuthenticator.authenticate(testDomain1, "foo", "bar")

      response must beLeft
    }

    "if creating context succeeds" in {
      "and result.hasMore is true" in {
        val mocks = createMocks
        val response = mocks.byDomainAuthenticator.authenticate(testDomain1, "foo", "bar")

        "return Success" in {
          response must beRight
        }

        "call contextCreator.apply" in {
          // We first authenticate to the service account, and then to the user
          there.was(
            one(mocks.contextCreator).apply("test\\test", "test"),
            one(mocks.contextCreator).apply("", "bar")
          )
        }

        "call the correct search on context" in {
          //because specs2 doesn't compare SearchControls objects properly, we have to use argument capture. And since
          //mockito only allows all raw variable comparisons or mock variable comparisons, had to use argument
          //capture on everything
          val baseNameCapture = new ArgumentCapture[String]
          val usernameFilterCapture = new ArgumentCapture[String]
          val searchControlCapture = new ArgumentCapture[SearchControls]

          there.was(
            one(mocks.context).search(baseNameCapture, usernameFilterCapture, searchControlCapture)
          )

          searchControlCapture.value.getSearchScope mustEqual 2
          baseNameCapture.value mustEqual "DC=test,DC=test,DC=com"
          usernameFilterCapture.value mustEqual "(sAMAccountName=foo)"
        }

        "call result.hasMore" in {
          there.was(one(mocks.searchResults).hasMore)
        }

        "call result.next" in {
          there.was(one(mocks.searchResults).next())
        }

        "call result.next.getNameInNamespace" in {
          there.was(one(mocks.searchNext).getNameInNamespace)
        }

        "call result.next.getAttributes" in {
          there.was(one(mocks.searchNext).getAttributes)
        }

        "call attributes.get for username, email, firstname and lastname" in {
          there.was(one(mocks.attributes).get("sAMAccountName"))
          there.was(one(mocks.attributes).get("mail"))
          there.was(one(mocks.attributes).get("givenName"))
          there.was(one(mocks.attributes).get("sn"))
        }
      }

      "and result.hasMore is false" in {
        val mocks = createMocks
        mocks.searchResults.hasMore.returns(false)
        val response = mocks.byDomainAuthenticator.authenticate(testDomain1, "foo", "bar")

        "return a UserDoesNotExistException" in {
          response must beLeft[LdapException]
        }
      }
    }

    "if creating the context fails" in {
      val mocks = createMocks
      mocks.contextCreator
        .apply(anyString, anyString)
        .returns(Left(LdapServiceException("oops")))
      val response = mocks.byDomainAuthenticator.authenticate(testDomain1, "foo", "bar")

      "return an error" in {
        response must beLeft
      }
    }
    "lookup a user" should {
      "return a user it can find" in {
        val mocks = createMocks
        val serviceAccount = ServiceAccount("second", "serviceuser", "servicepass")
        val response = mocks.byDomainAuthenticator.lookup(testDomain1, "foo", serviceAccount)

        response must beRight
        "call contextCreator.apply" in {
          there.was(one(mocks.contextCreator).apply("second\\serviceuser", "servicepass"))
        }

        "call the correct search on context" in {
          //because specs2 doesn't compare SearchControls objects properly, we have to use argument capture. And since
          //mockito only allows all raw variable comparisons or mock variable comparisons, had to use argument
          //capture on everything
          val baseNameCapture = new ArgumentCapture[String]
          val usernameFilterCapture = new ArgumentCapture[String]
          val searchControlCapture = new ArgumentCapture[SearchControls]

          there.was(
            one(mocks.context).search(baseNameCapture, usernameFilterCapture, searchControlCapture)
          )

          searchControlCapture.value.getSearchScope mustEqual 2
          baseNameCapture.value mustEqual "DC=test,DC=test,DC=com"
          usernameFilterCapture.value mustEqual "(sAMAccountName=foo)"
        }

        "call result.hasMore" in {
          there.was(one(mocks.searchResults).hasMore)
        }

        "call result.next" in {
          there.was(one(mocks.searchResults).next())
        }

        "call result.next.getNameInNamespace" in {
          there.was(one(mocks.searchNext).getNameInNamespace)
        }

        "call result.next.getAttributes" in {
          there.was(one(mocks.searchNext).getAttributes)
        }

        "call attributes.get for username, email, firstname and lastname" in {
          there.was(one(mocks.attributes).get("sAMAccountName"))
          there.was(one(mocks.attributes).get("mail"))
          there.was(one(mocks.attributes).get("givenName"))
          there.was(one(mocks.attributes).get("sn"))
        }
      }
      "return a Failure if the user does not exist" in {
        val mocks = createMocks
        val serviceAccount = ServiceAccount("first", "foo", "bar")
        mocks.searchResults.hasMore.returns(false)
        val response = mocks.byDomainAuthenticator.lookup(testDomain1, "foo", serviceAccount)

        response must beLeft
      }
      "return a Failure when the service user credentials are incorrect" in {
        val mocks = createMocks
        val serviceAccount = ServiceAccount("first", "foo", "bar")
        mocks.contextCreator
          .apply(anyString, anyString)
          .returns(Left(LdapServiceException("oops")))

        val response = mocks.byDomainAuthenticator.lookup(testDomain1, "foo", serviceAccount)

        response must beLeft
      }
    }
  }
  "TestAuthenticator" should {
    "authenticate the test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth
        .authenticate(anyString, anyString)
        .returns(Left(UserDoesNotExistException("should not be here")))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val testUserLookup = underTest.authenticate("testuser", "testpassword")

      testUserLookup must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=testuser",
          "testuser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )

      val recordPagingUserLookup = underTest.lookup("recordPagingTestUser")

      recordPagingUserLookup must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=recordPagingTestUser",
          "recordPagingTestUser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )
      there.were(noCallsTo(mockLdapAuth))
    }
    "authenticate the record paging test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth
        .authenticate(anyString, anyString)
        .returns(Left(UserDoesNotExistException("should not be here")))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val result = underTest.authenticate("recordPagingTestUser", "testpassword")

      result must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=recordPagingTestUser",
          "recordPagingTestUser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )
      there.were(noCallsTo(mockLdapAuth))
    }
    "authenticate a user that is not the test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      val userDetails =
        LdapUserDetails("o=foo,cn=bar", "foo", Some("bar"), Some("baz"), Some("qux"))
      mockLdapAuth.authenticate(anyString, anyString).returns(Right(userDetails))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val result = underTest.authenticate("foo", "bar")

      result must beRight(userDetails)
      there.was(one(mockLdapAuth).authenticate("foo", "bar"))
    }
    "lookup the test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth
        .authenticate(anyString, anyString)
        .returns(Left(UserDoesNotExistException("should not be here")))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val testUserLookup = underTest.lookup("testuser")

      testUserLookup must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=testuser",
          "testuser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )

      val recordPagingUserLookup = underTest.lookup("recordPagingTestUser")

      recordPagingUserLookup must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=recordPagingTestUser",
          "recordPagingTestUser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )

      there.were(noCallsTo(mockLdapAuth))
    }
    "lookup the record paging test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth
        .authenticate(anyString, anyString)
        .returns(Left(UserDoesNotExistException("should not be here")))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val result = underTest.lookup("recordPagingTestUser")

      result must beRight(
        LdapUserDetails(
          "O=test,OU=testdata,CN=recordPagingTestUser",
          "recordPagingTestUser",
          Some("test@test.test"),
          Some("Test"),
          Some("User")
        )
      )
      there.were(noCallsTo(mockLdapAuth))
    }
    "lookup a user that is not the test user" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      val userDetails =
        LdapUserDetails("o=foo,cn=bar", "foo", Some("bar"), Some("baz"), Some("qux"))
      mockLdapAuth.lookup(anyString).returns(Right(userDetails))

      val underTest = new TestAuthenticator(mockLdapAuth)
      val result = underTest.lookup("foo")

      result must beRight(userDetails)
      there.was(one(mockLdapAuth).lookup("foo"))
    }
    "find non-existent users" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth
        .getUsersNotInLdap(List(nonexistentUser))
        .returns(IO(List(nonexistentUser)))

      val underTest = new TestAuthenticator(mockLdapAuth)
      underTest.getUsersNotInLdap(List(nonexistentUser)).unsafeRunSync() must beEqualTo(
        List(nonexistentUser)
      )
    }
    "perform a health check" in {
      val mockLdapAuth = mock[LdapAuthenticator]
      mockLdapAuth.healthCheck().returns(IO(Right(())))
      val underTest = new TestAuthenticator(mockLdapAuth)

      underTest.healthCheck().unsafeRunSync() must beRight(())
    }
  }
  "return a successful health check" in {
    val mockLdapAuth = mock[LdapAuthenticator]
    mockLdapAuth.healthCheck().returns(IO(Right(())).asHealthCheck(classOf[LdapAuthenticatorSpec]))

    new TestAuthenticator(mockLdapAuth).healthCheck().unsafeRunSync() should beRight[Unit]
  }
}
