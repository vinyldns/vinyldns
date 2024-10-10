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
import models.{CustomLinks, DnsChangeNotices, Meta}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/*
 * Controller for specific pages - sends requests along to views
 */
@Singleton
class FrontendController @Inject() (
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
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChanges(request.user.userName, isAdmin)
      )
    )
  }

  def viewAllGroups(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.groups.groups(request.user.userName, isAdmin)))
  }

  def viewGroup(groupId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View group for $groupId")
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.groups.groupDetail(request.user.userName, isAdmin)))
  }

  def viewAllZones(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.zones.zones(request.user.userName, isAdmin)))
  }

  def viewZone(zoneId: String): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.zones.zoneDetail(request.user.userName, isAdmin, zoneId)))
  }

  def viewRecordSets(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.recordsets.recordSets(request.user.userName, isAdmin)))
  }

  def viewAllBatchChanges(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChanges(request.user.userName, isAdmin)
      )
    )
  }

  def viewBatchChange(batchId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View Batch Change for $batchId")
    val isAdmin = request.user.isSuper || request.user.isSupport
    val dnsChangeNotices = configuration.get[DnsChangeNotices]("dns-change-notices")
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChangeDetail(request.user.userName, isAdmin, dnsChangeNotices)
      )
    )
  }

  def viewNewBatchChange(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.dnsChanges.dnsChangeNew(request.user.userName, isAdmin)))
  }

  def viewSettings(): Action[AnyContent] = userAction.async { implicit request =>
    val isAdmin = request.user.isSuper || request.user.isSupport
    Future(
      Ok(
        views.html.settings.settings(request.user.userName, isAdmin)
      )
    )
  }
}
