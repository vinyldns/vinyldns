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

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import nl.grons.metrics.scala.{Histogram, Meter}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, OneInstancePerTest, WordSpec}
import vinyldns.api.domain.zone.ZoneServiceAlgebra
import vinyldns.core.route.Monitor

import scala.util.Failure

class VinylDNSDirectivesSpec
    extends WordSpec
    with ScalatestRouteTest
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with VinylDNSDirectives
    with Directives
    with VinylDNSJsonProtocol
    with BeforeAndAfterEach {

  private val mockLatency = mock[Histogram]
  private val mockErrors = mock[Meter]

  val zoneRoute: Route =
    new ZoneRoute(mock[ZoneServiceAlgebra], mock[VinylDNSAuthenticator]).getRoutes
  val zoneService: ZoneServiceAlgebra = mock[ZoneServiceAlgebra]

  val vinylDNSAuthenticator: VinylDNSAuthenticator = mock[VinylDNSAuthenticator]

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case _ => failWith(e)
  }

  class TestMonitor extends Monitor("test") {
    override val latency: Histogram = mockLatency
    override val errors: Meter = mockErrors
  }

  override def getMonitor(name: String) = new TestMonitor

  private def simulateException: String = throw new IOException("simulated")

  private val testRoute =
    (path("test") & get & monitor("test")) {
      complete("ok")
    } ~
      (path("failure") & get & monitor("failure")) {
        failWith(new IOException("fail"))
      } ~
      (path("exception") & get & monitor("exception")) {
        complete(simulateException)
      }

  override def beforeEach(): Unit =
    reset(mockLatency, mockErrors)

  ".handleAuthenticateError" should {
    "respond with Forbidden status if account is locked" in {
      val trythis = handleAuthenticateError(AccountLocked("error"))

      trythis shouldBe HttpResponse(
        status = StatusCodes.Forbidden,
        entity = HttpEntity(s"Authentication Failed: error")
      )
    }

    "respond with Unauthorized status for other authentication errors" in {
      val trythis = handleAuthenticateError(AuthRejected("error"))

      trythis shouldBe HttpResponse(
        status = StatusCodes.Unauthorized,
        entity = HttpEntity(s"Authentication Failed: error")
      )
    }
  }

  "The monitor directive" should {
    "record when completing an HttpResponse normally" in {
      Get("/test") ~> testRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "ok"

        verify(mockLatency).+=(anyLong)
      }
    }
    "record when an unexpected exception is thrown" in {
      Get("/exception") ~> Route.seal(testRoute) ~> check {
        verify(mockLatency).+=(anyLong)
        verify(mockErrors).mark()
      }
    }
    "record when completing an HttpResponse with a failure" in {
      Get("/failure") ~> testRoute ~> check {
        status shouldBe StatusCodes.InternalServerError

        verify(mockLatency).+=(anyLong)
        verify(mockErrors).mark()
      }
    }
    "record method" should {
      val testMonitor = new TestMonitor()
      val mockHttpResponse = HttpResponse()

      "increment the latency and not errors when recording a successful HttpResponse" in {
        val result = record(testMonitor, System.nanoTime())(mockHttpResponse)
        result shouldBe mockHttpResponse

        verify(mockLatency).+=(anyLong())
        verifyZeroInteractions(mockErrors)
      }
      "increment the latency and the errors when recording a 500 HttpResponse" in {
        val httpResponse = HttpResponse(StatusCodes.ServiceUnavailable)

        val result = record(testMonitor, System.nanoTime())(httpResponse)
        result shouldBe httpResponse

        verify(mockLatency).+=(anyLong())
        verify(mockErrors).mark()
      }
      "increment the latency and the errors when recording an exception" in {
        an[IOException] should be thrownBy record(testMonitor, System.nanoTime())(
          Failure(new IOException("fail")))

        verify(mockLatency).+=(anyLong())
        verify(mockErrors).mark()
      }
      "do nothing if the parameter is unexpected" in {
        val result = record(testMonitor, System.nanoTime())(100)
        result shouldBe 100

        verifyZeroInteractions(mockLatency)
        verifyZeroInteractions(mockErrors)
      }
    }
  }

  "GET" should {
    "return 404 NotFound if route doesn't exist" in {
      Get("/no-existo") ~> Route.seal(zoneRoute) ~> check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

  "PUT" should {
    "return 405 MethodNotAllowed if HTTP method is not allowed for that route" in {
      Put("/zones") ~> Route.seal(zoneRoute) ~> check {
        response.status shouldBe StatusCodes.MethodNotAllowed
      }
    }
  }
}
