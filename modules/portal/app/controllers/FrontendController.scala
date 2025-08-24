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
import com.typesafe.config.{ConfigObject, ConfigRenderOptions, ConfigValueFactory}

import javax.inject.{Inject, Singleton}
import models.{CustomLinks, DnsChangeNotices, Meta}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

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
    val canReview = request.user.isSuper || request.user.isSupport
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChanges(request.user.userName, canReview)
      )
    )
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
    val canReview = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.zones.zoneDetail(request.user.userName, canReview, zoneId)))
  }

  def viewRecordSets(): Action[AnyContent] = userAction.async { implicit request =>
    val canReview = request.user.isSuper || request.user.isSupport
    Future(Ok(views.html.recordsets.recordSets(request.user.userName, canReview)))
  }

  def viewAllBatchChanges(): Action[AnyContent] = userAction.async { implicit request =>
    val canReview = request.user.isSuper || request.user.isSupport
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChanges(request.user.userName, canReview)
      )
    )
  }

  def viewBatchChange(batchId: String): Action[AnyContent] = userAction.async { implicit request =>
    logger.info(s"View Batch Change for $batchId")
    val canReview = request.user.isSuper || request.user.isSupport
    val dnsChangeNotices = configuration.get[DnsChangeNotices]("dns-change-notices")
    Future(
      Ok(
        views.html.dnsChanges
          .dnsChangeDetail(request.user.userName, canReview, dnsChangeNotices)
      )
    )
  }

  def viewNewBatchChange(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(views.html.dnsChanges.dnsChangeNew(request.user.userName)))
  }

  def getAllowedDNSProviders: Action[AnyContent] = userAction.async {
    Future {
      val providersConfig = configuration.getOptional[Configuration]("api.dns-provider-portal-fields.providers")
      val providerKeys = providersConfig match {
        case Some(conf) =>
          conf.subKeys.toSeq
        case None =>
          Seq.empty
      }
      Ok(Json.obj("allowedDNSProviders" -> providerKeys))
    }
  }

  def getCreateZoneTemplate(provider: String): Action[AnyContent] = userAction.async {
    Future {
      val templatesPath = s"api.dns-provider-portal-fields.providers.$provider.request-templates"
      val requiredPath = s"api.dns-provider-portal-fields.providers.$provider.required-fields"

      val templateConfigOpt = configuration.getOptional[Configuration](templatesPath)
      val requiredConfigOpt = configuration.getOptional[Configuration](requiredPath)
      (templateConfigOpt, requiredConfigOpt) match {
        case (Some(templateConfig), Some(requiredConfig)) =>
          val renderedTemplates = Json.parse(
            templateConfig.underlying.root().render(ConfigRenderOptions.concise())
          )
          val renderedRequired = Json.parse(
            requiredConfig.underlying.root().render(ConfigRenderOptions.concise())
          )

          Ok(Json.obj(
            "provider" -> provider,
            "request-templates" -> renderedTemplates,
            "required-fields"   -> renderedRequired
          ))

        case _ =>
          NotFound(Json.obj("error" -> s"Missing request-templates or required-fields for provider '$provider'"))
      }
    }
  }
}
