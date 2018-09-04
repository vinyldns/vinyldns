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
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import nl.grons.metrics.scala.{Histogram, Meter}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, OneInstancePerTest, WordSpec}
import vinyldns.core.route.Monitor

class VinylDNSDirectivesSpec
    extends WordSpec
    with ScalatestRouteTest
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with VinylDNSDirectives
    with Directives
    with BeforeAndAfterEach {

  private val mockLatency = mock[Histogram]
  private val mockErrors = mock[Meter]

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
  }
}
