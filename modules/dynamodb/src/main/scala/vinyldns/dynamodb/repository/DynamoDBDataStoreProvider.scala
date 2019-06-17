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

package vinyldns.dynamodb.repository

import cats.implicits._
import cats.effect.{ContextShift, IO}
import org.slf4j.LoggerFactory
import vinyldns.core.repository._
import pureconfig.module.catseffect.loadConfigF
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.RepositoryName._
import vinyldns.core.health.HealthCheck._
import vinyldns.core.task.TaskRepository

class DynamoDBDataStoreProvider extends DataStoreProvider {

  private val logger = LoggerFactory.getLogger(classOf[DynamoDBDataStoreProvider])
  private val implementedRepositories =
    Set(group, membership, groupChange, recordChange, zoneChange, userChange)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def load(config: DataStoreConfig, crypto: CryptoAlgebra): IO[LoadedDataStore] =
    for {
      settingsConfig <- loadConfigF[IO, DynamoDBDataStoreSettings](config.settings)
      _ <- validateRepos(config.repositories)
      repoConfigs <- loadRepoConfigs(config.repositories)
      dataStore <- initializeRepos(settingsConfig, repoConfigs, crypto)
    } yield new LoadedDataStore(dataStore, IO.unit, checkHealth(settingsConfig))

  def validateRepos(reposConfig: RepositoriesConfig): IO[Unit] = {
    val invalid = reposConfig.keys.diff(implementedRepositories)

    if (invalid.isEmpty) {
      IO.unit
    } else {
      val error = s"Invalid config provided to dynamodb; unimplemented repos included: $invalid"
      IO.raiseError(DataStoreStartupError(error))
    }
  }

  def loadRepoConfigs(
      config: RepositoriesConfig): IO[Map[RepositoryName, DynamoDBRepositorySettings]] = {

    def loadConfigIfDefined(
        repositoryName: RepositoryName): Option[IO[(RepositoryName, DynamoDBRepositorySettings)]] =
      config.get(repositoryName).map { repoConf =>
        loadConfigF[IO, DynamoDBRepositorySettings](repoConf).map(repositoryName -> _)
      }

    val activeRepoSettings = RepositoryName.values.toList.flatMap(loadConfigIfDefined).parSequence

    activeRepoSettings.map(_.toMap)
  }

  def initializeRepos(
      dynamoConfig: DynamoDBDataStoreSettings,
      repoSettings: Map[RepositoryName, DynamoDBRepositorySettings],
      crypto: CryptoAlgebra): IO[DataStore] = {

    def initializeSingleRepo[T <: Repository](
        repoName: RepositoryName,
        fn: DynamoDBRepositorySettings => IO[T]): IO[Option[T]] =
      repoSettings
        .get(repoName)
        .map { configuredOn =>
          for {
            _ <- IO(logger.error(s"Loading dynamodb repo for type: $repoName"))
            repo <- fn(configuredOn)
            _ <- IO(logger.error(s"Completed dynamodb load for type: $repoName"))
          } yield repo
        }
        .sequence

    (
      initializeSingleRepo[UserRepository](
        user,
        DynamoDBUserRepository.apply(_, dynamoConfig, crypto)),
      initializeSingleRepo[GroupRepository](group, DynamoDBGroupRepository.apply(_, dynamoConfig)),
      initializeSingleRepo[MembershipRepository](
        membership,
        DynamoDBMembershipRepository.apply(_, dynamoConfig)),
      initializeSingleRepo[GroupChangeRepository](
        groupChange,
        DynamoDBGroupChangeRepository.apply(_, dynamoConfig)),
      initializeSingleRepo[RecordSetRepository](
        recordSet,
        DynamoDBRecordSetRepository.apply(_, dynamoConfig)),
      initializeSingleRepo[RecordChangeRepository](
        recordChange,
        DynamoDBRecordChangeRepository.apply(_, dynamoConfig)),
      initializeSingleRepo[ZoneChangeRepository](
        zoneChange,
        DynamoDBZoneChangeRepository.apply(_, dynamoConfig)),
      IO.pure[Option[ZoneRepository]](None),
      IO.pure[Option[BatchChangeRepository]](None),
      initializeSingleRepo[UserChangeRepository](
        userChange,
        DynamoDBUserChangeRepository.apply(_, dynamoConfig, crypto)),
      IO.pure[Option[TaskRepository]](None)
    ).parMapN {
      DataStore.apply
    }
  }

  private def checkHealth(dynamoConfig: DynamoDBDataStoreSettings): HealthCheck =
    IO {
      val client = DynamoDBClient(dynamoConfig)
      client.listTables(1)
    }.attempt.asHealthCheck
}
