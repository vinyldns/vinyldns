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

import cats.implicits._
import vinyldns.api.Interfaces._
import vinyldns.api.domain.zone.ZoneRepository

import scala.concurrent.ExecutionContext

class HealthService(zoneRepository: ZoneRepository)(implicit ec: ExecutionContext) {

  def checkHealth(): Result[Unit] =
    zoneRepository
      .getZone("notFound")
      .map(_ => ().asRight)
      .recover { case e: Throwable => e.asLeft }
      .toResult
}
