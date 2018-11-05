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

import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.slf4j.LoggerFactory
import vinyldns.core.route.HealthCheck.{HealthCheckError, HealthCheckResponse}

class HealthService(healthChecks: List[HealthCheckResponse]) {

  private val logger = LoggerFactory.getLogger(classOf[HealthService])

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def checkHealth(): IO[List[HealthCheckError]] =
    healthChecks.parSequence
      .map {
        _.collect {
          case Left(err) => {
            logger.error(s"Health Check Failure: ${err.message}")
            err
          }
        }
      }
}
