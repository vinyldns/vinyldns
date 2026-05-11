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

import java.io.{BufferedReader, InputStreamReader, PrintWriter, StringWriter}
import java.net.{HttpURLConnection, URL}

import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.slf4j.LoggerFactory
import play.api.libs.json._
import vinyldns.core.domain.membership.User
import vinyldns.core.health.HealthCheck
import vinyldns.core.health.HealthCheck._

trait UserSyncProvider {
  def getStaleUsers(users: List[User]): IO[List[User]]
}

class LdapUserSyncProvider(authenticator: Authenticator) extends UserSyncProvider {
  private val logger = LoggerFactory.getLogger(classOf[LdapUserSyncProvider])

  def getStaleUsers(users: List[User]): IO[List[User]] =
    for {
      _ <- IO(logger.info(s"Checking ${users.size} users against LDAP"))
      staleUsers <- authenticator.getUsersNotInLdap(users)
      _ <- IO(logger.info(s"LDAP sync complete; ${staleUsers.size} users not found in directory"))
    } yield staleUsers
}

object NoOpUserSyncProvider extends UserSyncProvider {
  def getStaleUsers(users: List[User]): IO[List[User]] = IO.pure(List.empty)
}

class GraphApiUserSyncProvider(
    tenantId: String,
    clientId: String,
    clientSecret: String,
    usernameAttribute: String
) extends UserSyncProvider {

  private val logger = LoggerFactory.getLogger(classOf[GraphApiUserSyncProvider])
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  @volatile private var cachedToken: Option[String] = None
  @volatile private var tokenExpiresAt: Long = 0L
  private val tokenLock = new Object

  // $COVERAGE-OFF$
  private[controllers] def getAccessToken(): IO[String] =
    IO {
      tokenLock.synchronized {
        val now = System.currentTimeMillis()
        cachedToken match {
          case Some(token) if now < tokenExpiresAt - 300000 => // 5 min buffer
            token
          case _ =>
            fetchAccessToken()
        }
      }
    }

  private def fetchAccessToken(): String = {
    val tokenUrl = s"https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token"
    val conn = new URL(tokenUrl).openConnection().asInstanceOf[HttpURLConnection]
    try {
      conn.setRequestMethod("POST")
      conn.setDoOutput(true)
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

      val body =
        s"client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
          s"&client_secret=${java.net.URLEncoder.encode(clientSecret, "UTF-8")}" +
          s"&scope=${java.net.URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8")}" +
          s"&grant_type=client_credentials"

      val os = conn.getOutputStream
      os.write(body.getBytes("UTF-8"))
      os.close()

      val responseCode = conn.getResponseCode
      if (responseCode != 200) {
        val errorBody = try {
          val es = conn.getErrorStream
          if (es != null) {
            val reader = new BufferedReader(new InputStreamReader(es))
            val sb = new StringBuilder
            var line: String = null
            while ({ line = reader.readLine(); line != null }) { sb.append(line) }
            reader.close()
            sb.toString()
          } else ""
        } catch { case _: Exception => "" }
        throw new RuntimeException(
          s"Token request failed with status $responseCode: $errorBody"
        )
      }

      val reader = new BufferedReader(new InputStreamReader(conn.getInputStream))
      val response = new StringBuilder
      var line: String = null
      while ({ line = reader.readLine(); line != null }) {
        response.append(line)
      }
      reader.close()

      val json = Json.parse(response.toString())
      val accessToken = (json \ "access_token").as[String]
      val expiresIn = (json \ "expires_in").as[Long]

      cachedToken = Some(accessToken)
      tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
      logger.info(s"Successfully acquired Graph API access token, expires in ${expiresIn}s")

      accessToken
    } finally {
      conn.disconnect()
    }
  }
  // $COVERAGE-ON$

  // Limit concurrent Graph API requests to avoid rate limiting (Microsoft throttles at ~2000 req/10min)
  private val maxConcurrency = 10

  def getStaleUsers(users: List[User]): IO[List[User]] =
    for {
      _ <- IO(logger.info(s"Checking ${users.size} users against Graph API"))
      token <- getAccessToken()
      results <- users.grouped(maxConcurrency).toList.foldLeft(IO.pure(List.empty[Option[User]])) {
        (acc, batch) =>
          for {
            prev <- acc
            batchResults <- batch.map(u => checkUser(token, u)).parSequence
          } yield prev ++ batchResults
      }
      staleUsers = results.flatten
      _ <- IO(logger.info(s"Graph API sync complete; ${staleUsers.size} of ${users.size} users marked as stale"))
    } yield staleUsers

  private[controllers] def parseUserResponse(responseBody: String, user: User): Option[User] = {
    val json = Json.parse(responseBody)
    val values = (json \ "value").as[JsArray].value

    if (values.isEmpty) {
      logger.info(s"User ${user.userName} not found in Graph API, marking as stale")
      Some(user)
    } else {
      val accountEnabled = (values.head \ "accountEnabled").asOpt[Boolean].getOrElse(true)
      if (!accountEnabled) {
        logger.info(s"User ${user.userName} is disabled in Graph API, marking as stale")
        Some(user)
      } else {
        None
      }
    }
  }

  private[controllers] def escapeODataValue(value: String): String =
    value.replace("'", "''")

  // $COVERAGE-OFF$
  private[controllers] def checkUser(token: String, user: User): IO[Option[User]] =
    IO {
      try {
        val escapedUsername = escapeODataValue(user.userName)
        val filter = java.net.URLEncoder.encode(
          s"$usernameAttribute eq '$escapedUsername'",
          "UTF-8"
        )
        val select = java.net.URLEncoder.encode("accountEnabled", "UTF-8")
        // $count=true + ConsistencyLevel: eventual are required for advanced query
        // properties like onPremisesSamAccountName, employeeId, etc.
        // See https://learn.microsoft.com/en-us/graph/aad-advanced-queries
        val url =
          s"https://graph.microsoft.com/v1.0/users?$$filter=$filter&$$select=$select&$$count=true"

        val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
        try {
          conn.setRequestMethod("GET")
          conn.setRequestProperty("Authorization", s"Bearer $token")
          conn.setRequestProperty("ConsistencyLevel", "eventual")

          val responseCode = conn.getResponseCode
          if (responseCode == 404) {
            logger.info(s"User ${user.userName} not found in Graph API (404), marking as stale")
            Some(user)
          } else if (responseCode != 200) {
            val errorBody = try {
              val es = conn.getErrorStream
              if (es != null) {
                val reader = new BufferedReader(new InputStreamReader(es))
                val sb = new StringBuilder
                var line: String = null
                while ({ line = reader.readLine(); line != null }) { sb.append(line) }
                reader.close()
                sb.toString()
              } else ""
            } catch { case _: Exception => "" }
            logger.error(
              s"Graph API error for user ${user.userName}: HTTP $responseCode, body=$errorBody; treating as active (safe default)"
            )
            None
          } else {
            val reader = new BufferedReader(new InputStreamReader(conn.getInputStream))
            val response = new StringBuilder
            var line: String = null
            while ({ line = reader.readLine(); line != null }) {
              response.append(line)
            }
            reader.close()

            parseUserResponse(response.toString(), user)
          }
        } finally {
          conn.disconnect()
        }
      } catch {
        case e: Exception =>
          val errorMessage = new StringWriter
          e.printStackTrace(new PrintWriter(errorMessage))
          logger.error(
            s"Error checking user ${user.userName} in Graph API: ${errorMessage.toString.replaceAll("\n", ";").replaceAll("\t", " ")}; treating as active (safe default)"
          )
          None
      }
    }
  // $COVERAGE-ON$
}

class NoOpAuthenticator extends Authenticator {
  def authenticate(username: String, password: String): Either[LdapException, LdapUserDetails] =
    Left(LdapServiceException("LDAP not configured"))

  def lookup(username: String): Either[LdapException, LdapUserDetails] =
    Left(LdapServiceException("LDAP not configured"))

  def getUsersNotInLdap(users: List[User]): IO[List[User]] =
    IO.pure(Nil)

  def healthCheck(): HealthCheck =
    IO(().asRight).asHealthCheck(classOf[NoOpAuthenticator])
}
