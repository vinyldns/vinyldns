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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Aws4AuthenticatorSpec extends AnyWordSpec with Matchers {

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
}
