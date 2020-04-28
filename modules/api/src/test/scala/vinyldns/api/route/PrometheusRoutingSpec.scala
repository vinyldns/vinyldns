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

import akka.http.scaladsl.model.{HttpProtocol, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.core.VinylDNSMetrics

class PrometheusRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with PrometheusRoute
    with BeforeAndAfterEach
    with MockitoSugar
    with Matchers {

  val metricRegistry = VinylDNSMetrics.metricsRegistry

  val collectorRegistry = CollectorRegistry.defaultRegistry

  collectorRegistry.register(new DropwizardExports(metricRegistry))

  "GET /metrics/prometheus" should {
    "return metrics logged in prometheus" in {
      Get("/metrics/prometheus") ~> prometheusRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val resultStatus = responseAs[HttpResponse]
        resultStatus.protocol shouldBe HttpProtocol("HTTP/1.1")
      }
    }
  }
}
