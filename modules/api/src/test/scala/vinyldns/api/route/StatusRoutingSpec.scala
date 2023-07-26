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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.api.VinylDNSTestHelpers.processingDisabled
import vinyldns.core.TestMembershipData.{notAuth, okAuth, superUserAuth}

class StatusRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with OneInstancePerTest
    with VinylDNSJsonProtocol
    with BeforeAndAfterEach
    with MockitoSugar
    with Matchers {

  val statusRoute: Route =
    new StatusRoute(
      VinylDNSTestHelpers.testServerConfig,
      new TestVinylDNSAuthenticator(okAuth),
      VinylDNSTestHelpers.processingDisabled
    ).getRoutes
  val notAuthRoute: Route =
    new StatusRoute(
      VinylDNSTestHelpers.testServerConfig,
      new TestVinylDNSAuthenticator(notAuth),
      VinylDNSTestHelpers.processingDisabled
    ).getRoutes
  val adminUserRoute: Route =
    new StatusRoute(
      VinylDNSTestHelpers.testServerConfig,
      new TestVinylDNSAuthenticator(superUserAuth),
      VinylDNSTestHelpers.processingDisabled
    ).getRoutes

  def actorRefFactory: ActorSystem = system

  "GET /status" should {
    "return the current status of true" in {
      Get("/status") ~> statusRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[CurrentStatus]
        resultStatus.processingDisabled shouldBe false
        resultStatus.color shouldBe "blue"
        resultStatus.keyName shouldBe "vinyldns."
        resultStatus.version shouldBe "unset"
      }
    }
  }

  "POST /status" should {
    "disable processing when it's requested by admin user" in {
      Post("/status?processingDisabled=true") ~> adminUserRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[CurrentStatus]
        resultStatus.processingDisabled shouldBe true
      }
    }

    "enable processing when it's requested by admin user" in {
      Post("/status?processingDisabled=false") ~> adminUserRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[CurrentStatus]
        resultStatus.processingDisabled shouldBe false

        // remember, the signal is the opposite of intent
        processingDisabled.get.unsafeRunSync() shouldBe false
      }
    }

    "not disable processing when it's requested by non-admin user" in {
      Post("/status?processingDisabled=true") ~> statusRoute ~> check {
        response.status shouldBe StatusCodes.Forbidden
      }
    }

    "not enable processing when it's requested by non-admin user" in {
      Post("/status?processingDisabled=false") ~> statusRoute ~> check {
        response.status shouldBe StatusCodes.Forbidden

        // remember, the signal is the opposite of intent
        processingDisabled.get.unsafeRunSync() shouldBe false
      }
    }

    "not disable processing when it's requested by a non-user" in {
      Post("/status?processingDisabled=true") ~> notAuthRoute ~> check {
        response.status shouldBe StatusCodes.Forbidden
      }
    }

    "not enable processing when it's requested by a non-user" in {
      Post("/status?processingDisabled=false") ~> notAuthRoute ~> check {
        response.status shouldBe StatusCodes.Forbidden

        // remember, the signal is the opposite of intent
        processingDisabled.get.unsafeRunSync() shouldBe false
      }
    }
  }
}
