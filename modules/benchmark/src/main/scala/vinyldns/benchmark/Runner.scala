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

package vinyldns.benchmark
import java.util.concurrent.TimeUnit

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.elasticsearch.metrics.ElasticsearchReporter
import vinyldns.core.VinylDNSMetrics
import vinyldns.mysql.repository.{MySqlRecordChangeRepository, MySqlRecordSetRepository, MySqlZoneRepository}

object Runner {

  def main(args: Array[String]): Unit = {
    // startup elastic search reporting
    val elasticHost = ConfigFactory.load().getString("elastic-search-host")
    val reporter = ElasticsearchReporter
      .forRegistry(VinylDNSMetrics.metricsRegistry)
      .hosts(elasticHost)
      .build()
    reporter.start(1, TimeUnit.SECONDS)

    val config = BenchmarkConfig().unsafeRunSync()
    val recordRepo = IO(TestMySqlInstance.recordSetRepository)
      .unsafeRunSync()
      .asInstanceOf[MySqlRecordSetRepository]
    val zoneRepo = IO(TestMySqlInstance.zoneRepository)
      .unsafeRunSync()
      .asInstanceOf[MySqlZoneRepository]
    val changeRepo = IO(TestMySqlInstance.recordChangeRepository)
      .unsafeRunSync()
      .asInstanceOf[MySqlRecordChangeRepository]

    // Run our loader to insert a ton of zones, records, changes
    BulkLoader.run(config, zoneRepo, recordRepo, changeRepo)

    // Run query tests
    RecordSetQueryTester.run(zoneRepo, recordRepo)

    println("FINISHED BENCHMARK!")

    // sleep a little to allow the reporter to output some things
    Thread.sleep(5000)
  }
}
