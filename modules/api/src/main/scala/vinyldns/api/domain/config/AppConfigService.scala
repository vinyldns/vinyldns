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

package vinyldns.api.domain.config

import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import vinyldns.api.Interfaces._
import vinyldns.api.config.RuntimeVinylDNSConfig
import vinyldns.api.domain.zone.{ConfigAlreadyExists, ConfigNotFound, InvalidRequest, NotAuthorizedError}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.config._
import vinyldns.mysql.TransactionProvider

object AppConfigService {
  def apply(
             appConfigRepo: AppConfigRepository
           ): AppConfigService =
    new AppConfigService(appConfigRepo)
}

class AppConfigService(
                        appConfigRepo: AppConfigRepository
                      ) extends AppConfigServiceAlgebra
  with TransactionProvider {

  private def requireSuper(auth: AuthPrincipal): Result[Unit] =
    ensuring(NotAuthorizedError("Not authorized. Super user access required."))(auth.isSuper).toResult

  // Create single config
  def createAppConfig(
                       key: String,
                       value: String,
                       auth: AuthPrincipal
                     ): Result[AppConfigResponse] =
    for {
      _ <- requireSuper(auth)
      _ <- validateKeyValue(key, value)

      existing <- appConfigRepo
        .getByKey(key)
        .toResult[Option[AppConfigResponse]]

      _ <- (existing match {
        case Some(_) =>
          ConfigAlreadyExists(s"Config with key [$key] already exists").asLeft[Unit]
        case None =>
          ().asRight[Throwable]
      }).toResult

      result <- appConfigRepo
        .create(key, value)
        .toResult[AppConfigResponse]

      _ <- RuntimeVinylDNSConfig.refresh(appConfigRepo).toResult[Unit]

    } yield result

  // Get by key
  def getAppConfig(
                    key: String,
                    auth: AuthPrincipal
                  ): Result[AppConfigResponse] =
    for {
      _ <- requireSuper(auth)
      result <- appConfigRepo
        .getByKey(key)
        .toResult[Option[AppConfigResponse]]

      config <- (result match {
        case Some(value) => value.asRight[Throwable]
        case None =>
          ConfigNotFound(s"Config with key [$key] not found").asLeft[AppConfigResponse]
      }).toResult

    } yield config

  // Get all
  def getAllAppConfigs(
                        auth: AuthPrincipal
                      ): Result[AppConfigListResponse] =
    for {
      _ <- requireSuper(auth)
      result <- appConfigRepo.getAll.map(configs => AppConfigListResponse(configs.size, configs)).toResult[AppConfigListResponse]
    } yield result

  // Update
  def updateAppConfig(
                       key: String,
                       value: String,
                       auth: AuthPrincipal
                     ): Result[AppConfigResponse] =
    for {
      _ <- requireSuper(auth)
      _ <- validateKeyValue(key, value)

      updated <- appConfigRepo
        .update(key, value)
        .toResult[Option[AppConfigResponse]]

      result <- updated
        .toRight(ConfigNotFound(s"Config with key [$key] not found"))
        .toResult

      _ <- RuntimeVinylDNSConfig.refresh(appConfigRepo).toResult[Unit]

    } yield result

  // Delete
  def deleteAppConfig(
                       key: String,
                       auth: AuthPrincipal
                     ): Result[Boolean] =
    for {
      _ <- requireSuper(auth)
      deleted <- appConfigRepo
        .delete(key)
        .toResult[Boolean]

      result <- Either
        .cond(
          deleted,
          true,
          ConfigNotFound(s"Config with key [$key] not found")
        )
        .toResult

      _ <- RuntimeVinylDNSConfig.refresh(appConfigRepo).toResult[Unit]

    } yield result

  // Get the current in-memory effective snapshot
  def getEffectiveConfig(
                          auth: AuthPrincipal
                        ): Result[Map[String, String]] =
    for {
      _ <- requireSuper(auth)
      result <- RuntimeVinylDNSConfig.getAll.toResult[Map[String, String]]
    } yield result

  // Reload config from file + apply DB overrides
  def reloadConfig(auth: AuthPrincipal): Result[String] =
    for {
      _ <- requireSuper(auth)
      _ <- RuntimeVinylDNSConfig.reload().toResult[Unit]
    } yield "Config reloaded successfully"

  // ---------------------------
  // Validations
  // ---------------------------

  private def validateKeyValue(
                                key: String,
                                value: String
                              ): Result[Unit] =
    if (key.trim.isEmpty)
      Left(InvalidRequest("Key cannot be empty")).toResult
    else if (value.trim.isEmpty)
      Left(InvalidRequest("Value cannot be empty")).toResult
    else IO.unit.toResult
}