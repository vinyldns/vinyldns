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

import akka.event.Logging._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.LogEntry
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import vinyldns.core.domain.auth.AuthPrincipal

class VinylDNSServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with VinylDNSDirectives[Throwable] {

  val vinylDNSAuthenticator: VinylDNSAuthenticator = new TestVinylDNSAuthenticator(
    mock[AuthPrincipal])

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case _ => complete(StatusCodes.InternalServerError)
  }

  private def buildMockRequest(
      path: String = "/path/to/resource",
      body: String = "request body") = {
    val requestHeaders = List[HttpHeader](RawHeader("Authorization", "fake_auth"))
    HttpRequest(uri = Uri(path), entity = HttpEntity(body), headers = requestHeaders)
  }

  private def buildUnloggedRequest(
      path: String = "/path/to/unlogged/resource",
      body: String = "request body") =
    HttpRequest(uri = Uri(path), entity = HttpEntity(body))

  private def buildMockResponse(body: String = "results") =
    HttpResponse(StatusCodes.OK, entity = HttpEntity(body))

  ".logMessage" should {
    "build a string to be logged" in {
      val mockRequest = buildMockRequest()
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include("Authorization='fake_auth'")
      actual should include("protocol=HTTP/1.1")
      actual should include("method=GET")
      actual should include("path=/path/to/resource")
      actual should include("status_code=200")
      actual should include("request_duration=20")
      (actual should not).include("response_body=results")
    }
    "hide the zone id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234-56789-0000")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      actual should include("/zones/")
      actual should include("<zone id>")
      (actual should not).include("1234")
    }
    "hide the zone id and recordset id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234/recordsets/5678-90")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      actual should include("/zones/")
      actual should include("<zone id>")
      (actual should not).include("1234")
      actual should include("/recordsets/")
      actual should include("<record set id>")
      (actual should not).include("5678-90")
    }
    "hide the zone id, recordset id and change id if provided" in {
      val mockRequest = buildMockRequest(path = "/zones/1234/recordsets/5678-90/changes/abcdef")
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      actual should include("/zones/")
      actual should include("<zone id>")
      (actual should not).include("1234")
      actual should include("/recordsets/")
      actual should include("<record set id>")
      (actual should not).include("5678-90")
      actual should include("<change id>")
      (actual should not).include("abcdef")
    }
    "leave the path unchanged if not a zone or recordset" in {
      val path = "/some/other/path"
      val mockRequest = buildMockRequest(path = path)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      actual should include(s"path=$path")
    }
    "hide the TSIG key in the request" in {
      val tsigKey = "this is my tsig key"
      val body =
        s"""{"name":"zone.","connection":{"name":"zone.","key":"$tsigKey","primaryServer":"10.0.0.0"}}"""
      val mockRequest = buildMockRequest(body = body)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(tsigKey)
    }
    "hide the TSIG key in the response" in {
      val tsigKey = "this is my tsig key"
      val body =
        s"""{"zone":{"name":"zone.","connection":{"key":"$tsigKey","primaryServer":"10.0.0.0"},
           |"account":"foo","shared":false}"changeType":"Create","status":"Complete"}""".stripMargin
      val mockRequest = buildMockRequest()
      val mockResponse = buildMockResponse(body = body)

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(tsigKey)
    }
    "hide multiple TSIGs key in the request and response" in {
      val key1 = "tsig-key-1"
      val key2 = "tsig-key-2"
      val key3 = "tsig-key-3"
      val body = s"""{"key":"$key1","key":"$key3","key":"$key2"}"""
      val mockRequest = buildMockRequest()
      val mockResponse = buildMockResponse(body = body)

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(key1)
      (actual should not).include(key2)
      (actual should not).include(key3)
    }
    "handle spaces around the key label" in {
      val key = "tsig-key"
      val reqBody = s"""{ "key" :"$key"}"""
      val resBody = s"""{"key" :"$key"}"""
      val mockRequest = buildMockRequest(body = reqBody)
      val mockResponse = buildMockResponse(body = resBody)

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(key)
    }
    "handle spaces around the key value" in {
      val key = "tsig-key"
      val reqBody = s"""{"key": "$key","next":"value"}"""
      val resBody = s"""{"key":"$key" ,"next":"value"}"""
      val mockRequest = buildMockRequest(body = reqBody)
      val mockResponse = buildMockResponse(body = resBody)

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(key)
    }
    "handle multiple whitespace characters" in {
      val key = "tsig-key"
      val reqBody = "{\t\t\"key\" \n:\t \"" + key + "\" \f,\"next\":\"value\"}"
      val mockRequest = buildMockRequest(body = reqBody)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(key)
    }
    "handle comma characters in the key" in {
      val key = "abc, 123"
      val reqBody = s"""{"key": "$key","next": "value"}"""
      val mockRequest = buildMockRequest(body = reqBody)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include(key)
    }
    "hide the TSIG key when the request body contains newlines" in {
      val body =
        """
          |{
          | "name": "cap2.",
          | "email": "test@test.com",
          |        "connection": {
          |            "name": "cap.",
          |             "keyName": "cap.",
          |             "key": "simulated",
          |             "primaryServer": "localhost"
          |        }
          |}
        """.stripMargin
      val mockRequest = buildMockRequest(body = body)
      val mockResponse = buildMockResponse()

      val actual = VinylDNSService.logMessage(mockRequest, Some(mockResponse), 20)
      (actual should not).include("simulated")
    }
    "handle response not being provided" in {
      val mockRequest = buildMockRequest()
      val actual = VinylDNSService.logMessage(mockRequest, None, 20)
      (actual should not).include("Response")
    }
  }
  ".buildLogEntry" should {

    "return an optional log entry when given both a request and response" in {
      val unloggedUris =
        Seq(Uri.Path("/health"), Uri.Path("/color"), Uri.Path("/ping"), Uri.Path("/status"))
      val req = buildMockRequest()
      val res = buildMockResponse()

      val underTest = VinylDNSService.buildLogEntry(unloggedUris)(req)
      val actual = underTest(res)

      actual shouldBe defined
      actual.get shouldBe a[LogEntry]
      actual.get.obj shouldBe a[String]
      actual.get.level shouldBe InfoLevel
    }
    "return a log message even if the response is not a response" in {
      val unloggedUris =
        Seq(Uri.Path("/health"), Uri.Path("/color"), Uri.Path("/ping"), Uri.Path("/status"))
      val req = buildMockRequest()
      val res = 0

      val underTest = VinylDNSService.buildLogEntry(unloggedUris)(req)
      val actual = underTest(res)

      actual shouldBe defined
      actual.get shouldBe a[LogEntry]
      actual.get.obj shouldBe a[String]
      actual.get.level shouldBe ErrorLevel
    }
    "not return a log entry when the request is for non-logging requests" in {
      val unloggedUris = Seq(Uri.Path("/path/to/unlogged/resource"))
      val req = buildUnloggedRequest()
      val res = 0

      val underTest = VinylDNSService.buildLogEntry(unloggedUris)(req)
      val actual = underTest(res)

      actual shouldBe None
    }

  }
}
