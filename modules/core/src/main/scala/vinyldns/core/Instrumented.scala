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

package vinyldns.core

import java.util.concurrent.TimeUnit

import com.codahale.metrics._
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import nl.grons.metrics.scala.InstrumentedBuilder
import org.slf4j.LoggerFactory

object VinylDNSMetrics {

  val metricsRegistry: MetricRegistry = new MetricRegistry
  val memoryRegistry: MetricRegistry = new MetricRegistry

  // Collect memory stats
  memoryRegistry.register("memory", new MemoryUsageGaugeSet())

  // Output all VinylDNS metrics as jmx under the "vinyldns.core" domain as milliseconds
  JmxReporter
    .forRegistry(metricsRegistry)
    .inDomain("vinyldns.core")
    .build()
    .start()

  // Output all memory metrics to the log
  Slf4jReporter
    .forRegistry(memoryRegistry)
    .outputTo(LoggerFactory.getLogger("VinylDNSJVM"))
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()
    .start(10, TimeUnit.SECONDS)
}

/**
  * Guidance from the scala-metrics library we are using, this is to be included in classes to help out with
  * metric recording
  */
trait Instrumented extends InstrumentedBuilder {

  val metricRegistry: MetricRegistry = VinylDNSMetrics.metricsRegistry
}
