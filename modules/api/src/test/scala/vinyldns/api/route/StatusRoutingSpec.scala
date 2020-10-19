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
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.effect.{ContextShift, IO}
import fs2.concurrent.SignallingRef
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class StatusRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with StatusRoute
    with OneInstancePerTest
    with VinylDNSJsonProtocol
    with BeforeAndAfterEach
    with MockitoSugar
    with Matchers {

  def actorRefFactory: ActorSystem = system

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  val processingDisabled: SignallingRef[IO, Boolean] =
    fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()

  "GET /status" should {
    "return the current status of true" in {
      Get("/status") ~> statusRoute("blue", "unset", "vinyldns.") ~> check {
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
    "disable processing" in {
      Post("/status?processingDisabled=true") ~> statusRoute("blue", "unset", "vinyldns.") ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[CurrentStatus]
        resultStatus.processingDisabled shouldBe true
      }
    }

    "enable processing" in {
      Post("/status?processingDisabled=false") ~> statusRoute("blue", "unset", "vinyldns.") ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[CurrentStatus]
        resultStatus.processingDisabled shouldBe false

        // remember, the signal is the opposite of intent
        processingDisabled.get.unsafeRunSync() shouldBe false
      }
    }
  }
}
