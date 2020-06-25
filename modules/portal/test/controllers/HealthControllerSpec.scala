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
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{GET, status}
import play.api.test.{FakeRequest, Helpers}
import vinyldns.core.health.HealthService
import vinyldns.core.health.HealthCheck._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class HealthControllerSpec extends Specification {

  val components: ControllerComponents = Helpers.stubControllerComponents()

  "HealthController" should {
    "send 200 if the healthcheck succeeds" in {
      val healthService =
        new HealthService(List(IO.unit.attempt.asHealthCheck(classOf[HealthControllerSpec])))
      val controller = new HealthController(components, healthService)

      val result = controller
        .health()
        .apply(FakeRequest(GET, "/health"))

      status(result) must beEqualTo(200)
    }
    "send 500 if a healthcheck fails" in {
      val err = IO
        .raiseError(new RuntimeException("bad!!"))
        .attempt
        .asHealthCheck(classOf[HealthControllerSpec])
      val healthService =
        new HealthService(List(IO.unit.attempt.asHealthCheck(classOf[HealthControllerSpec]), err))
      val controller = new HealthController(components, healthService)

      val result = controller
        .health()
        .apply(FakeRequest(GET, "/health"))

      status(result) must beEqualTo(500)
    }
  }
}
