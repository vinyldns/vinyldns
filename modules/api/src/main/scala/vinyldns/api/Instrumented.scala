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

package vinyldns.api

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{JmxReporter, MetricRegistry, Slf4jReporter}
import nl.grons.metrics.scala.InstrumentedBuilder
import org.slf4j.LoggerFactory

object VinylDNSMetrics {

  val metricsRegistry: MetricRegistry = new MetricRegistry

  // Output all VinylDNS metrics as jmx under the "vinyldns.api" domain as milliseconds
  JmxReporter
    .forRegistry(metricsRegistry)
    .inDomain("vinyldns.api")
    .build()
    .start()

  val logReporter: Slf4jReporter =
    Slf4jReporter
      .forRegistry(metricsRegistry)
      .outputTo(LoggerFactory.getLogger("vinyldns.api.metrics"))
      .build()

  val logMetrics = VinylDNSConfig.vinyldnsConfig.getBoolean("metrics.log-to-console")
  if (logMetrics) {
    // Record metrics once per minute
    logReporter.start(1, TimeUnit.MINUTES)
  }
}

/**
  * Guidance from the scala-metrics library we are using, this is to be included in classes to help out with
  * metric recording
  */
trait Instrumented extends InstrumentedBuilder {

  val metricRegistry: MetricRegistry = VinylDNSMetrics.metricsRegistry
}
