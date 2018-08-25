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
import models.CustomLinks
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
class FrontendController @Inject()(components: ControllerComponents, configuration: Configuration)
    extends AbstractController(components) {

  import VinylDNS.withAuthenticatedUser

  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  private val logger = LoggerFactory.getLogger(classOf[FrontendController])

  def loginPage(): Action[AnyContent] = Action { implicit request =>
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

  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect("/login").withNewSession
  }

  def index(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.zones.zones(username)))
    }
  }

  def viewAllGroups(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.groups.groups(username)))
    }
  }

  def viewGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"View group for $groupId")
    withAuthenticatedUser { username =>
      Future(Ok(views.html.groups.groupDetail(username)))
    }
  }

  def viewAllZones(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.zones.zones(username)))
    }
  }

  def viewZone(zoneId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.zones.zoneDetail(username, zoneId)))
    }
  }

  def viewAllBatchChanges(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.batchChanges.batchChanges(username)))
    }
  }

  def viewBatchChange(batchId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"View Batch Change for $batchId")
    withAuthenticatedUser { username =>
      Future(Ok(views.html.batchChanges.batchChangeDetail(username)))
    }
  }

  def viewNewBatchChange(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future(Ok(views.html.batchChanges.batchChangeNew(username)))
    }
  }
}
