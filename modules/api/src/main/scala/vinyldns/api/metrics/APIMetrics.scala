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

package vinyldns.api.metrics
import java.util.concurrent.TimeUnit

import cats.effect.IO
import com.codahale.metrics.Slf4jReporter.LoggingLevel
import com.codahale.metrics.{Metric, MetricFilter, ScheduledReporter, Slf4jReporter}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import pureconfig.module.catseffect.loadConfigF
import vinyldns.core.VinylDNSMetrics

final case class MemoryMetricsSettings(logEnabled: Boolean, logSeconds: Int)
final case class APIMetricsSettings(memory: MemoryMetricsSettings)

object APIMetrics {

  // Output all memory metrics to the log, do not start unless configured
  private val logReporter = Slf4jReporter
    .forRegistry(VinylDNSMetrics.metricsRegistry)
    .filter(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = {
        name.startsWith("memory")
      }
    })
    .withLoggingLevel(LoggingLevel.INFO)
    .outputTo(LoggerFactory.getLogger("MemStats"))
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()

  def initialize(
      settings: APIMetricsSettings,
      reporter: ScheduledReporter = logReporter): IO[Unit] = IO {
    if (settings.memory.logEnabled) {
      reporter.start(settings.memory.logSeconds, TimeUnit.SECONDS)
    }
  }

  def loadSettings(config: Config): IO[APIMetricsSettings] =
    loadConfigF[IO, APIMetricsSettings](config)
}
