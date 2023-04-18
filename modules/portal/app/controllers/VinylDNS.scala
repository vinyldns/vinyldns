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

import java.util
import java.util.HashMap

import actions.{SecuritySupport, UserRequest}
import akka.util.ByteString
import cats.data.EitherT
import cats.effect.IO
import com.amazonaws.auth.{BasicAWSCredentials, SignerFactory}
import controllers.OidcAuthenticator.ErrorResponse
import javax.inject.{Inject, Singleton}
import models.{CustomLinks, Meta, SignableVinylDNSRequest, VinylDNSRequest}
import org.slf4j.LoggerFactory
import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSClient}
import play.api.mvc._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.{LockStatus, User}
import vinyldns.core.logging.RequestTracing

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object VinylDNS {

  import play.api.mvc._

  val ID_TOKEN = "idToken"

  object Alerts {
    private val TYPE = "alertType"
    private val MSG = "alertMessage"

    def error(msg: String): Flash = Flash(Map(TYPE -> "danger", MSG -> msg))

    def fromFlash(flash: Flash): Option[Alert] =
      (flash.get(TYPE), flash.get(MSG)) match {
        case (Some(alertType), Some(alertMessage)) => Some(Alert(alertType, alertMessage))
        case _ => None
      }
  }
  case class Alert(alertType: String, message: String)

  case class UserInfo(
                       userName: String,
                       firstName: Option[String],
                       lastName: Option[String],
                       email: Option[String],
                       isSuper: Boolean,
                       isSupport: Boolean,
                       id: String,
                       lockStatus: LockStatus
                     )
  object UserInfo {
    def fromUser(user: User): UserInfo =
      UserInfo(
        userName = user.userName,
        firstName = user.firstName,
        lastName = user.lastName,
        email = user.email,
        isSuper = user.isSuper,
        isSupport = user.isSupport,
        id = user.id,
        lockStatus = user.lockStatus
      )
  }

  trait UserDetails {
    val username: String
    val email: Option[String]
    val firstName: Option[String]
    val lastName: Option[String]
  }
}

