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
import models.{SignableVinylDNSRequest, UserAccount, VinylDNSRequest}
import org.joda.time.DateTime
import play.api.{Logger, _}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import java.util.HashMap
import javax.inject.{Inject, Singleton}

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
    def info(msg: String): Flash = Flash(Map(TYPE -> "info", MSG -> msg))
    def success(msg: String): Flash = Flash(Map(TYPE -> "success", MSG -> msg))

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
      isLocked: Boolean,
      id: String)
  object UserInfo {
    def fromAccount(account: UserAccount): UserInfo =
      UserInfo(
        userName = account.username,
        firstName = account.firstName,
        lastName = account.lastName,
        email = account.email,
        isSuper = account.isSuper,
        isLocked = account.isLocked,
        id = account.userId
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
    auditLogAccessor: ChangeLogStore,
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

  implicit val userInfoReads: Reads[VinylDNS.UserInfo] = Json.reads[VinylDNS.UserInfo]
  implicit val userInfoWrites: Writes[VinylDNS.UserInfo] = Json.writes[VinylDNS.UserInfo]

  private def checkSuperUser(username: String): Try[UserAccount] =
    for {
      account <- userAccountAccessor.get(username).flatMap {
        case Some(account) if account.isSuper => Success(account)
        case Some(account) if !account.isSuper =>
          Failure(new UserIsNotAnAdminException(s"User account for $username is not an admin"))
        case None =>
          Failure(new UserDoesNotExistException(s"User account for $username not found"))
      }
    } yield account

  private def withUnlockedUser(username: String): Try[UserAccount] =
    for {
      account <- userAccountAccessor.get(username).flatMap {
        case Some(account) if !account.isLocked => Success(account)
        case Some(account) if account.isLocked =>
          Failure(new UserAccountIsLockedException(s"User account for $username is locked"))
        case None => Failure(new UserDoesNotExistException(s"User account for $username not found"))
      }
    } yield account

  private def errorResponse(e: Throwable): Result =
    e match {
      case e: UserAccountIsLockedException => Forbidden(e.getMessage).withHeaders(cacheHeaders: _*)
      case e: UserIsNotAnAdminException => Unauthorized(e.getMessage).withHeaders(cacheHeaders: _*)
      case _ => NotFound("User was not found").withHeaders(cacheHeaders: _*)
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

  def newGroup(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest = VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"groups/$id")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Logger.info(s"group [$id] retrieved with status [${response.status}]")
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def deleteGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest = VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"groups/$id")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Logger.info(s"group [$id] deleted with status [${response.status}]")
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def updateGroup(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getMyGroups(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser({ username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val queryParameters = new HashMap[String, java.util.List[String]]()
          for {
            (name, values) <- request.queryString
          } queryParameters.put(name, values.asJava)
          val vinyldnsRequest =
            VinylDNSRequest(
              "GET",
              s"$vinyldnsServiceBackend",
              s"groups",
              parameters = queryParameters)
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    })
  }

  def getAuthenticatedUserData(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { user =>
      withUnlockedUser(user) match {
        case Success(_) => {
          val response = userAccountAccessor.get(user).map {
            case Some(userDetails) =>
              Ok(Json.toJson(VinylDNS.UserInfo.fromAccount(userDetails)))
                .withHeaders(cacheHeaders: _*)
            case _ =>
              Status(404)(s"Did not find user data for '$user'")
          }
          Future.fromTry(response)
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  private def processCsv(username: String, account: UserAccount): Result =
    account.username match {
      case accountUsername: String if accountUsername == username =>
        Logger.info(
          s"Sending credentials for user=$username with key accessKey=${account.accessKey}")
        Ok(
          s"NT ID, access key, secret key,api url\n%s,%s,%s,%s"
            .format(
              account.username,
              account.accessKey,
              account.accessSecret,
              vinyldnsServiceBackend))
          .as("text/csv")

      case _ =>
        Redirect("/login").withNewSession
          .flashing(VinylDNS.Alerts.error("Mismatched credentials - Please log in again"))
    }

  def serveCredsFile(fileName: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info(s"Serving credentials for file $fileName")
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(account) => Future(processCsv(username, account))
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def regenerateCreds(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          Future
            .fromTry(processRegenerate(username))
            .map(response => {
              Status(200)("Successfully regenerated credentials")
                .withHeaders(cacheHeaders: _*)
                .withSession("username" -> response.username, "accessKey" -> response.accessKey)
            })
            .recover {
              case _: UserDoesNotExistException =>
                NotFound(s"User $username was not found").withHeaders(cacheHeaders: _*)
            }
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  private def processRegenerate(oldAccountName: String): Try[UserAccount] =
    for {
      oldAccount <- userAccountAccessor.get(oldAccountName).flatMap {
        case Some(account) => Success(account)
        case None =>
          Failure(
            new UserDoesNotExistException(s"Error - User account for $oldAccountName not found"))
      }
      account = oldAccount.regenerateCredentials()
      _ <- userAccountAccessor.put(account)
      _ <- auditLogAccessor.log(
        UserChangeMessage(
          account.userId,
          account.username,
          DateTime.now(),
          ChangeType("updated"),
          account,
          Some(oldAccount)))
    } yield {
      Logger.info(s"Credentials successfully regenerated for ${account.username}")
      account
    }

  private def createNewUser(details: UserDetails): Try[UserAccount] = {
    val newAccount =
      UserAccount(details.username, details.firstName, details.lastName, details.email)
    for {
      newUser <- userAccountAccessor.put(newAccount)
    } yield {
      auditLogAccessor.log(
        UserChangeMessage(
          newUser.userId,
          newUser.username,
          DateTime.now(),
          ChangeType("created"),
          newUser,
          None))
      newUser
    }
  }

  def getUserDataByUsername(username: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { user =>
        withUnlockedUser(user) match {
          case Success(_) => {
            Future
              .fromTry {
                for {
                  userDetails <- authenticator.lookup(username)
                  existingAccount <- userAccountAccessor.get(userDetails.username)
                  userAccount <- existingAccount match {
                    case Some(user) => Try(VinylDNS.UserInfo.fromAccount(user))
                    case None =>
                      createNewUser(userDetails).map(VinylDNS.UserInfo.fromAccount)
                  }
                } yield userAccount
              }
              .map(Json.toJson(_))
              .map(Ok(_).withHeaders(cacheHeaders: _*))
              .recover {
                case _: UserDoesNotExistException => NotFound(s"User $username was not found")
              }
          }
          case Failure(e) => Future(errorResponse(e))
        }
      }
  }

  def updateUser(userId: String, lockedStatus: Boolean): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { username =>
        (withUnlockedUser(username), checkSuperUser(username)) match {
          case (Success(_), Success(_)) =>
            Future
              .fromTry(updateLockStatus(userId, lockedStatus))
              .map(_ => {
                Status(200)(s"Successfully changed locked status to ${lockedStatus}")
                  .withHeaders(cacheHeaders: _*)
              })
          case (Failure(e), Success(_)) => Future(errorResponse(e))
          case (Success(_), Failure(e)) => Future(errorResponse(e))
          case (Failure(e), Failure(_)) => Future(errorResponse(e))
        }
      }
  }

  private def updateLockStatus(userId: String, lockedStatus: Boolean): Try[UserAccount] =
    for {
      oldAccount <- userAccountAccessor.get(userId).flatMap {
        case Some(account) => Success(account)
        case None =>
          Failure(new UserDoesNotExistException(s"Error - User account for $userId not found"))
      }
      account = oldAccount.lockUser(lockedStatus)
      _ <- userAccountAccessor.put(account)
      _ <- auditLogAccessor.log(
        UserChangeMessage(
          account.userId,
          account.username,
          DateTime.now(),
          ChangeType("updated"),
          account,
          Some(oldAccount)))
    } yield {
      Logger.info(s"Locked status for ${account.username} set to ${lockedStatus}")
      account
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

        // get or create the new style user account
        val userAccount = userAccountAccessor
          .get(userDetails.username)
          .flatMap {
            case None =>
              Logger.info(s"Creating user account for ${userDetails.username}")
              createNewUser(userDetails).map {
                case user: UserAccount =>
                  Logger.info(s"User account for ${user.username} created with id ${user.userId}")
                  user
              }
            case Some(user) =>
              Logger.info(s"User account for ${user.username} exists with id ${user.userId}")
              withUnlockedUser(username)
          }
          .recoverWith {
            case ex =>
              Logger.error(
                s"User retrieval or creation failed for user ${userDetails.username} with message ${ex.getMessage}")
              throw ex
          }
          .get

        Logger.info(
          s"--NEW MEMBERSHIP-- user [${userAccount.username}] logged in with id [${userAccount.userId}]")
        Redirect("/index")
          .withSession("username" -> userAccount.username, "accessKey" -> userAccount.accessKey)
    }

  def getZones: Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest = new VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$id")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def syncZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest =
            new VinylDNSRequest("POST", s"$vinyldnsServiceBackend", s"zones/$id/sync")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getRecordSets(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def listRecordSetChanges(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getChanges(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest =
            VinylDNSRequest("GET", s"$vinyldnsServiceBackend", s"zones/$id/history")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def addZone(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def updateZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { user =>
      withUnlockedUser(user) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def addRecordSet(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val json = request.body.asJson
          val payload = json.map(Json.stringify)
          val vinyldnsRequest =
            new VinylDNSRequest(
              "POST",
              s"$vinyldnsServiceBackend",
              s"zones/$id/recordsets",
              payload)
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def deleteZone(id: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val vinyldnsRequest =
            new VinylDNSRequest("DELETE", s"$vinyldnsServiceBackend", s"zones/$id")
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def updateRecordSet(zid: String, rid: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { username =>
        withUnlockedUser(username) match {
          case Success(_) => {
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
          case Failure(e) => Future(errorResponse(e))
        }
      }
  }

  def deleteRecordSet(zid: String, rid: String): Action[AnyContent] = Action.async {
    implicit request =>
      withAuthenticatedUser { username =>
        withUnlockedUser(username) match {
          case Success(_) => {
            val vinyldnsRequest =
              new VinylDNSRequest(
                "DELETE",
                s"$vinyldnsServiceBackend",
                s"zones/$zid/recordsets/$rid")
            val signedRequest =
              signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
            executeRequest(signedRequest).map(response => {
              Status(response.status)(response.body)
                .withHeaders(cacheHeaders: _*)
            })
          }
          case Failure(e) => Future(errorResponse(e))
        }
      }
  }

  private def signRequest(
      vinyldnsRequest: VinylDNSRequest,
      credentials: AWSCredentials): SignableVinylDNSRequest = {
    val signableRequest = new SignableVinylDNSRequest(vinyldnsRequest)
    signer.sign(signableRequest, credentials)
    signableRequest
  }

  def getUserCreds(keyOption: Option[String]): BasicAWSCredentials =
    keyOption match {
      case Some(key) =>
        userAccountAccessor.getUserByKey(key) match {
          case Success(Some(account)) =>
            new BasicAWSCredentials(account.accessKey, account.accessSecret)
          case Success(None) =>
            throw new IllegalArgumentException(
              s"Key [$key] Not Found!! Please logout then back in.")
          case Failure(ex) => throw ex
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
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def getBatchChange(batchChangeId: String): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def newBatchChange(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
          val json = request.body.asJson
          val payload = json.map(Json.stringify)
          val vinyldnsRequest =
            new VinylDNSRequest(
              "POST",
              s"$vinyldnsServiceBackend",
              "zones/batchrecordchanges",
              payload)
          val signedRequest =
            signRequest(vinyldnsRequest, getUserCreds(request.session.get("accessKey")))
          executeRequest(signedRequest).map(response => {
            Logger.info(response.body)
            Status(response.status)(response.body)
              .withHeaders(cacheHeaders: _*)
          })
        }
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }

  def listBatchChanges(): Action[AnyContent] = Action.async { implicit request =>
    withAuthenticatedUser { username =>
      withUnlockedUser(username) match {
        case Success(_) => {
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
        case Failure(e) => Future(errorResponse(e))
      }
    }
  }
}
