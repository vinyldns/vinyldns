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

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials, SignerFactory}
import models.{SignableVinylDNSRequest, VinylDNSRequest}
import org.joda.time.DateTime
import play.api.{Logger, _}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import java.util.HashMap

import cats.effect.IO
import javax.inject.{Inject, Singleton}
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.{LockStatus, User}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object VinylDNS {

  import play.api.mvc._

  object Alerts {
    private val TYPE = "alertType"
    private val MSG = "alertMessage"
    def error(msg: String): Flash = Flash(Map(TYPE -> "danger", MSG -> msg))
    def warning(msg: String): Flash = Flash(Map(TYPE -> "warning", MSG -> msg))

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
      id: String)
  object UserInfo {
    def fromUser(user: User): UserInfo =
      UserInfo(
        userName = user.userName,
        firstName = user.firstName,
        lastName = user.lastName,
        email = user.email,
        isSuper = user.isSuper,
        id = user.id
      )
  }

  private[controllers] def withAuthenticatedUser(block: String => Future[Result])(
      implicit request: Request[AnyContent]): Future[Result] = {
    import Results.Redirect

    request.session.get("username") match {
      case Some(username) => block(username)
      case None =>
        Future(
          Redirect("/login").flashing(
            Alerts.warning("You are not logged in. Please login to continue.")))
    }
  }
}