@Singleton
class VinylDNS @Inject() (
                           configuration: Configuration,
                           authenticator: Authenticator,
                           userAccountAccessor: UserAccountAccessor,
                           wsClient: WSClient,
                           components: ControllerComponents,
                           crypto: CryptoAlgebra,
                           oidcAuthenticator: OidcAuthenticator,
                           securitySupport: SecuritySupport
                         ) extends AbstractController(components)
  with CacheHeader {

  import VinylDNS._
  import play.api.mvc._

  private val logger = LoggerFactory.getLogger(classOf[VinylDNS])
  private val signer = SignerFactory.getSigner("VinylDNS", "us/east")
  private val vinyldnsServiceBackend =
    configuration
      .getOptional[String]("portal.vinyldns.backend.url")
      .getOrElse("http://localhost:9000")

  implicit val lockStatusFormat: Format[LockStatus] = new Format[LockStatus] {
    def reads(json: JsValue): JsResult[LockStatus] = json match {
      case JsString(v) => JsSuccess(LockStatus.withName(v))
      case _ => JsError("LockStatus value was not a string")
    }
    def writes(o: LockStatus): JsValue = JsString(o.toString)
  }

  implicit val userInfoReads: Reads[VinylDNS.UserInfo] = Json.reads[VinylDNS.UserInfo]
  implicit val userInfoWrites: Writes[VinylDNS.UserInfo] = Json.writes[VinylDNS.UserInfo]
  implicit lazy val customLinks: CustomLinks = CustomLinks(configuration)
  implicit lazy val meta: Meta = Meta(configuration)

  private val userAction = securitySupport.apiAction
  private val frontendAction = securitySupport.frontendAction

  def oidcCallback(loginId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Received callback for LoginId [$loginId]")
    val setUrl =
      s"${oidcAuthenticator.redirectUriString}set-oidc-session/$loginId?${request.rawQueryString}"
    Future(Ok(views.html.setOidcSession(setUrl)))
  }

  def setOidcSession(loginId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Setting session for LoginId [$loginId]")

    val details = for {
      code <- EitherT.fromEither[IO](oidcAuthenticator.getCodeFromAuthResponse(request))
      validToken <- oidcAuthenticator.oidcCallback(code, loginId)
      userDetails <- EitherT.fromEither[IO](oidcAuthenticator.getUserFromClaims(validToken))
      userCreate <- EitherT.right[ErrorResponse](processLoginWithDetails(userDetails))
    } yield (userCreate, validToken)

    details.value
      .map {
        case Right((user, token)) =>
          logger.info(
            s"LoginId [$loginId] complete: --LOGIN-- user [${user.userName}] logged in with id ${user.id}"
          )

          val redirectLocation =
            Try {
              new String(java.util.Base64.getDecoder.decode(loginId.split(":").last))
            } match {
              case Success(x) => if (x.nonEmpty) x else "/index"
              case Failure(_) => "/index"
            }

          Redirect(redirectLocation).withSession(ID_TOKEN -> token.toString)
        case Left(err) =>
          logger.error(s"LoginId [$loginId] failed with error: $err")
          InternalServerError(
            views.html.systemMessage(
              """
                |There was an issue when logging in.
                |<a href="/index">Please try again by clicking this link.</a>
                |If the issue persists, contact your VinylDNS Administrators
            """.stripMargin)
          ).withNewSession
      }
      .unsafeToFuture()
  }

  def login(): Action[AnyContent] = Action { implicit request =>
    val userForm = Form(
      tuple(
        "username" -> text,
        "password" -> text
      )
    )
    val (username, password) = userForm.bindFromRequest.get
    processLogin(username, password)
  }

  def newGroup(): Action[AnyContent] = userAction.async { implicit request =>
    val json = request.body.asJson
    val payload = json.map(Json.stringify)
    val vinyldnsRequest =
      new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", "groups", payload)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(response.body)
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getGroup(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups/$id")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(s"group [$id] retrieved with status [${response.status}]")
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getGroupChange(gcid: String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups/change/$gcid")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(s"group change [$gcid] retrieved with status [${response.status}]")
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def listGroupChanges(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = new VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      s"groups/$id/activity",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getUser(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"users/$id")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(s"user [$id] retrieved with status [${response.status}]")
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def deleteGroup(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest = VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"groups/$id")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(s"group [$id] deleted with status [${response.status}]")
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def updateGroup(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val payload = request.body.asJson.map(Json.stringify)
    val vinyldnsRequest =
      VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"groups/$id", payload)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(s"group [$id] updated with status [${response.status}]")
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getGroups(): Action[AnyContent] = userAction.async { implicit request =>
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)

    val vinyldnsRequest =
      VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups", parameters = queryParameters)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getBackendIds(): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest =
      VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/backendids")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getValidEmailDomains(): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest =
      VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups/valid/domains")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getRecordSetCount(zoneId : String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest =
      VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$zoneId/recordsetcount")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getAuthenticatedUserData(): Action[AnyContent] = userAction.async { implicit request =>
    Future {
      Ok(Json.toJson(VinylDNS.UserInfo.fromUser(request.user)))
        .withHeaders(cacheHeaders: _*)
    }
  }

  private def processCsv(user: User): Result = {
    logger.info(
      s"Sending credentials for user=${user.userName} with key accessKey=${user.accessKey}"
    )
    Ok(
      s"NT ID, access key, secret key,api url\n%s,%s,%s,%s"
        .format(
          user.userName,
          user.accessKey,
          crypto.decrypt(user.secretKey.value),
          vinyldnsServiceBackend
        )
    ).as("text/csv")
  }

  def serveCredsFile(fileName: String): Action[AnyContent] = frontendAction.async {
    implicit request =>
      logger.info(s"Serving credentials for file $fileName")
      Future(processCsv(request.user))
  }

  def regenerateCreds(): Action[AnyContent] = userAction.async { implicit request =>
    Future
      .fromTry(processRegenerate(request.user))
      .map(response => {
        Status(200)("Successfully regenerated credentials")
          .withHeaders(cacheHeaders: _*)
          .withSession("username" -> response.userName, "accessKey" -> response.accessKey)
      })
      .recover {
        case _: UserDoesNotExistException =>
          NotFound(s"User ${request.userName} was not found").withHeaders(cacheHeaders: _*)
      }
  }

  private def processRegenerate(user: User): Try[User] = {
    val updatedUser = user.regenerateCredentials()
    val update = for {
      _ <- userAccountAccessor.update(updatedUser, user)
    } yield {
      logger.info(s"Credentials successfully regenerated for ${user.userName}")
      updatedUser
    }
    update.attempt.unsafeRunSync().toTry
  }

  private def createNewUser(details: UserDetails): IO[User] = {
    val newUser =
      User(
        details.username,
        User.generateKey,
        Encrypted(User.generateKey),
        details.firstName,
        details.lastName,
        details.email
      )

    userAccountAccessor.create(newUser).map { u =>
      logger.info(s"User account for ${u.userName} created with id ${u.id}")
      u
    }
  }

  def getUserDataByUsername(username: String): Action[AnyContent] = userAction.async {
    implicit request =>
      {
        for {
          userDetails <- IO.fromEither(authenticator.lookup(username))
          existingAccount <- userAccountAccessor.get(userDetails.username)
          userAccount <- existingAccount match {
            case Some(user) => IO(VinylDNS.UserInfo.fromUser(user))
            case None =>
              createNewUser(userDetails).map(VinylDNS.UserInfo.fromUser)
          }
        } yield userAccount
      }.unsafeToFuture()
        .map(Json.toJson(_))
        .map(Ok(_).withHeaders(cacheHeaders: _*))
        .recover {
          case _: UserDoesNotExistException =>
            NotFound(s"User $username was not found")
          case le: LdapException =>
            InternalServerError(le.getMessage)
        }
  }

  def processLogin(username: String, password: String): Result =
    authenticator.authenticate(username, password) match {
      case Left(error: UserDoesNotExistException) =>
        logger.error(s"Authentication failed for [$username]", error)
        Redirect("/login").flashing(
          VinylDNS.Alerts.error("Authentication failed, please try again")
        )
      case Left(error: LdapException) =>
        logger.error(
          "An unexpected error occurred when authenticating, please contact your VinylDNS " +
            "administrators",
          error
        )
        Redirect("/login").flashing(
          VinylDNS.Alerts
            .error("Authentication failed, please contact your VinylDNS administrators")
        )
      case Right(userDetails: LdapUserDetails) =>
        logger.info(
          s"user [${userDetails.username}] logged in with ldap path [${userDetails.nameInNamespace}]"
        )
        val user = processLoginWithDetails(userDetails).unsafeRunSync()
        logger.info(s"--LOGIN-- user [${user.userName}] logged in with id [${user.id}]")
        Redirect("/index")
          .withSession("username" -> user.userName, "accessKey" -> user.accessKey)
    }

  def processLoginWithDetails(userDetails: UserDetails): IO[User] =
    userAccountAccessor
      .get(userDetails.username)
      .flatMap {
        case None =>
          logger.info(s"Creating user account for ${userDetails.username}")
          createNewUser(userDetails)
        case Some(u) =>
          logger.info(s"User account for ${u.userName} exists with id ${u.id}")
          IO.pure(u)
      }

  def getZones: Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest =
      new VinylDNSRequest("GET", s"$vinyldnsServiceBackend", "zones", parameters = queryParameters)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }
  def getZone(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val vinyldnsRequest = new VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$id")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def getZoneByName(name: String): Action[AnyContent] = userAction.async { implicit request =>
    val vinyldnsRequest =
      new VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/name/$name")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getZoneChange(id: String): Action[AnyContent] = userAction.async { implicit request =>
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest =
      new VinylDNSRequest(
        "GET",
        s"$vinyldnsServiceBackend",
        s"zones/$id/changes",
        parameters = queryParameters)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def syncZone(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val vinyldnsRequest =
      new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", s"zones/$id/sync")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def getRecordSet(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      s"recordsets/$id",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def listRecordSets: Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      "recordsets",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def listRecordSetData: Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      "recordsets",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def listRecordSetsByZone(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      s"zones/$id/recordsets",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def listRecordSetChanges(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest = new VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      s"zones/$id/recordsetchanges",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def addZone(): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val json = request.body.asJson
    val payload = json.map(Json.stringify)
    val vinyldnsRequest =
      new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", "zones", payload)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def updateZone(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val json = request.body.asJson
    val payload = json.map(Json.stringify)
    val vinyldnsRequest =
      new VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"zones/$id", payload)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def addRecordSet(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val json = request.body.asJson
    val payload = json.map(Json.stringify)
    val vinyldnsRequest =
      new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", s"zones/$id/recordsets", payload)
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def deleteZone(id: String): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val vinyldnsRequest = new VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"zones/$id")
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def updateRecordSet(zid: String, rid: String): Action[AnyContent] = userAction.async {
    implicit request =>
      // $COVERAGE-OFF$
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest(
          "PUT",
          s"$vinyldnsServiceBackend",
          s"zones/$zid/recordsets/$rid",
          payload
        )
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    // $COVERAGE-ON$
  }

  def deleteRecordSet(zid: String, rid: String): Action[AnyContent] = userAction.async {
    implicit request =>
      // $COVERAGE-OFF$
      val vinyldnsRequest =
        new VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"zones/$zid/recordsets/$rid")
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    // $COVERAGE-ON$
  }

  private def extractParameters(
                                 params: util.Map[String, util.List[String]]
                               ): Seq[(String, String)] =
    params.asScala.foldLeft(Seq[(String, String)]()) {
      case (acc, (key, values)) =>
        acc ++ values.asScala.map(v => key -> v)
    }

  private def executeRequest(request: VinylDNSRequest, user: User)(
    implicit userRequest: UserRequest[_]
  ) = {
    val signableRequest = new SignableVinylDNSRequest(request)
    val credentials = new BasicAWSCredentials(user.accessKey, crypto.decrypt(user.secretKey.value))
    signer.sign(signableRequest, credentials)
    logger.info(s"Request to send: [${signableRequest.getResourcePath}]")

    // We stringify JSON before it gets here, so we need to specify the content type through this
    // ugly implicit. If the body came as a JsonValue, we'd get this for free. Also, WSClient does
    // some weird stuff inside such as preserve content type when specifically overriding the headers.
    implicit val stringToJsonBody: BodyWritable[String] = {
      BodyWritable(str => InMemoryBody(ByteString.fromString(str)), signableRequest.contentType)
    }

    wsClient
      .url(signableRequest.getEndpoint.toString + "/" + signableRequest.getResourcePath)
      .withBody(
        signableRequest.getOriginalRequestObject
          .asInstanceOf[VinylDNSRequest]
          .payload
          .getOrElse("")
      )
      .withHttpHeaders(
        signableRequest.getHeaders.asScala.toSeq ++
          Seq(RequestTracing.extractTraceHeader(userRequest.headers.toSimpleMap)): _*
      )
      .withMethod(signableRequest.getHttpMethod.name())
      .withQueryStringParameters(extractParameters(signableRequest.getParameters): _*)
      .execute()
  }

  def getMemberList(groupId: String): Action[AnyContent] = userAction.async { implicit request =>
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)

    val vinyldnsRequest = new VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      s"groups/$groupId/members",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
  }

  def getBatchChange(batchChangeId: String): Action[AnyContent] = userAction.async {
    implicit request =>
      // $COVERAGE-OFF$
      val vinyldnsRequest =
        new VinylDNSRequest(
          "GET",
          s"$vinyldnsServiceBackend",
          s"zones/batchrecordchanges/$batchChangeId"
        )
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    // $COVERAGE-ON$
  }

  def newBatchChange(): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val json = request.body.asJson
    val payload = json.map(Json.stringify)
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (name, values) <- request.queryString
    } queryParameters.put(name, values.asJava)
    val vinyldnsRequest =
      new VinylDNSRequest(
        "POST",
        s"$vinyldnsServiceBackend",
        "zones/batchrecordchanges",
        payload,
        parameters = queryParameters
      )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(response.body)
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def listBatchChanges(): Action[AnyContent] = userAction.async { implicit request =>
    // $COVERAGE-OFF$
    val queryParameters = new HashMap[String, java.util.List[String]]()
    for {
      (startFrom, maxItems) <- request.queryString
    } queryParameters.put(startFrom, maxItems.asJava)
    val vinyldnsRequest = new VinylDNSRequest(
      "GET",
      s"$vinyldnsServiceBackend",
      "zones/batchrecordchanges",
      parameters = queryParameters
    )
    executeRequest(vinyldnsRequest, request.user).map(response => {
      logger.info(response.body)
      Status(response.status)(response.body)
        .withHeaders(cacheHeaders: _*)
    })
    // $COVERAGE-ON$
  }

  def cancelBatchChange(batchChangeId: String): Action[AnyContent] = userAction.async {
    implicit request =>
      // $COVERAGE-OFF$
      val vinyldnsRequest =
        new VinylDNSRequest(
          "POST",
          s"$vinyldnsServiceBackend",
          s"zones/batchrecordchanges/$batchChangeId/cancel"
        )
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    // $COVERAGE-ON$
  }

  def approveBatchChange(batchChangeId: String): Action[AnyContent] = userAction.async {
    implicit request =>
      // $COVERAGE-OFF$
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest(
          "POST",
          s"$vinyldnsServiceBackend",
          s"zones/batchrecordchanges/$batchChangeId/approve",
          payload
        )
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    // $COVERAGE-ON$
  }

  def rejectBatchChange(batchChangeId: String): Action[AnyContent] =
    userAction.async { implicit request =>
      // $COVERAGE-OFF$
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest(
          "POST",
          s"$vinyldnsServiceBackend",
          s"zones/batchrecordchanges/$batchChangeId/reject",
          payload
        )
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
      // $COVERAGE-ON$
    }

  def lockUser(userId: String): Action[AnyContent] = userAction.async { implicit request =>
    if (request.user.isSuper) {
      val vinyldnsRequest =
        new VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"users/$userId/lock")
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    } else {
      Future.successful(
        Forbidden("Request restricted to super users only.").withHeaders(cacheHeaders: _*)
      )
    }
  }

  def unlockUser(userId: String): Action[AnyContent] = userAction.async { implicit request =>
    if (request.user.isSuper) {
      val vinyldnsRequest =
        new VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"users/$userId/unlock")
      executeRequest(vinyldnsRequest, request.user).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    } else {
      Future.successful(
        Forbidden("Request restricted to super users only.").withHeaders(cacheHeaders: _*)
      )
    }
  }
}