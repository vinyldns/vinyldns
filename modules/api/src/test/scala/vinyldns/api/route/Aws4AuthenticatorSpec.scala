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

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.headers.{Host, RawHeader}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Aws4AuthenticatorSpec extends AnyWordSpec with Matchers {

  private val auth = new Aws4Authenticator()
  private val secret = "secret"
  private val content = """{"name":"example"}"""
  private val scopeDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"))

  private def amzDate(when: Instant): String =
    auth.iso8601Format.format(when).replace("UTC", "Z")

  private def scopeFor(when: Instant): String =
    s"${scopeDateFormat.format(when)}/us-east-1/vinyldns/aws4_request"

  // Builds a request and a matching, genuinely valid signature for the given scope/headers.
  private def signedRequest(
      scope: String,
      signedHeaders: String,
      headers: List[akka.http.scaladsl.model.HttpHeader]
  ): (HttpRequest, List[String]) = {
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = "http://localhost/zones",
      headers = headers,
      entity = HttpEntity(ContentTypes.`application/json`, content)
    )
    val requestDate = auth.getDate(req).get
    val dateTime = auth.iso8601Format.format(requestDate).replace("UTC", "Z")
    val canonicalHeaderSet = signedHeaders.split(';').map(_.toLowerCase).toSet
    val canonicalHeaders = auth.canonicalHeaders(req, canonicalHeaderSet).toSeq
    val canonicalRequest = auth.canonicalReq(req, canonicalHeaders, signedHeaders, content)
    val signature = auth.calculateSig(canonicalRequest, dateTime, scope, secret)
    // authorization list layout: accessKey, scope, signedHeaders, signature
    (req, List("AKID", scope, signedHeaders, signature))
  }

  "getting canonical headers" should {
    "pull the content type" in {
      val entity = HttpEntity(ContentTypes.`application/json`, "")
      val req = HttpRequest(
        entity = entity
      )

      val canonicalHeaders = new Aws4Authenticator().canonicalHeaders(req, Set("content-type"))
      canonicalHeaders.keys should contain("content-type")
      canonicalHeaders("content-type") should contain("application/json")
    }
    "pull the content-length" in {
      val entity = HttpEntity(ContentTypes.`application/json`, "")
      val req = HttpRequest(
        entity = entity
      )

      val canonicalHeaders = new Aws4Authenticator().canonicalHeaders(req, Set("content-length"))
      canonicalHeaders.keys should contain("content-length")
      canonicalHeaders("content-length") should contain("0")
    }
  }

  "authenticateReq" should {
    "accept a fresh, fully-signed request with a valid signature" in {
      val now = Instant.now()
      val (req, authorization) = signedRequest(
        scopeFor(now),
        "host;x-amz-date",
        List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(now)))
      )
      auth.authenticateReq(req, authorization, secret, content) shouldBe true
    }

    "reject a valid signature whose timestamp is outside the freshness window" in {
      val stale = Instant.now().minusSeconds(60 * 60) // one hour old
      val (req, authorization) = signedRequest(
        scopeFor(stale),
        "host;x-amz-date",
        List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(stale)))
      )
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }

    "reject when the credential-scope date does not match the request date" in {
      val now = Instant.now()
      val wrongScope = scopeFor(now.minusSeconds(60 * 60 * 48)) // two days off
      val (req, authorization) = signedRequest(
        wrongScope,
        "host;x-amz-date",
        List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(now)))
      )
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }

    "reject when host is not among the signed headers" in {
      val now = Instant.now()
      val (req, authorization) = signedRequest(
        scopeFor(now),
        "x-amz-date",
        List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(now)))
      )
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }

    "reject when x-amz-date is present but not signed" in {
      val now = Instant.now()
      val (req, authorization) = signedRequest(
        scopeFor(now),
        "host",
        List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(now)))
      )
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }

    "reject when a declared signed header is missing from the request" in {
      val now = Instant.now()
      // sign declaring a 'range' header that is not actually on the request
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = "http://localhost/zones",
        headers = List(Host("example.com"), RawHeader("X-Amz-Date", amzDate(now))),
        entity = HttpEntity(ContentTypes.`application/json`, content)
      )
      val authorization = List("AKID", scopeFor(now), "host;range;x-amz-date", "f" * 64)
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }

    "reject when the request date is missing or unparseable" in {
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = "http://localhost/zones",
        headers = List(Host("example.com")),
        entity = HttpEntity(ContentTypes.`application/json`, content)
      )
      val authorization =
        List("AKID", scopeFor(Instant.now()), "host", "f" * 64)
      auth.authenticateReq(req, authorization, secret, content) shouldBe false
    }
  }
}
