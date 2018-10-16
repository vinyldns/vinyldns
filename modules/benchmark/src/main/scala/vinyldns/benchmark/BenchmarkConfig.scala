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
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.module.catseffect._

/**
  * Benchmark configuration
  * @param xs - number of xs zones, each contains 10 records
  * @param s - number of s zones, each contains 100 records
  * @param m - number of m zones, each contains 1000 records
  * @param l - number of large zones, each contains 10,000 records
  * @param xl - number of xl zones, each contains 100,000 records
  * @param xxl - number of xxl zones, each contains 1MM records
  */
final case class BenchmarkConfig(xs: Int, s: Int, m: Int, l: Int, xl: Int, xxl: Int)
object BenchmarkConfig {
  def apply(): IO[BenchmarkConfig] =
    loadConfigF[IO, BenchmarkConfig](ConfigFactory.load(), "benchmark")
}
