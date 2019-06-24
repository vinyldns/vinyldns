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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import vinyldns.core.domain.auth.AuthPrincipal

class VinylDNSServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with VinylDNSDirectives {

  val vinylDNSAuthenticator: VinylDNSAuthenticator = new TestVinylDNSAuthenticator(
    mock[AuthPrincipal])

  val emptyStringToString = Map.empty[String, String]

  private val authRequestHeaders = List[HttpHeader](RawHeader("Authorization", "fake_auth"))

  private def buildMockRequest(
      path: String = "/path/to/resource",
      body: String = "request body",
      headers: List[HttpHeader] = List.empty) = {
    val requestHeaders = authRequestHeaders ++ headers
    HttpRequest(uri = Uri(path), entity = HttpEntity(body), headers = requestHeaders)
  }

  private def buildMockResponse(body: String = "results", status: StatusCode = StatusCodes.OK) =
    HttpResponse(status, entity = HttpEntity(body))

  ".logMessage" should {

    "build a http access info to be logged" in {
      val mockRequest = buildMockRequest()
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/path/to/resource"),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }

    "build a http access info with header(except auth) to be logged" in {
      val mockRequest =
        buildMockRequest(
          headers = List[HttpHeader](
            RawHeader("X-CustomHeader", "CustomValue"),
            RawHeader("User-Agent", "VinylDNSServiceSpec")
          ))
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/path/to/resource"),
          "request" -> Map(
            "method" -> "GET",
            "headers" -> Map(
              "X-CustomHeader" -> "CustomValue",
              "User-Agent" -> "VinylDNSServiceSpec")),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }

    "build a http access info with error response payload to be logged" in {
      val mockRequest = buildMockRequest()
      val mockResponse = buildMockResponse("error message", StatusCodes.BadRequest)

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/path/to/resource"),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map(
            "duration" -> 20,
            "status_code" -> 400,
            "body" -> Map(
              "content" -> "HttpEntity.Strict(text/plain; charset=UTF-8,error message)")),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }

    "hide the zone id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234-56789-0000")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/zones/<zone id>"),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))
      actual should be(expected)
    }

    "hide the zone id and recordset id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234/recordsets/5678-90")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/zones/<zone id>/recordsets/<record set id>"),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }

    "hide the zone id, recordset id and change id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234/recordsets/5678-90/changes/abcdef")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)

      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> "/zones/<zone id>/recordsets/<record set id>/changes/<change id>"),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }

    "leave the path unchanged if not a zone or recordset" in {
      val path = "/some/other/path"
      val mockRequest = buildMockRequest(path = path)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      val expected = Map(
        "http" -> Map(
          "url" -> Map("path" -> path),
          "request" -> Map("method" -> "GET", "headers" -> emptyStringToString),
          "response" -> Map("duration" -> 20, "status_code" -> 200),
          "user_agent" -> Map("original" -> "-"),
          "version" -> "HTTP/1.1"
        ))

      actual should be(expected)
    }
  }
}
