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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.data.EitherT
import cats.effect.IO
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.config.RuntimeVinylDNSConfig
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.TestMembershipData._


class TestConfigReloadRoute(
    val vinylDNSAuthenticator: VinylDNSAuthenticator,
    reloadEffect: IO[Unit]
) extends VinylDNSJsonProtocol
    with VinylDNSDirectives[Throwable] {

  override def logger: Logger = LoggerFactory.getLogger(classOf[TestConfigReloadRoute])

  override def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case e: NotAuthorizedError => complete(StatusCodes.Forbidden, e.msg)
    case ex: Throwable         => complete(StatusCodes.InternalServerError, ex.getMessage)
  }

  override def getRoutes: Route = reloadConfigRoute

  val reloadConfigRoute: Route =
    path("appconfig" / "reload") {
      (post & monitor("Endpoint.reloadConfig")) {
        authenticateAndExecute { authPrincipal =>
          if (!authPrincipal.isSuper) {
            logger.warn(
              s"User ${authPrincipal.signedInUser.userName} attempted to reload config without permission"
            )
            EitherT.leftT[IO, Unit](
              NotAuthorizedError(
                s"User ${authPrincipal.signedInUser.userName} is not authorized to reload the application config"
              )
            )
          } else {
            EitherT.liftF(
              for {
                _ <- IO(logger.info(s"Config reload triggered by ${authPrincipal.signedInUser.userName}"))
                _ <- reloadEffect
                _ <- IO(logger.info("Configuration reload completed"))
              } yield ()
            )
          }
        } { _ => complete(StatusCodes.OK, "Application configuration reloaded successfully") }
      }
    }
}

class ConfigReloadRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with OneInstancePerTest
    with VinylDNSJsonProtocol
    with VinylDNSRouteTestHelper
    with Matchers {

  def actorRefFactory: ActorSystem = system

  private val successReload: IO[Unit] = IO.unit
  private val failingReload: IO[Unit] = IO.raiseError(new RuntimeException("config parse failure"))

  // Routes wired with different auth principals
  private val okRoute: Route =
    new TestConfigReloadRoute(new TestVinylDNSAuthenticator(okAuth), successReload).reloadConfigRoute

  private val superUserRoute: Route =
    new TestConfigReloadRoute(new TestVinylDNSAuthenticator(superUserAuth), successReload).reloadConfigRoute

  private val supportUserRoute: Route =
    new TestConfigReloadRoute(new TestVinylDNSAuthenticator(supportUserAuth), successReload).reloadConfigRoute

  private val failRoute: Route =
    new TestConfigReloadRoute(new TestVinylDNSAuthenticator(superUserAuth), failingReload).reloadConfigRoute

  "POST /appconfig/reload" should {

    "return 200 OK with success message for a super user" in {
      Post("/appconfig/reload") ~> superUserRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("reloaded successfully")
      }
    }

    "return 403 Forbidden for a regular authenticated user" in {
      Post("/appconfig/reload") ~> okRoute ~> check {
        status shouldBe StatusCodes.Forbidden
        responseAs[String] should include("is not authorized to reload")
      }
    }

    "return 403 Forbidden for a support user (not super)" in {
      Post("/appconfig/reload") ~> supportUserRoute ~> check {
        status shouldBe StatusCodes.Forbidden
        responseAs[String] should include("is not authorized to reload")
      }
    }

    "return 500 Internal Server Error when the reload effect fails" in {
      Post("/appconfig/reload") ~> failRoute ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "reject GET requests as method not allowed" in {
      Get("/appconfig/reload") ~> Route.seal(okRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
    }

    "reject PUT requests as method not allowed" in {
      Put("/appconfig/reload") ~> Route.seal(okRoute) ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
      }
    }

    "not route a different path" in {
      Post("/appconfig/other") ~> Route.seal(okRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "POST /appconfig/reload with RuntimeVinylDNSConfig" should {

    "actually invoke RuntimeVinylDNSConfig.reload and keep config valid" in {
      // Wire the real reload effect
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      val realRoute =
        new TestConfigReloadRoute(
          new TestVinylDNSAuthenticator(superUserAuth),
          RuntimeVinylDNSConfig.reload()
        ).reloadConfigRoute

      Post("/appappconfig/reload") ~> realRoute ~> check {
        status shouldBe StatusCodes.OK
        // After the HTTP call completes the reload IO has run; current must still be valid
        RuntimeVinylDNSConfig.current should not be null
        RuntimeVinylDNSConfig.current.validEmailConfig should not be null
      }
    }

    "leave current and currentIO in sync after reload via the route" in {
      RuntimeVinylDNSConfig.init().unsafeRunSync()
      val realRoute =
        new TestConfigReloadRoute(
          new TestVinylDNSAuthenticator(superUserAuth),
          RuntimeVinylDNSConfig.reload()
        ).reloadConfigRoute

      Post("/appconfig/reload") ~> realRoute ~> check {
        status shouldBe StatusCodes.OK
        val sync = RuntimeVinylDNSConfig.current
        val async = RuntimeVinylDNSConfig.currentIO.unsafeRunSync()
        sync.serverConfig.color  shouldBe async.serverConfig.color
        sync.validEmailConfig    shouldBe async.validEmailConfig
      }
    }
  }
}
