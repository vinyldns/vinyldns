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

import actions.{LdapFrontendAction, OidcFrontendAction}
import javax.inject.{Inject, Singleton}
import models.{CustomLinks, Meta}
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.mvc._
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/*
 * Controller for specific pages - sends requests along to views
 */
@Singleton
class FrontendController @Inject()(
    components: ControllerComponents,
    configuration: Configuration,
    userAccountAccessor: UserAccountAccessor,
    oidcAuthenticator: OidcAuthenticator
) extends AbstractController(components) {

  val ID_TOKEN = "idToken"

  private val oidcEnabled: Boolean =
    configuration.getOptional[Boolean]("oidc.enabled").getOrElse(false)
  private val userAction = if (oidcEnabled) {
    Action.andThen(new OidcFrontendAction(userAccountAccessor.get))
  } else {
    Action.andThen(new LdapFrontendAction(userAccountAccessor.get))
  }

  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  implicit lazy val meta: Meta = Meta(configuration)
  private val logger = LoggerFactory.getLogger(classOf[FrontendController])

  def loginPage(): Action[AnyContent] = Action { implicit request =>
    if (oidcEnabled) {
      request.session.get(ID_TOKEN) match {
        case Some(t) => Redirect("/index")
        case None =>
          val oidcRedirect = oidcAuthenticator.oidcGetCode()
          Redirect(oidcRedirect.url, oidcRedirect.queryString)
      }
    } else {
      request.session.get("username") match {
        case Some(_) => Redirect("/index")
        case None =>
          val flash = request.flash
          Logger.error(s"$flash")
          VinylDNS.Alerts.fromFlash(flash) match {
            case Some(VinylDNS.Alert("danger", message)) =>
              Ok(views.html.login(Some(message)))
            case _ =>
              Ok(views.html.login())
          }
      }
    }
  }

  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect("/login").withNewSession
  }

  def noAccess(): Action[AnyContent] = Action { implicit request =>
    logger.info(
      s"User account for '${request.session.get("username").getOrElse("username not found")}' is locked.")
    Unauthorized(views.html.noAccess())
  }

  def index(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.zones.zones(request.user.userName)))
  }

  def viewAllGroups(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.groups.groups(request.user.userName)))
  }

  def viewGroup(groupId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View group for $groupId")
    Future(Ok(views.html.groups.groupDetail(request.user.userName)))
  }

  def viewAllZones(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.zones.zones(request.user.userName)))
  }

  def viewZone(zoneId: String): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.zones.zoneDetail(request.user.userName, zoneId)))
  }

  def viewAllBatchChanges(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.batchChanges.batchChanges(request.user.userName)))
  }

  def viewBatchChange(batchId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View Batch Change for $batchId")
    Future(Ok(views.html.batchChanges.batchChangeDetail(request.user.userName)))
  }

  def viewNewBatchChange(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.batchChanges.batchChangeNew(request.user.userName)))
  }
}
