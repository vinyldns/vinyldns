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

import cats.scalatest.EitherMatchers
import org.mockito.Mockito.doReturn
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.ResultHelpers
import vinyldns.api.domain.zone.ZoneRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HealthServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  private val mockZoneRepo = mock[ZoneRepository]
  val underTest = new HealthService(mockZoneRepo)

  "Checking Status" should {
    "return an error if the zone repository could not be reached" in {
      doReturn(Future.failed(new RuntimeException("fail"))).when(mockZoneRepo).getZone("notFound")
      val result = leftResultOf(underTest.checkHealth().value)
      result shouldBe a[RuntimeException]
    }

    "return success if the zone repository returns appropriately" in {
      doReturn(Future.successful(None)).when(mockZoneRepo).getZone("notFound")
      val result = awaitResultOf(underTest.checkHealth().value)
      result should be(right)
    }
  }
}
