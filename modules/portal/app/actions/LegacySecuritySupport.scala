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

package actions
import controllers.{OidcAuthenticator, UserAccountAccessor, VinylDNS}
import javax.inject.Inject
import models.{CustomLinks, Meta}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc._

class LegacySecuritySupport @Inject()(
    components: ControllerComponents,
    userAccountAccessor: UserAccountAccessor,
    configuration: Configuration,
    oidcAuthenticator: OidcAuthenticator)
    extends AbstractController(components)
    with SecuritySupport {
  private val logger = LoggerFactory.getLogger(classOf[LegacySecuritySupport])

  def frontendAction: FrontendActionBuilder =
    new LegacyFrontendAction(
      userAccountAccessor.get,
      oidcAuthenticator,
      components.parsers.anyContent)

  def apiAction: ApiActionBuilder =
    new LegacyApiAction(userAccountAccessor.get, oidcAuthenticator, components.parsers.anyContent)

  def loginPage()(implicit links: CustomLinks, meta: Meta): Action[AnyContent] = Action {
    implicit request =>
      if (oidcAuthenticator.oidcEnabled) {
        request.session.get(VinylDNS.ID_TOKEN) match {
          case Some(_) => Redirect("/index")
          case None =>
            logger.info(s"No ${VinylDNS.ID_TOKEN} in session; Initializing oidc login")
            Redirect(oidcAuthenticator.getCodeCall.toString, 302)
        }
      } else {
        request.session.get("username") match {
          case Some(_) => Redirect("/index")
          case None =>
            val flash = request.flash
            logger.error(s"$flash")
            VinylDNS.Alerts.fromFlash(flash) match {
              case Some(VinylDNS.Alert("danger", message)) =>
                Ok(views.html.login(Some(message)))
              case _ =>
                Ok(views.html.login())
            }
        }
      }
  }

  private def getLoggedInUser(request: RequestHeader) =
    if (oidcAuthenticator.oidcEnabled) {
      request.session
        .get(VinylDNS.ID_TOKEN)
        .flatMap {
          oidcAuthenticator.getValidUsernameFromToken
        }
    } else {
      request.session.get("username")
    }.getOrElse("No user in session")

  def logout(): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Initializing logout for user [${getLoggedInUser(request)}]")
    if (oidcAuthenticator.oidcEnabled) {
      Redirect(oidcAuthenticator.oidcLogoutUrl).withNewSession
    } else {
      Redirect("/login").withNewSession
    }
  }

  def noAccess()(implicit links: CustomLinks, meta: Meta): Action[AnyContent] = Action {
    implicit request =>
      logger.info(s"User account for '${getLoggedInUser(request)}' is locked.")
      Unauthorized(
        views.html.systemMessage(
          """
          |Account locked. Please contact your VinylDNS administrators for more information.
      """.stripMargin))
  }
}
