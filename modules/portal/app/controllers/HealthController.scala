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

package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import vinyldns.core.health.HealthService

@Singleton
class HealthController @Inject() (components: ControllerComponents, healthService: HealthService)
    extends AbstractController(components)
    with CacheHeader {

  def health(): Action[AnyContent] = Action { implicit request =>
    healthService
      .checkHealth()
      .map {
        case Nil => Ok("OK").withHeaders(cacheHeaders: _*)
        case _ =>
          InternalServerError("There was an internal server error.").withHeaders(cacheHeaders: _*)
      }
      .unsafeRunSync()
  }
}
