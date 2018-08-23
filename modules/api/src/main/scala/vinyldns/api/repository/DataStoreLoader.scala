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

import vinyldns.api.repository.RepositoryName._

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
      _ <- IO.fromEither(validateLoadResponse(config.repositories, dataStore))
    } yield (className, dataStore)

  // Ensures that if a datastore is configured on, load returned it, and if configured off, load did not
  def validateLoadResponse(
      reposConfig: RepositoriesConfig,
      dataStore: DataStore): Either[DataStoreStartupError, Unit] = {
    val dataStoreMap = dataStore.asMap.keySet
    val configMap = reposConfig.asMap.keySet

    val differingValues = dataStoreMap.diff(configMap)

    if (differingValues.isEmpty) {
      Right((): Unit)
    } else {
      Left(
        DataStoreStartupError(s"Unexpected response loading the following repos: $differingValues"))
    }
  }

  /*
   * Validates that there's exactly one repo defined across all datastore configs. Returns only
   * DataStoreConfigs with at least one defined repo if valid
   */
  def getValidatedConfigs(
      configs: List[DataStoreConfig]): Either[DataStoreStartupError, List[DataStoreConfig]] = {

    val activeConfigMaps = configs.map(_.repositories.asMap).filter(_.nonEmpty)

    val validated = RepositoryName.values.map { repoName =>
      val definedRepos = activeConfigMaps.flatMap(conf => conf.get(repoName))
      definedRepos match {
        case _ :: Nil => ().validNel[String]
        case Nil => s"Must have one repo of type $repoName".invalidNel[Unit]
        case _ =>
          s"May not have more than one repo of type $repoName"
            .invalidNel[Unit]
      }
    }

    val combinedValidations = validated.fold(().validNel)(_ |+| _)
    combinedValidations.toEither
      .map(_ => configs.filter(_.repositories.asMap.nonEmpty))
      .leftMap { errors =>
        val errorString = errors.toList.mkString(", ")
        DataStoreStartupError(s"Config validation error: $errorString")
      }
  }

  def generateAccessor(
      configs: List[DataStoreConfig],
      stringToStore: Map[String, DataStore]): Either[DataStoreStartupError, DataAccessor] = {

    // maps RepositoryName to the DataStore that should hold that repo based on config
    val reposByType = RepositoryName.values.toList.flatMap { repoName =>
      val store = for {
        matchingDbConfig <- configs.find(_.repositories.asMap.contains(repoName))
        matchingDbName = matchingDbConfig.className
        matchingDataStore <- stringToStore.get(matchingDbName)
      } yield matchingDataStore

      store.map(s => repoName -> s)
    }.toMap

    val userRepo = reposByType.get(RepositoryName.user).flatMap(_.userRepository)
    val groupRepo = reposByType.get(RepositoryName.group).flatMap(_.groupRepository)
    val membershipRepo = reposByType.get(RepositoryName.membership).flatMap(_.membershipRepository)
    val groupChangeRepo =
      reposByType.get(RepositoryName.groupChange).flatMap(_.groupChangeRepository)
    val recordSetRepo = reposByType.get(RepositoryName.recordSet).flatMap(_.recordSetRepository)
    val recordChangeRepo =
      reposByType.get(RepositoryName.recordChange).flatMap(_.recordChangeRepository)
    val zoneChangeRepo = reposByType.get(RepositoryName.zoneChange).flatMap(_.zoneChangeRepository)
    val zoneRepo = reposByType.get(RepositoryName.zone).flatMap(_.zoneRepository)
    val batchChangeRepo =
      reposByType.get(RepositoryName.batchChange).flatMap(_.batchChangeRepository)

    val accessor: ValidatedNel[String, DataAccessor] =
      (
        getExistingRepo(userRepo, user),
        getExistingRepo(groupRepo, group),
        getExistingRepo(membershipRepo, membership),
        getExistingRepo(groupChangeRepo, groupChange),
        getExistingRepo(recordSetRepo, recordSet),
        getExistingRepo(recordChangeRepo, recordChange),
        getExistingRepo(zoneChangeRepo, zoneChange),
        getExistingRepo(zoneRepo, zone),
        getExistingRepo(batchChangeRepo, batchChange))
        .mapN(DataAccessor)

    accessor.toEither.leftMap(errors => DataStoreStartupError(errors.toList.mkString(", ")))
  }

  def getExistingRepo[A](a: Option[A], name: RepositoryName): ValidatedNel[String, A] =
    Validated.fromOption(a, s"Repo $name does not exist in configured database").toValidatedNel
}
