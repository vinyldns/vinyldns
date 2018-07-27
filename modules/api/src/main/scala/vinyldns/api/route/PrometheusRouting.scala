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

import java.io.StringWriter

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

import scala.collection.JavaConverters._

trait PrometheusRoute extends Directives {

  def collectorRegistry: CollectorRegistry

  private val `text/plain; version=0.0.4; charset=utf-8` = ContentType {
    MediaType.customWithFixedCharset(
      "text",
      "plain",
      HttpCharsets.`UTF-8`,
      params = Map("version" -> "0.0.4")
    )
  }

  def renderMetrics(registry: CollectorRegistry, names: Set[String]): String = {
    val writer = new StringWriter()
    TextFormat.write004(writer, registry.filteredMetricFamilySamples(names.toSet.asJava))
    writer.toString
  }

  val prometheusRoute =
    (get & path("metrics" / "prometheus") & parameter('name.*)) { names =>
      val content = renderMetrics(collectorRegistry, names.toSet)
      complete {
        HttpResponse(entity = HttpEntity(`text/plain; version=0.0.4; charset=utf-8`, content))
      }
    }
}
