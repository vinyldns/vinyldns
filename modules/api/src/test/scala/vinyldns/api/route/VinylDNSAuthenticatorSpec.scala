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

package vinyldns.api.route

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.server.RequestContext
import cats.effect._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.domain.auth.AuthPrincipalProvider
import vinyldns.core.TestMembershipData._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.auth.AuthPrincipal

class VinylDNSAuthenticatorSpec extends AnyWordSpec with Matchers with MockitoSugar {
  private val mockAuthenticator = mock[Aws4Authenticator]
  private val mockAuthPrincipalProvider = mock[AuthPrincipalProvider]

  private val underTest =
    new ProductionVinylDNSAuthenticator(mockAuthenticator, mockAuthPrincipalProvider)

  "VinylDNSAuthenticator" should {
    "use Crypto" in {
      val str = "mysecret"
      val mockCrypto = mock[CryptoAlgebra]
      doReturn("decrypted!").when(mockCrypto).decrypt(str)
      val res = underTest.decryptSecret(str, crypto = mockCrypto)
      res shouldNot be(str)
      res shouldBe "decrypted!"
      verify(mockCrypto).decrypt(str)
    }
    "return an authPrincipal when the request is valid" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      doReturn(okUser.accessKey)
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      doReturn(IO.pure(Some(okAuth)))
        .when(mockAuthPrincipalProvider)
        .getAuthPrincipal(any[String])

      doReturn(true)
        .when(mockAuthenticator)
        .authenticateReq(any[HttpRequest], any[List[String]], any[String], any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Right(okAuth)
    }
    "fail if missing Authorization header" in {
      val httpRequest: HttpRequest = HttpRequest()

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      doReturn(okUser.accessKey)
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      doReturn(IO.pure(Some(okAuth)))
        .when(mockAuthPrincipalProvider)
        .getAuthPrincipal(any[String])

      doReturn(true)
        .when(mockAuthenticator)
        .authenticateReq(any[HttpRequest], any[List[String]], any[String], any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthMissing("Authorization header not found"))
    }
    "fail if Authorization header can not be parsed" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "can not parse me" //header can not be parsed
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthRejected("Authorization header could not be parsed"))
    }
    "fail if the access key is missing" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      //missing access key
      doThrow(new MissingAuthenticationTokenException("accessKey not found"))
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthMissing("accessKey not found"))
    }
    "fail if the access key can not be retrieved" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      // access key is not in header
      doThrow(new IllegalArgumentException("Invalid authorization header"))
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthRejected("Invalid authorization header"))
    }
    "fail if the user is locked" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))
      val lockedUserAuth: AuthPrincipal = AuthPrincipal(lockedUser, Seq())

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      doReturn(lockedUser.accessKey)
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      doReturn(IO.pure(Some(lockedUserAuth)))
        .when(mockAuthPrincipalProvider)
        .getAuthPrincipal(any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AccountLocked("Account with username locked is locked"))
    }
    "fail if the user can not be found" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      doReturn("fakeKey")
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      // No User found
      doReturn(IO.pure(None))
        .when(mockAuthPrincipalProvider)
        .getAuthPrincipal(any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthRejected("Account with accessKey fakeKey specified was not found"))
    }
    "fail if signatures can not be validated" in {
      val fakeHttpHeader = mock[HttpHeader]
      doReturn("Authorization").when(fakeHttpHeader).name

      val header = "AWS4-HMAC-SHA256" +
        " Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
        " SignedHeaders=host;range;x-amz-date," +
        " Signature=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      doReturn(header).when(fakeHttpHeader).value

      val httpRequest: HttpRequest = HttpRequest().withHeaders(List(fakeHttpHeader))

      val context: RequestContext = mock[RequestContext]
      doReturn(httpRequest).when(context).request

      doReturn(okUser.accessKey)
        .when(mockAuthenticator)
        .extractAccessKey(any[String])

      doReturn(IO.pure(Some(okAuth)))
        .when(mockAuthPrincipalProvider)
        .getAuthPrincipal(any[String])

      // signature validation fails
      doReturn(false)
        .when(mockAuthenticator)
        .authenticateReq(any[HttpRequest], any[List[String]], any[String], any[String])

      val result = underTest.authenticate(context, "").unsafeRunSync()
      result shouldBe Left(AuthRejected("Request signature could not be validated"))
    }
  }
}
