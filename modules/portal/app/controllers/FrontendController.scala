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

import actions.SecuritySupport
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
    securitySupport: SecuritySupport
) extends AbstractController(components) {

  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  implicit lazy val meta: Meta = Meta(configuration)
  private val logger = LoggerFactory.getLogger(classOf[FrontendController])
  private val userAction = securitySupport.frontendAction

  def loginPage(): Action[AnyContent] = securitySupport.loginPage()

  def noAccess(): Action[AnyContent] = securitySupport.noAccess()

  def logout(): Action[AnyContent] = securitySupport.logout()

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
