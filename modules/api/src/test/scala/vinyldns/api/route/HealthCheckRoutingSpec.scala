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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.Mockito.doReturn
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.OneInstancePerTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.effect._
import vinyldns.core.health.HealthCheck.HealthCheckError
import vinyldns.core.health.HealthService

class HealthCheckRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with HealthCheckRoute
    with OneInstancePerTest
    with Matchers
    with MockitoSugar {

  val healthService: HealthService = mock[HealthService]

  "GET on the healthcheck" should {
    "return OK when all datastores return a positive result" in {
      doReturn(IO.pure(List())).when(healthService).checkHealth()
      Get("/health") ~> healthCheckRoute ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return a 500 when the zone manager returns any error" in {
      val err = HealthCheckError("an error!")
      doReturn(IO.pure(List(err))).when(healthService).checkHealth()
      Get("/health") ~> healthCheckRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }
}
