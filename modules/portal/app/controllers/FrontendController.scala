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

import actions.FrontendAction
import javax.inject.{Inject, Singleton}
import models.{CustomLinks, Meta}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc._

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

  private val oidcEnabled: Boolean = oidcAuthenticator.oidcEnabled
  private val userAction =
    Action.andThen(new FrontendAction(userAccountAccessor.get, oidcAuthenticator))

  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  implicit lazy val meta: Meta = Meta(configuration)
  private val logger = LoggerFactory.getLogger(classOf[FrontendController])

  def loginPage(): Action[AnyContent] = Action { implicit request =>
    if (oidcEnabled) {
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
    if (oidcEnabled) {
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
    if (oidcEnabled) {
      Redirect(oidcAuthenticator.oidcLogoutUrl).withNewSession
    } else {
      Redirect("/login").withNewSession
    }
  }

  def noAccess(): Action[AnyContent] = Action { implicit request =>
    logger.info(s"User account for '${getLoggedInUser(request)}' is locked.")
    Unauthorized(
      views.html.systemMessage(
        """
        |Account locked. Please contact your VinylDNS administrators for more information.
      """.stripMargin))
  }

  def index(): Action[AnyContent] = userAction.async { implicit request =>
    val canReview = request.user.isSuper || request.user.isSupport
    Future(
      Ok(views.html.batchChanges
        .batchChanges(request.user.userName, canReview)))
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
    val canReview = request.user.isSuper || request.user.isSupport
    Future(
      Ok(views.html.batchChanges
        .batchChanges(request.user.userName, canReview)))
  }

  def viewBatchChange(batchId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View Batch Change for $batchId")
    val canReview = request.user.isSuper || request.user.isSupport
    Future(
      Ok(views.html.batchChanges
        .batchChangeDetail(request.user.userName, canReview)))
  }

  def viewNewBatchChange(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.batchChanges.batchChangeNew(request.user.userName)))
  }
}