@Singleton
class VinylDNS @Inject()(
    configuration: Configuration,
    authenticator: Authenticator,
    userAccountAccessor: UserAccountAccessor,
    wsClient: WSClient,
    components: ControllerComponents)
    extends AbstractController(components) {

  import VinylDNS._
  import play.api.mvc._

  private val signer = SignerFactory.getSigner("VinylDNS", "us/east")
  private val vinyldnsServiceBackend =
    configuration
      .getOptional[String]("portal.vinyldns.backend.url")
      .getOrElse("http://localhost:9000")
  private val cacheHeaders = Seq(
    ("Cache-Control", "no-cache, no-store, must-revalidate"),
    ("Pragma", "no-cache"),
    ("Expires", "0"))

  implicit val lockStatusFormat: Format[LockStatus] = new Format[LockStatus] {
    def reads(json: JsValue): JsResult[LockStatus] = json match {
      case JsString(v) => JsSuccess(LockStatus.withName(v))
      case _ => JsError("LockStatus value was not a string")
    }
    def writes(o: LockStatus): JsValue = JsString(o.toString)
  }
  implicit val userInfoReads: Reads[VinylDNS.UserInfo] = Json.reads[VinylDNS.UserInfo]
  implicit val userInfoWrites: Writes[VinylDNS.UserInfo] = Json.writes[VinylDNS.UserInfo]

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

  def newGroup(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", "groups", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(response.body)
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups/$id")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(s"group [$id] retrieved with status [${response.status}]")
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def deleteGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest = VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"groups/$id")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(s"group [$id] deleted with status [${response.status}]")
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def updateGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val payload = request.body.asJson.map(Json.stringify)
      val vinyldnsRequest =
        VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"groups/$id", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(s"group [$id] updated with status [${response.status}]")
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getMyGroups(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser({ _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (name, values) <- request.queryString
      } queryParameters.put(name, values.asJava)

      val vinyldnsRequest =
        VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups", parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    })
  }

  def getAuthenticatedUserData(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { user =>
      val response = userAccountAccessor.get(user).map {
        case Some(userDetails) =>
          Ok(Json.toJson(VinylDNS.UserInfo.fromUser(userDetails)))
            .withHeaders(cacheHeaders: _*)
        case _ =>
          Status(404)(s"Did not find user data for '$user'")
      }
      response.unsafeToFuture()
    }
  }

  private def processCsv(username: String, user: User): Result =
    user.userName match {
      case accountUsername: String if accountUsername == username =>
        Logger.info(s"Sending credentials for user=$username with key accessKey=${user.accessKey}")
        Ok(
          s"NT ID, access key, secret key,api url\n%s,%s,%s,%s"
            .format(user.userName, user.accessKey, user.secretKey, vinyldnsServiceBackend))
          .as("text/csv")

      case _ =>
        Redirect("/login").withNewSession
          .flashing(VinylDNS.Alerts.error("Mismatched credentials - Please log in again"))
    }

  def serveCredsFile(fileName: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info(s"Serving credentials for file $fileName")
    withAuthenticatedUser { username =>
      userAccountAccessor
        .get(username)
        .flatMap {
          case Some(account) => IO(processCsv(username, account))
          case None =>
            IO.raiseError(
              new UnsupportedOperationException(s"Error - User account for $username not found"))
        }
        .unsafeToFuture()
    }
  }

  def regenerateCreds(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      Future
        .fromTry(processRegenerate(username))
        .map(response => {
          Status(200)("Successfully regenerated credentials")
            .withHeaders(cacheHeaders: _*)
            .withSession("username" -> response.userName, "accessKey" -> response.accessKey)
        })
        .recover {
          case _: UserDoesNotExistException =>
            NotFound(s"User $username was not found").withHeaders(cacheHeaders: _*)
        }
    }
  }

  private def processRegenerate(oldAccountName: String): Try[User] = {
    val update = for {
      oldUser <- userAccountAccessor.get(oldAccountName).flatMap {
        case Some(u) => IO.pure(u)
        case None =>
          IO.raiseError(
            new UserDoesNotExistException(s"Error - User account for $oldAccountName not found"))
      }
      newUser = oldUser.regenerateCredentials()
      _ <- userAccountAccessor.update(newUser, oldUser)
    } yield {
      Logger.info(s"Credentials successfully regenerated for ${newUser.userName}")
      newUser
    }

    update.attempt.unsafeRunSync().toTry
  }

  private def createNewUser(details: UserDetails): IO[User] = {
    val newUser =
      User(
        details.username,
        User.generateKey,
        User.generateKey,
        details.firstName,
        details.lastName,
        details.email)
    userAccountAccessor.create(newUser)
  }

  def getUserDataByUsername(username: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { _ =>
        {
          for {
            userDetails <- IO.fromEither(authenticator.lookup(username).toEither)
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
            case _: UserDoesNotExistException => NotFound(s"User $username was not found")
          }
      }
  }

  def processLogin(username: String, password: String): Result =
    authenticator.authenticate(username, password) match {
      case Failure(error) =>
        Logger.error(s"Authentication failed for [$username]", error)
        Redirect("/login").flashing(
          VinylDNS.Alerts.error("Authentication failed, please try again"))
      case Success(userDetails: UserDetails) =>
        Logger.info(
          s"user [${userDetails.username}] logged in with ldap path [${userDetails.nameInNamespace}]")

        val user = userAccountAccessor
          .get(userDetails.username)
          .flatMap {
            case None =>
              Logger.info(s"Creating user account for ${userDetails.username}")
              createNewUser(userDetails).map { u: User =>
                Logger.info(s"User account for ${u.userName} created with id ${u.id}")
                u
              }
            case Some(u) =>
              Logger.info(s"User account for ${u.userName} exists with id ${u.id}")
              IO.pure(u)
          }
          .unsafeRunSync()

        Logger.info(s"--NEW MEMBERSHIP-- user [${user.userName}] logged in with id [${user.id}]")
        Redirect("/index")
          .withSession("username" -> user.userName, "accessKey" -> user.accessKey)
    }

  def getZones: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (name, values) <- request.queryString
      } queryParameters.put(name, values.asJava)
      val vinyldnsRequest =
        new VinylDNSRequest(
          "GET",
          s"$vinyldnsServiceBackend",
          "zones",
          parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest = new VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$id")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def syncZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest =
        new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", s"zones/$id/sync")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getRecordSets(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (name, values) <- request.queryString
      } queryParameters.put(name, values.asJava)
      val vinyldnsRequest = VinylDNSRequest(
        "GET",
        s"$vinyldnsServiceBackend",
        s"zones/$id/recordsets",
        parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def listRecordSetChanges(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (name, values) <- request.queryString
      } queryParameters.put(name, values.asJava)
      val vinyldnsRequest = new VinylDNSRequest(
        "GET",
        s"$vinyldnsServiceBackend",
        s"zones/$id/recordsetchanges",
        parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getChanges(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$id/history")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def addZone(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", "zones", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def updateZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { user =>
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest("PUT", s"$vinyldnsServiceBackend", s"zones/$id", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def addRecordSet(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", s"zones/$id/recordsets", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def deleteZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest = new VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"zones/$id")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def updateRecordSet(zid: String, rid: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { _ =>
        val json = request.body.asJson
        val payload = json.map(Json.stringify)
        val vinyldnsRequest =
          new VinylDNSRequest(
            "PUT",
            s"$vinyldnsServiceBackend",
            s"zones/$zid/recordsets/$rid",
            payload)
        val signedRequest =
          signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
        executeRequest(signedRequest).map(response => {
          Status(response.status)(response.body)
            .withHeaders(cacheHeaders: _*)
        })
      }
  }

  def deleteRecordSet(zid: String, rid: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { _ =>
        val vinyldnsRequest =
          new VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"zones/$zid/recordsets/$rid")
        val signedRequest =
          signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
        executeRequest(signedRequest).map(response => {
          Status(response.status)(response.body)
            .withHeaders(cacheHeaders: _*)
        })
      }
  }

  def signRequest(
      vinyldnsRequest: VinylDNSRequest,
      credentials: AWSCredentials): SignableVinylDNSRequest = {
    val signableRequest = new SignableVinylDNSRequest(vinyldnsRequest)
    signer.sign(signableRequest, credentials)
    signableRequest
  }

  def getUserCreds(keyOption: Option[String]): BasicAWSCredentials =
    keyOption match {
      case Some(key) =>
        userAccountAccessor.getUserByKey(key).attempt.unsafeRunSync() match {
          case Right(Some(account)) =>
            new BasicAWSCredentials(account.accessKey, account.secretKey)
          case Right(None) =>
            throw new IllegalArgumentException(
              s"Key [$key] Not Found!! Please logout then back in.")
          case Left(ex) => throw ex
        }
      case None => throw new IllegalArgumentException("No Key Found!!")
    }

  private def extractParameters(
      params: util.Map[String, util.List[String]]): Seq[(String, String)] =
    params.asScala.foldLeft(Seq[(String, String)]()) {
      case (acc, (key, values)) =>
        acc ++ values.asScala.map(v => key -> v)
    }

  private def executeRequest(request: SignableVinylDNSRequest) = {
    Logger.info(s"Request to send: [${request.getResourcePath}]")
    wsClient
      .url(request.getEndpoint.toString + "/" + request.getResourcePath)
      .withHttpHeaders("Content-Type" -> request.contentType)
      .withBody(
        request.getOriginalRequestObject.asInstanceOf[VinylDNSRequest].payload.getOrElse(""))
      .withHttpHeaders(request.getHeaders.asScala.toSeq: _*)
      .withMethod(request.getHttpMethod.name())
      .withQueryStringParameters(extractParameters(request.getParameters): _*)
      .execute()
  }

  def getMemberList(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (name, values) <- request.queryString
      } queryParameters.put(name, values.asJava)

      val vinyldnsRequest = new VinylDNSRequest(
        "GET",
        s"$vinyldnsServiceBackend",
        s"groups/$groupId/members",
        parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def getBatchChange(batchChangeId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val vinyldnsRequest =
        new VinylDNSRequest(
          "GET",
          s"$vinyldnsServiceBackend",
          s"zones/batchrecordchanges/$batchChangeId")
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def newBatchChange(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val json = request.body.asJson
      val payload = json.map(Json.stringify)
      val vinyldnsRequest =
        new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", "zones/batchrecordchanges", payload)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(response.body)
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }

  def listBatchChanges(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { _ =>
      val queryParameters = new HashMap[String, java.util.List[String]]()
      for {
        (startFrom, maxItems) <- request.queryString
      } queryParameters.put(startFrom, maxItems.asJava)
      val vinyldnsRequest = new VinylDNSRequest(
        "GET",
        s"$vinyldnsServiceBackend",
        "zones/batchrecordchanges",
        parameters = queryParameters)
      val signedRequest =
        signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
      executeRequest(signedRequest).map(response => {
        Logger.info(response.body)
        Status(response.status)(response.body)
          .withHeaders(cacheHeaders: _*)
      })
    }
  }
}
