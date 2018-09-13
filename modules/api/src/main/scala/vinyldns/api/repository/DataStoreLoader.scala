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
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository._
import vinyldns.core.repository.RepositoryName._

import scala.reflect.ClassTag

object DataStoreLoader {
  def loadAll(configs: List[DataStoreConfig], crypto: CryptoAlgebra): IO[DataAccessor] =
    for {
      activeConfigs <- IO.fromEither(getValidatedConfigs(configs))
      dataStores <- activeConfigs.map(load(_, crypto)).parSequence
      accessor <- IO.fromEither(generateAccessor(dataStores))
    } yield accessor

  def load(config: DataStoreConfig, crypto: CryptoAlgebra): IO[(DataStoreConfig, DataStore)] =
    for {
      className <- IO.pure(config.className)
      provider <- IO(Class.forName(className).newInstance.asInstanceOf[DataStoreProvider])
      dataStore <- provider.load(config, crypto)
    } yield (config, dataStore)

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

  def generateAccessor(responses: List[(DataStoreConfig, DataStore)])
    : Either[DataStoreStartupError, DataAccessor] = {

    def getRepoOf[A <: Repository: ClassTag](repoName: RepositoryName): ValidatedNel[String, A] = {

      val matched = responses.find {
        case (c, _) => c.repositories.hasKey(repoName)
      }

      val repository = matched.flatMap {
        case (_, store) => store.get[A](repoName)
      }

      repository match {
        case Some(repo) => repo.validNel
        case None =>
          val dataStoreName = matched
            .map {
              case (c, _) => c.className
            }
            .getOrElse("Unknown Configured Database")

          s"Repo $repoName was not returned by configured database: $dataStoreName".invalidNel
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
