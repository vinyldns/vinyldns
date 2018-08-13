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

import com.codahale.metrics.{JmxReporter, MetricRegistry}
import nl.grons.metrics.scala.InstrumentedBuilder

object VinylDNSMetrics {

  val metricsRegistry: MetricRegistry = new MetricRegistry

  // Output all VinylDNS metrics as jmx under the "vinyldns.api" domain as milliseconds
  JmxReporter
    .forRegistry(metricsRegistry)
    .inDomain("vinyldns.api")
    .build()
    .start()
}

/**
  * Guidance from the scala-metrics library we are using, this is to be included in classes to help out with
  * metric recording
  */
trait Instrumented extends InstrumentedBuilder {

  val metricRegistry: MetricRegistry = VinylDNSMetrics.metricsRegistry
}
