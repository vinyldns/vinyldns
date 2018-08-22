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
    } yield dataStore

  /*
   * Validates that there's exactly one repo defined across all datastore configs. Returns only
   * DataStoreConfigs with at least one defined repo if valid
   */
  def getValidatedConfigs(configs: List[DataStoreConfig])
    : Either[DataStoreInitializationError, List[DataStoreConfig]] = {

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
        DataStoreInitializationError(s"Config error: $errorString")
      }
  }

  def generateAccessor(
      dataStores: List[DataStore]): Either[DataStoreInitializationError, DataAccessor] = {
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
      DataStoreInitializationError("error pulling repositories from databases"))
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
