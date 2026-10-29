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
import models.{CustomLink, CustomLinks}
import org.slf4j.LoggerFactory
import play.api.{Configuration, Environment}
import play.api.libs.json.Json
import play.api.mvc._
import play.filters.csrf.CSRF

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

/*
 * Controller for page routes — serves the React SPA for all authenticated routes.
 * Play injects the CSRF token into <head> so React can read it via:
 *   document.getElementById('csrf').getAttribute('content')
 */
@Singleton
class FrontendController @Inject() (
    components: ControllerComponents,
    configuration: Configuration,
    securitySupport: SecuritySupport,
    env: Environment
) extends AbstractController(components) {

  private val logger = LoggerFactory.getLogger(classOf[FrontendController])
  private val userAction = securitySupport.frontendAction
  private val configuredLinks = CustomLinks(configuration).links

  private def configuredLinksJson: String =
    Json
      .toJson(configuredLinks.map { link: CustomLink =>
        Json.obj(
          "displayOnSidebar" -> link.displayOnSidebar,
          "displayOnLoginScreen" -> link.displayOnLoginScreen,
          "title" -> link.title,
          "href" -> link.href,
          "icon" -> link.icon
        )
      })
      .toString
      .replace("<", "\\u003c")

  /**
   * Reads public/index.html and injects the Play CSRF token into <head>.
   * Tries the classpath first (production: bundled in JAR via sbt dist),
   * then falls back to the filesystem (dev: built to public/ by npm run build).
   */
  private def serveReactApp(implicit request: Request[AnyContent]): Future[Result] = {
    val csrfToken = CSRF.getToken.map(_.value).getOrElse("")
    val contentOpt: Option[String] =
      env.resourceAsStream("public/index.html")
        .orElse {
          val f = new java.io.File(env.rootPath, "public/index.html")
          if (f.exists()) Some(new java.io.FileInputStream(f)) else None
        }
        .map { is =>
          val src = Source.fromInputStream(is, "UTF-8")
          try src.mkString finally src.close()
        }
    contentOpt match {
      case Some(content) =>
        val html = content.replace(
          "<head>",
          s"""<head>
             |    <meta id="csrf" name="csrf-token" content="$csrfToken" />
             |    <script>window.__VINYLDNS_PORTAL_LINKS__ = $configuredLinksJson;</script>""".stripMargin
        )
        Future.successful(Ok(html).as("text/html"))
      case None =>
        logger.warn("React index.html not found — run: npm run build")
        Future.successful(ServiceUnavailable("React app not built. Run: npm run build").as("text/plain"))
    }
  }

  def loginPage(): Action[AnyContent]  = securitySupport.loginPage()
  def noAccess(): Action[AnyContent]   = securitySupport.noAccess()
  def logout(): Action[AnyContent]     = securitySupport.logout()

  def index(): Action[AnyContent]                          = userAction.async { implicit request => serveReactApp }
  def catchAll(path: String): Action[AnyContent]            = userAction.async { implicit request => serveReactApp }
  def viewAllGroups(): Action[AnyContent]                 = userAction.async { implicit request => serveReactApp }
  def viewGroup(groupId: String): Action[AnyContent]      = userAction.async { implicit request => serveReactApp }
  def viewAllZones(): Action[AnyContent]                  = userAction.async { implicit request => serveReactApp }
  def viewZone(zoneId: String): Action[AnyContent]        = userAction.async { implicit request => serveReactApp }
  def viewRecordSets(): Action[AnyContent]                = userAction.async { implicit request => serveReactApp }
  def viewAllBatchChanges(): Action[AnyContent]           = userAction.async { implicit request => serveReactApp }
  def viewBatchChange(batchId: String): Action[AnyContent] = userAction.async { implicit request => serveReactApp }
  def viewNewBatchChange(): Action[AnyContent]            = userAction.async { implicit request => serveReactApp }
}
