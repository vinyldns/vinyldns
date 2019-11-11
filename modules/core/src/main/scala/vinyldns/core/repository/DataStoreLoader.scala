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

package vinyldns.core.repository

import cats.data._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import vinyldns.core.crypto.CryptoAlgebra
import org.slf4j.LoggerFactory
import vinyldns.core.repository.RepositoryName._
import vinyldns.core.health.HealthCheck.HealthCheck

import scala.reflect.ClassTag

object DataStoreLoader {

  class DataStoreInfo(
      val dataStoreConfig: DataStoreConfig,
      val dataStore: DataStore,
      val shutdownHook: IO[Unit],
      val healthCheck: HealthCheck
  ) {
    val accessorTuple: (DataStoreConfig, DataStore) = (dataStoreConfig, dataStore)
  }

  class DataLoaderResponse[A](
      val accessor: A,
      shutdownHook: List[IO[Unit]],
      val healthChecks: List[HealthCheck]
  ) {
    def shutdown(): Unit = shutdownHook.parSequence.unsafeRunSync()
  }

  private val logger = LoggerFactory.getLogger("DataStoreLoader")
  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def loadAll[A <: DataAccessor](
      configs: List[DataStoreConfig],
      crypto: CryptoAlgebra,
      dataAccessorProvider: DataAccessorProvider[A]
  ): IO[DataLoaderResponse[A]] =
    for {
      activeConfigs <- IO.fromEither(getValidatedConfigs(configs, dataAccessorProvider.repoNames))
      dataStores <- activeConfigs.map(load(_, crypto)).parSequence
      accessor <- IO.fromEither(generateAccessor(dataStores, dataAccessorProvider))
    } yield new DataLoaderResponse[A](
      accessor,
      dataStores.map(_.shutdownHook),
      dataStores.map(_.healthCheck)
    )

  def load(config: DataStoreConfig, crypto: CryptoAlgebra): IO[DataStoreInfo] =
    for {
      _ <- IO(
        logger
          .error(s"Attempting to load repos ${config.repositories.keys} from ${config.className}")
      )
      provider <- IO(
        Class
          .forName(config.className)
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[DataStoreProvider]
      )
      loadResponse <- provider.load(config, crypto)
    } yield new DataStoreInfo(
      config,
      loadResponse.dataStore,
      loadResponse.shutdownHook,
      loadResponse.healthCheck
    )

  /*
   * Validates that there's exactly one repo defined across all datastore configs. Returns only
   * DataStoreConfigs with at least one defined repo if valid
   */
  def getValidatedConfigs(
      configs: List[DataStoreConfig],
      repoNames: List[RepositoryName]
  ): Either[DataStoreStartupError, List[DataStoreConfig]] = {

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

    val combinedValidations = repoNames.map(existsOnce).reduce(_ |+| _)

    combinedValidations.toEither
      .map(_ => configs.filter(_.repositories.nonEmpty))
      .leftMap { errors =>
        val errorString = errors.toList.mkString(", ")
        DataStoreStartupError(s"Config validation error: $errorString")
      }
  }

  def getRepoOf[A <: Repository: ClassTag](
      responses: List[(DataStoreConfig, DataStore)],
      repoName: RepositoryName
  ): ValidatedNel[String, A] = {

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

  def generateAccessor[A <: DataAccessor](
      responses: List[DataStoreInfo],
      dataAccessorProvider: DataAccessorProvider[A]
  ): Either[DataStoreStartupError, A] = {
    val accessor = dataAccessorProvider
      .create(responses.map(_.accessorTuple))
    accessor.toEither.leftMap(errors => DataStoreStartupError(errors.toList.mkString(", ")))
  }
}
