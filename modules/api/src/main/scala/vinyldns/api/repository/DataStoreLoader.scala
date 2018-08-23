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
import com.typesafe.config.Config

class DataStoreLoader {

  def loadAll(configs: List[DataStoreConfig]): IO[DataAccessor] =
    for {
      activeConfigs <- IO.fromEither(getValidatedConfigs(configs))
      dataStores <- activeConfigs.map(load).parSequence
      accessor <- IO.fromEither(generateAccessor(dataStores))
    } yield accessor

  def load(config: DataStoreConfig): IO[DataStore] =
    for {
      className <- IO.pure(config.className)
      provider <- IO(Class.forName(className).newInstance.asInstanceOf[DataStoreProvider])
      dataStore <- provider.load(config)
      _ <- IO.fromEither(validateLoadResponse(config.repositories, dataStore))
    } yield dataStore


  // Ensures that if a datastore is configured on, load returned it, and if configured off, load did not
  def validateLoadResponse(repos: RepositoriesConfig, dataStore: DataStore): Either[DataStoreStartupError, Unit] = {
    def optionsAgree(opt1: Option[Config], opt2: Option[Any], name: String): Either[DataStoreStartupError, Unit] = {
      (opt1, opt2) match {
        case (Some(_), Some(_)) => Right(())
        case (None, None) => Right(())
        case _ => Left(DataStoreStartupError(s"Unexpected response from loading repo: $name"))
      }
    }

    for {
      _ <- optionsAgree(repos.user, dataStore.userRepository, "user")
      _ <- optionsAgree(repos.group, dataStore.groupRepository, "user")
      _ <- optionsAgree(repos.membership, dataStore.membershipRepository, "user")
      _ <- optionsAgree(repos.groupChange, dataStore.groupChangeRepository, "user")
      _ <- optionsAgree(repos.recordSet, dataStore.recordSetRepository, "user")
      _ <- optionsAgree(repos.recordChange, dataStore.recordChangeRepository, "user")
      _ <- optionsAgree(repos.zoneChange, dataStore.zoneChangeRepository, "user")
      _ <- optionsAgree(repos.zone, dataStore.zoneRepository, "user")
      valid <- optionsAgree(repos.batchChange, dataStore.batchChangeRepository, "user")
    } yield valid
  }

  /*
   * Validates that there's exactly one repo defined across all datastore configs. Returns only
   * DataStoreConfigs with at least one defined repo if valid
   */
  def getValidatedConfigs(configs: List[DataStoreConfig])
    : Either[DataStoreStartupError, List[DataStoreConfig]] = {

    val activeConfigs = configs.filter(_.repositories.containsActiveRepo)
    val repoConfigs = activeConfigs.map(_.repositories)

    val validated: ValidatedNel[String, Unit] =
      listContainsSingleConfig(repoConfigs.flatMap(_.user), "user") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.group), "group") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.membership), "membership") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.groupChange), "groupChange") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.recordSet), "recordSet") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.recordChange), "recordChange") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.zoneChange), "zoneChange") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.zone), "zone") |+|
        listContainsSingleConfig(repoConfigs.flatMap(_.batchChange), "batchChange")

    validated.toEither
      .map(_ => activeConfigs)
      .leftMap { errors =>
        val errorString = errors.toList.mkString(", ")
        DataStoreStartupError(s"Config error: $errorString")
      }
  }

  def generateAccessor(
      dataStores: List[DataStore]): Either[DataStoreStartupError, DataAccessor] = {
    // Note: headOption is fine here only because we've already validated the config has a single
    // instance defined for each repo across datastores
    val accessor = for {
      userRepo <- dataStores.flatMap(_.userRepository).headOption
      groupRepo <- dataStores.flatMap(_.groupRepository).headOption
      membershipRepo <- dataStores.flatMap(_.membershipRepository).headOption
      groupChangeRepo <- dataStores.flatMap(_.groupChangeRepository).headOption
      recordSetRepo <- dataStores.flatMap(_.recordSetRepository).headOption
      recordChangeRepo <- dataStores.flatMap(_.recordChangeRepository).headOption
      zoneChangeRepo <- dataStores.flatMap(_.zoneChangeRepository).headOption
      zoneRepo <- dataStores.flatMap(_.zoneRepository).headOption
      batchChangeRepo <- dataStores.flatMap(_.batchChangeRepository).headOption
    } yield
      DataAccessor(
        userRepo,
        groupRepo,
        membershipRepo,
        groupChangeRepo,
        recordSetRepo,
        recordChangeRepo,
        zoneChangeRepo,
        zoneRepo,
        batchChangeRepo
      )

    Either.fromOption(
      accessor,
      DataStoreStartupError("error pulling repositories from databases"))
  }

  private def listContainsSingleConfig(
      configs: List[Config],
      name: String): ValidatedNel[String, Unit] =
    configs match {
      case _ :: Nil => ().validNel[String]
      case Nil => s"Must have one repo of type $name".invalidNel[Unit]
      case _ =>
        s"May not have more than one repo of type $name"
          .invalidNel[Unit]
    }
}
