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
import cats.effect.{ContextShift, IO}
import fs2.concurrent.SignallingRef
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.core.TestMembershipData.okAuth

/**
  * Tests that route constructors using call-by-name parameters re-evaluate the config
  * expression on every request, so a reload is reflected without restarting the server.
  *
  * These tests use a mutable @volatile var as the backing store for the by-name arg, which
  * is exactly what RuntimeVinylDNSConfig.current provides in production.
  */
class LiveConfigPropagationSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with OneInstancePerTest
    with VinylDNSJsonProtocol
    with VinylDNSRouteTestHelper
    with Matchers {

  def actorRefFactory: ActorSystem = system

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  // ------------------------------------------------------------------ helpers

  private val processingDisabled: SignallingRef[IO, Boolean] =
    SignallingRef[IO, Boolean](false).unsafeRunSync()

  private val auth = new TestVinylDNSAuthenticator(okAuth)

  // -----------------------------------------------------------------------
  // BlueGreenRoute — colorRoute(color: => String)
  // -----------------------------------------------------------------------

  "BlueGreenRoute.colorRoute with live by-name color" should {

    "return the initial color" in {
      val liveColor = "blue"
      val route = new BlueGreenRoute {}.colorRoute(liveColor)

      Get("/color") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "blue"
      }
    }

    "return the updated color after the backing value changes (simulates reload)" in {
      @volatile var liveColor = "blue"
      val route = new BlueGreenRoute {}.colorRoute(liveColor)

      // First request — blue
      Get("/color") ~> route ~> check {
        responseAs[String] shouldBe "blue"
      }

      // Simulate config reload updating the color
      liveColor = "green"

      // Second request — must pick up the new value WITHOUT rebuilding the route
      Get("/color") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "green"
      }
    }
  }

  // -----------------------------------------------------------------------
  // StatusRoute — serverConfig: => ServerConfig
  // -----------------------------------------------------------------------

  "StatusRoute.statusRoute with live by-name serverConfig" should {

    "return the initial color/keyName/version in the status response" in {
      val liveCfg = VinylDNSTestHelpers.testServerConfig
      val route = new StatusRoute(liveCfg, auth, processingDisabled).getRoutes

      Get("/status") ~> route ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[CurrentStatus]
        result.color   shouldBe liveCfg.color
        result.keyName shouldBe liveCfg.keyName
        result.version shouldBe liveCfg.version
      }
    }

    "reflect an updated color from serverConfig after simulated reload" in {
      @volatile var liveCfg = VinylDNSTestHelpers.testServerConfig // color = "blue"
      val route = new StatusRoute(liveCfg, auth, processingDisabled).getRoutes

      Get("/status") ~> route ~> check {
        responseAs[CurrentStatus].color shouldBe "blue"
      }

      // Simulate reload: replace the in-memory config
      liveCfg = VinylDNSTestHelpers.testServerConfig.copy(color = "green")

      Get("/status") ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[CurrentStatus].color shouldBe "green"
      }
    }

    "reflect an updated keyName after simulated reload" in {
      @volatile var liveCfg = VinylDNSTestHelpers.testServerConfig
      val route = new StatusRoute(liveCfg, auth, processingDisabled).getRoutes

      Get("/status") ~> route ~> check {
        responseAs[CurrentStatus].keyName shouldBe "vinyldns."
      }

      liveCfg = liveCfg.copy(keyName = "reloaded.")

      Get("/status") ~> route ~> check {
        responseAs[CurrentStatus].keyName shouldBe "reloaded."
      }
    }

    "reflect an updated version after simulated reload" in {
      @volatile var liveCfg = VinylDNSTestHelpers.testServerConfig
      val route = new StatusRoute(liveCfg, auth, processingDisabled).getRoutes

      Get("/status") ~> route ~> check {
        responseAs[CurrentStatus].version shouldBe "unset"
      }

      liveCfg = liveCfg.copy(version = "1.2.3")

      Get("/status") ~> route ~> check {
        responseAs[CurrentStatus].version shouldBe "1.2.3"
      }
    }
  }

  // -----------------------------------------------------------------------
  // BatchChangeRoute — manualReviewConfig: => ManualReviewConfig
  // -----------------------------------------------------------------------

  "BatchChangeRoute with live by-name manualReviewConfig" should {

    "serve the approve route when manualReviewConfig.enabled is true" in {
      val liveCfg = VinylDNSTestHelpers.manualReviewConfig // enabled = true
      import org.scalatestplus.mockito.MockitoSugar
      import vinyldns.api.domain.batch.BatchChangeServiceAlgebra

      val service = new MockitoSugar {}.mock[BatchChangeServiceAlgebra]
      val route = new BatchChangeRoute(
        service,
        VinylDNSTestHelpers.testLimitConfig,
        auth,
        liveCfg
      ).getRoutes

      // The approve path should be routable (will fail with 400/404/auth error, not 404 path-not-found)
      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        // Route exists — will get 4xx from service/auth, but NOT 404 "path does not exist"
        status should not be StatusCodes.NotFound
      }
    }

    "not serve the approve route when manualReviewConfig.enabled is false" in {
      val liveCfg = VinylDNSTestHelpers.manualReviewConfig.copy(enabled = false)
      import org.scalatestplus.mockito.MockitoSugar
      import vinyldns.api.domain.batch.BatchChangeServiceAlgebra

      val service = new MockitoSugar {}.mock[BatchChangeServiceAlgebra]
      val route = new BatchChangeRoute(
        service,
        VinylDNSTestHelpers.testLimitConfig,
        auth,
        liveCfg
      ).getRoutes

      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
    }

    "enable the approve route after a simulated reload turns manualReview on" in {
      @volatile var liveCfg = VinylDNSTestHelpers.manualReviewConfig.copy(enabled = false)
      import org.scalatestplus.mockito.MockitoSugar
      import vinyldns.api.domain.batch.BatchChangeServiceAlgebra

      val service = new MockitoSugar {}.mock[BatchChangeServiceAlgebra]
      val route = new BatchChangeRoute(
        service,
        VinylDNSTestHelpers.testLimitConfig,
        auth,
        liveCfg
      ).getRoutes

      // Disabled — route not present
      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }

      // Simulate config reload enabling manual review
      liveCfg = liveCfg.copy(enabled = true)

      // Same route instance, same request — now the path exists
      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        status should not be StatusCodes.NotFound
      }
    }

    "disable the approve route after a simulated reload turns manualReview off" in {
      @volatile var liveCfg = VinylDNSTestHelpers.manualReviewConfig // enabled = true
      import org.scalatestplus.mockito.MockitoSugar
      import vinyldns.api.domain.batch.BatchChangeServiceAlgebra

      val service = new MockitoSugar {}.mock[BatchChangeServiceAlgebra]
      val route = new BatchChangeRoute(
        service,
        VinylDNSTestHelpers.testLimitConfig,
        auth,
        liveCfg
      ).getRoutes

      // Enabled — route present
      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        status should not be StatusCodes.NotFound
      }

      // Simulate reload disabling manual review
      liveCfg = liveCfg.copy(enabled = false)

      Post(s"/zones/batchrecordchanges/some-id/approve") ~> Route.seal(route) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
    }
  }
}
