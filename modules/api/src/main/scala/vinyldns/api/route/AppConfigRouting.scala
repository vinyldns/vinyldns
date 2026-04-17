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

import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.slf4j.{Logger, LoggerFactory}
import spray.json._
import vinyldns.api.domain.zone._
import vinyldns.api.domain.config.AppConfigServiceAlgebra
import vinyldns.core.domain.config._

trait JsonSupport extends DefaultJsonProtocol {

  implicit val appConfigFormat: RootJsonFormat[AppConfig] = jsonFormat2(AppConfig)
  implicit val appConfigResponseFormat: RootJsonFormat[AppConfigResponse] =
    jsonFormat4(AppConfigResponse)
}


class AppConfigRoute(
                        appConfigService: AppConfigServiceAlgebra,
                        val vinylDNSAuthenticator: VinylDNSAuthenticator
                      ) extends Directives
  with JsonSupport
  with VinylDNSDirectives[Throwable] {

  def getRoutes: Route = configRoutes ~ configReloadRoute

  def logger: Logger = LoggerFactory.getLogger(classOf[AppConfig])

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case ConfigNotFound(msg) => complete(StatusCodes.NotFound, msg)
    case ConfigAlreadyExists(msg) => complete(StatusCodes.Conflict, msg)
    case InvalidRequest(msg) => complete(StatusCodes.UnprocessableEntity, msg)
    case NotAuthorizedError(msg) => complete(StatusCodes.Forbidden, msg)
    case ConfigValidationError(msg) => complete(StatusCodes.BadRequest, msg)
  }

      implicit val mapFormat: RootJsonFormat[Map[String, String]] =
    new RootJsonFormat[Map[String, String]] {
      def write(m: Map[String, String]): JsValue = JsObject(m.mapValues(JsString(_)))
      def read(json: JsValue): Map[String, String] = json.asJsObject.fields.mapValues(_.convertTo[String])
    }

  implicit val appConfigListResponseFormat: RootJsonFormat[AppConfigListResponse] =
    new RootJsonFormat[AppConfigListResponse] {
      def write(r: AppConfigListResponse): JsValue = JsObject(
        "total"   -> JsNumber(r.total),
        "configs" -> JsArray(r.configs.map(appConfigResponseFormat.write): _*)
      )
      def read(json: JsValue): AppConfigListResponse = {
        val obj = json.asJsObject
        AppConfigListResponse(
          total   = obj.fields("total").convertTo[Int],
          configs = obj.fields("configs").convertTo[List[AppConfigResponse]]
        )
      }
    }

  val configRoutes: Route =
    pathPrefix("appconfig") {
      concat(
        createConfig,
        getEffectiveConfig,
        getConfig,
        getAllConfigs,
        updateConfig,
        deleteConfig
      )
    }

  private def createConfig: Route =
    (post & pathEndOrSingleSlash) {
      authenticateAndExecuteWithEntity[AppConfigResponse, AppConfig] {
        (auth, request) =>
          appConfigService.createAppConfig(request.key, request.value, auth)
      } { response =>
        complete(StatusCodes.Created, response)
      }
    }

  private def getEffectiveConfig: Route =
    (get & path("effective")) {
      authenticateAndExecute[Map[String, String]] { auth =>
        appConfigService.getEffectiveConfig(auth)
      } { snapshot =>
        complete(StatusCodes.OK, snapshot.toJson)
      }
    }

  private def getConfig: Route =
    (get & path(Segment)) { key =>
      authenticateAndExecute[AppConfigResponse] { auth =>
        appConfigService.getAppConfig(key, auth)
      } { response =>
        complete(StatusCodes.OK, response)
      }
    }

  private def getAllConfigs: Route =
    (get & pathEndOrSingleSlash) {
      authenticateAndExecute[AppConfigListResponse] { auth =>
        appConfigService.getAllAppConfigs(auth)
      } { response =>
        complete(StatusCodes.OK, response.toJson)
      }
    }

  private def updateConfig: Route =
    (put & path(Segment)) { key =>
      authenticateAndExecuteWithEntity[AppConfigResponse, AppConfig] {
        (auth, request) =>
          appConfigService.updateAppConfig(key, request.value, auth)
      } { response =>
        complete(StatusCodes.OK, response)
      }
    }

  private def deleteConfig: Route =
    (delete & path(Segment)) { key =>
      authenticateAndExecute[Boolean] { auth =>
        appConfigService.deleteAppConfig(key, auth)
      } { _ =>
        complete(StatusCodes.NoContent)
      }
    }

  private def configReloadRoute: Route =
    path("config" / "reload") {
      post {
        authenticateAndExecute[String] { auth =>
          appConfigService.reloadConfig(auth)
        } { msg =>
          complete(StatusCodes.OK, msg)
        }
      }
    }
}