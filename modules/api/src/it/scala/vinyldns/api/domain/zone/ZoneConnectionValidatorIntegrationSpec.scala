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

package vinyldns.api.domain.zone
import cats.scalatest.EitherMatchers
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSConfig
import vinyldns.core.health.HealthCheck.HealthCheckError

class ZoneConnectionValidatorIntegrationSpec extends WordSpec with Matchers with EitherMatchers {
  "ZoneConnectionValidatorIntegrationSpec" should {
    "have a valid health check if we can connect to DNS backend" in {
      val check = new ZoneConnectionValidator(VinylDNSConfig.defaultZoneConnection)
        .healthCheck()
        .unsafeRunSync()
      check should beRight(())
    }

    "respond with a failure if health check fails" in {
      val result =
        new ZoneConnectionValidator(
          VinylDNSConfig.defaultZoneConnection.copy(primaryServer = "localhost:1234"))
          .healthCheck()
          .unsafeRunSync()
      result should beLeft(HealthCheckError("Connection refused (Connection refused)"))
    }
  }
}
