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
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Pac4jScalaTemplateHelper, Security, SecurityComponents}
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.mvc._
import play.api.Configuration
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
/*
 * Controller for specific pages - sends requests along to views
 */
@Singleton
class FrontendController @Inject()(
    val controllerComponents: SecurityComponents,
    configuration: Configuration,
    userAccountAccessor: UserAccountAccessor,
    crypto: CryptoAlgebra)(
    implicit val pac4jScalaTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile])
    extends Security[CommonProfile] {

  val oidcEnabled: Boolean = configuration.getOptional[Boolean]("oidc.enabled").getOrElse(false)
  lazy val oidcUsernameField: String =
    configuration.getOptional[String]("oidc.jwt-username-field").getOrElse("username")

  private val vinyldnsServiceBackend =
    configuration
      .getOptional[String]("portal.vinyldns.backend.url")
      .getOrElse("http://localhost:9000")

  private val userAction = if (oidcEnabled) {
    Secure.andThen {
      new OidcFrontendAction(userAccountAccessor.get, userAccountAccessor.create, oidcUsernameField)
    }
  } else {
    Action.andThen(new LdapFrontendAction(userAccountAccessor.get))
  }

  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  implicit lazy val meta: Meta = Meta(configuration)
  private val logger = LoggerFactory.getLogger(classOf[FrontendController])

  def loginPage(): Action[AnyContent] = Action { implicit request =>
    if (oidcEnabled) {
      Redirect("/")
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
    if (oidcEnabled) {
      Redirect("/").withNewSession
    } else {
      Redirect("/login").withNewSession
    }
  }

  def noAccess(): Action[AnyContent] = Action { implicit request =>
    logger.info(
      s"User account for '${request.session.get("username").getOrElse("username not found")}' is locked.")
    Unauthorized(views.html.noAccess())
  }

  def index(): Action[AnyContent] =
    userAction.async { implicit request =>
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

  def serveCredsFile(fileName: String): Action[AnyContent] = userAction.async { implicit request =>
    Logger.info(s"Serving credentials for file $fileName")
    Future(processCsv(request.user))
  }

  private def processCsv(user: User): Result = {
    Logger.info(
      s"Sending credentials for user=${user.userName} with key accessKey=${user.accessKey}")
    Ok(
      s"NT ID, access key, secret key,api url\n%s,%s,%s,%s"
        .format(
          user.userName,
          user.accessKey,
          crypto.decrypt(user.secretKey),
          vinyldnsServiceBackend))
      .as("text/csv")
  }
}
