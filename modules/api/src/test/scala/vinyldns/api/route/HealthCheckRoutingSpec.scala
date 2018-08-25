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

import java.io.IOException

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.Mockito.doReturn
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import vinyldns.api.domain.zone.ZoneRepository

import cats.effect._, cats.effect.implicits._, cats.instances.future._

class HealthCheckRoutingSpec
    extends WordSpec
    with ScalatestRouteTest
    with Directives
    with HealthCheckRoute
    with VinylDNSJsonProtocol
    with VinylDNSDirectives
    with OneInstancePerTest
    with Matchers
    with MockitoSugar {

  private val mockZoneRepo = mock[ZoneRepository]
  val healthService = new HealthService(mockZoneRepo)

  "GET on the healthcheck" should {
    "return OK when the zone manager returns a positive result" in {
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone("notFound")

      Get("/health") ~> healthCheckRoute ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return a 500 when the zone manager returns any error" in {
      doReturn(IO.raiseError(new IOException("fail"))).when(mockZoneRepo).getZone("notFound")

      Get("/health") ~> healthCheckRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }
}
