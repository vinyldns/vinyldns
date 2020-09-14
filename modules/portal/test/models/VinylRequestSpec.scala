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

package models

import java.io.{BufferedInputStream, BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.net.URI
import java.util

import actions.UserRequest
import com.amazonaws.HttpMethod
import com.amazonaws.http.HttpMethodName
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.mvc.AnyContent

import scala.collection.JavaConversions._
import play.api.test._
import play.api.test.Helpers._

import scala.util.parsing.input.StreamReader

@RunWith(classOf[JUnitRunner])
class VinylDNSRequestSpec extends Specification with Mockito {
  "SignableVinylDNSRequest" should {
    "have zero headers for a new request" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()

      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.getHeaders.size() must beEqualTo(0)
    }
    "have more headers after adding a header" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()

      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      val originalCount = underTest.getHeaders.size()
      underTest.addHeader("foo", "bar")

      underTest.getHeaders.size() must beGreaterThan(originalCount)
    }
    "contain the added header" in {
      val key = "foobar"
      val value = "bazqux"
      val mockVinylDNSReq = createMockVinylDNSRequest()

      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.addHeader(key, value)

      underTest.getHeaders.get(key) must beEqualTo(value)
    }
    "decode the resource path included in the url" in {
      val path = "/foo/bar"
      val mockVinylDNSReq = createMockVinylDNSRequest(s"http://some.server$path?baz=qux")
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      underTest.getResourcePath must beEqualTo(path)
    }
    "have zero parameters in a new request" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      underTest.getParameters.size must beEqualTo(0)
    }
    "have more parameters after adding one" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      val originalCount = underTest.getParameters.size()

      underTest.addParameter("foo", "bar")

      (underTest.getParameters.size() must be).greaterThan(originalCount)
    }
    "contain the added parameter" in {
      val key = "foobar"
      val value = "bazqux"
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.addParameter(key, value)

      underTest.getParameters.get(key).contains(value) must beTrue
    }
    "add a value to an existing parameter" in {
      val key = "foobar"
      val value = "qux"
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      underTest.addParameter(key, "baz")

      underTest.addParameter(key, value)

      underTest.getParameters.get(key).contains(value) must beTrue
    }
    "decode the endpoint from the url" in {
      val urlString = "https://some.server/foo/bar?baz=qux"
      val mockVinylDNSReq = createMockVinylDNSRequest(urlString)
      val expected = new URI("https://some.server")
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      underTest.getEndpoint must beEqualTo(expected)
    }
    "extract the proper http method from the vinyldns request" in {
      val values = Seq(
        "GET" -> HttpMethodName.GET,
        "POST" -> HttpMethodName.POST,
        "PUT" -> HttpMethodName.PUT,
        "PATCH" -> HttpMethodName.PATCH,
        "HEAD" -> HttpMethodName.HEAD,
        "DELETE" -> HttpMethodName.DELETE
      )
      values.map {
        case (in, out) =>
          val dr = createMockVinylDNSRequest(method = in)

          val underTest = new SignableVinylDNSRequest(dr)

          underTest.getHttpMethod must beEqualTo(out)
      }
    }
    "time offset is always zero" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.getTimeOffset must beEqualTo(0)
    }
    "content is a stream representation of the body string" in {
      val bodyString = "this is the foo content."
      val mockVinylDNSReq = createMockVinylDNSRequest(payload = Some(bodyString))
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      val reader = new BufferedReader(new InputStreamReader(underTest.getContent))
      reader.readLine() must beEqualTo(bodyString)
    }
    "unwrapped content is a stream representation of the body string" in {
      val bodyString = "this is the foo content."
      val mockVinylDNSReq = createMockVinylDNSRequest(payload = Some(bodyString))
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)
      val reader = new BufferedReader(new InputStreamReader(underTest.getContentUnwrapped))
      reader.readLine() must beEqualTo(bodyString)
    }
    "read limit is always -1" in {
      val mockVinylDNSReq = createMockVinylDNSRequest()
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.getReadLimitInfo.getReadLimit must beEqualTo(-1)
    }
    "original request object is returned unmodified" in {
      val vinyldnsRequest =
        VinylDNSRequest("FOO", "http://some.server.somewhere:9090/path/to/bar", "baz")
      val underTest = new SignableVinylDNSRequest(vinyldnsRequest)

      underTest.getOriginalRequestObject must beTheSameAs(vinyldnsRequest)
    }
    "setting the content changes the input stream to the new content" in {
      val originalBodyString = "this is the foo content."
      val newBodyString = "this is the bar content."
      val mockVinylDNSReq = createMockVinylDNSRequest(payload = Some(originalBodyString))
      val underTest = new SignableVinylDNSRequest(mockVinylDNSReq)

      underTest.setContent(new ByteArrayInputStream(newBodyString.getBytes("UTF-8")))

      val reader = new BufferedReader(new InputStreamReader(underTest.getContent))
      reader.readLine() must beEqualTo(newBodyString)
    }
  }

  private def createMockVinylDNSRequest(
      url: String = "",
      method: String = "GET",
      payload: Option[String] = None
  ) = {
    val req = mock[VinylDNSRequest]
    val uri = new URI(url)
    req.url.returns(s"${uri.getScheme}://${uri.getHost}")
    req.method.returns(method)
    req.payload.returns(payload)
    req.path.returns(new URI(url).getPath)

    req
  }
}
