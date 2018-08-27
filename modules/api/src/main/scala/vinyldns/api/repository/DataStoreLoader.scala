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

package vinyldns.api.repository

import cats.data._
import cats.effect.IO
import cats.implicits._
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.api.repository.RepositoryName._

import scala.reflect.ClassTag

object DataStoreLoader {
  def loadAll(configs: List[DataStoreConfig]): IO[DataAccessor] =
    for {
      activeConfigs <- IO.fromEither(getValidatedConfigs(configs))
      dataStores <- activeConfigs.map(load).parSequence
      accessor <- IO.fromEither(generateAccessor(activeConfigs, dataStores.toMap))
    } yield accessor

  def load(config: DataStoreConfig): IO[(String, DataStore)] =
    for {
      className <- IO.pure(config.className)
      provider <- IO(Class.forName(className).newInstance.asInstanceOf[DataStoreProvider])
      dataStore <- provider.load(config)
      _ <- IO.fromEither(validateLoadResponse(config, dataStore))
    } yield (className, dataStore)

  // Ensures that if a repository is configured on, load returned it, and if configured off, load did not
  def validateLoadResponse(
      config: DataStoreConfig,
      dataStore: DataStore): Either[DataStoreStartupError, Unit] = {
    val dataStoreMap = dataStore.keys
    val configMap = config.repositories.keys

    val loadedNotConfigured = dataStoreMap.diff(configMap)
    val configuredNotLoaded = configMap.diff(dataStoreMap)

    (loadedNotConfigured.isEmpty, configuredNotLoaded.isEmpty) match {
      case (true, true) => Right((): Unit)
      case (false, true) =>
        Left(
          DataStoreStartupError(
            s"""Loaded repos were configured off for ${config.className}: ${loadedNotConfigured
              .mkString(", ")}"""))
      case (true, false) =>
        Left(
          DataStoreStartupError(
            s"""Configured repos were not loaded by ${config.className}: ${configuredNotLoaded
              .mkString(", ")}"""))
      case _ =>
        Left(
          DataStoreStartupError(
            s"""Error on load by ${config.className}: configuration does not match load for repos:
           | ${(loadedNotConfigured ++ configuredNotLoaded).mkString(", ")}""".stripMargin))
    }
  }

  /*
   * Validates that there's exactly one repo defined across all datastore configs. Returns only
   * DataStoreConfigs with at least one defined repo if valid
   */
  def getValidatedConfigs(
      configs: List[DataStoreConfig]): Either[DataStoreStartupError, List[DataStoreConfig]] = {

    val repoConfigs = configs.map(_.repositories)

    def existsOnce(repoName: RepositoryName): ValidatedNel[String, Unit] = {
      val definedRepos = repoConfigs.flatMap(_.get(repoName))
      definedRepos match {
        case _ :: Nil => ().validNel[String]
        case Nil => s"Must have one repo of type $repoName".invalidNel[Unit]
        case _ =>
          s"May not have more than one repo of type $repoName"
            .invalidNel[Unit]
      }
    }

    val combinedValidations = RepositoryName.values.map(existsOnce).reduce(_ |+| _)

    combinedValidations.toEither
      .map(_ => configs.filter(_.repositories.nonEmpty))
      .leftMap { errors =>
        val errorString = errors.toList.mkString(", ")
        DataStoreStartupError(s"Config validation error: $errorString")
      }
  }

  def generateAccessor(
      configs: List[DataStoreConfig],
      dataStores: Map[String, DataStore]): Either[DataStoreStartupError, DataAccessor] = {

    def getRepoOf[A <: Repository: ClassTag](repoName: RepositoryName): ValidatedNel[String, A] = {
      val repository = for {
        matchingDbConfig <- configs.find(_.repositories.hasKey(repoName))
        matchingDbName = matchingDbConfig.className
        matchingDataStore <- dataStores.get(matchingDbName)
        repo <- matchingDataStore.get[A](repoName)
      } yield repo

      repository match {
        case Some(repo) => repo.validNel
        case None => s"Could not pull repo $repoName from configured database".invalidNel
      }
    }

    val accessor: ValidatedNel[String, DataAccessor] =
      (
        getRepoOf[UserRepository](user),
        getRepoOf[GroupRepository](group),
        getRepoOf[MembershipRepository](membership),
        getRepoOf[GroupChangeRepository](groupChange),
        getRepoOf[RecordSetRepository](recordSet),
        getRepoOf[RecordChangeRepository](recordChange),
        getRepoOf[ZoneChangeRepository](zoneChange),
        getRepoOf[ZoneRepository](zone),
        getRepoOf[BatchChangeRepository](batchChange))
        .mapN(DataAccessor)

    accessor.toEither.leftMap(errors => DataStoreStartupError(errors.toList.mkString(", ")))
  }
}
