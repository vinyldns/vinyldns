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

package vinyldns.mysql.repository

import cats.effect.IO
import pureconfig.module.catseffect.loadConfigF
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.repository._
import vinyldns.mysql.{MySqlConnectionSettings, MySqlConnector}

class MySqlDataStoreProvider extends DataStoreProvider {

  private val implementedRepositories =
    Set(RepositoryName.zone, RepositoryName.batchChange, RepositoryName.zoneChange)

  def load(config: DataStoreConfig, cryptoAlgebra: CryptoAlgebra): IO[DataStore] =
    for {
      settingsConfig <- loadConfigF[IO, MySqlConnectionSettings](config.settings)
      _ <- migrationsOn(settingsConfig)
      _ <- validateRepos(config.repositories)
      _ <- MySqlConnector.runDBMigrations(settingsConfig)
      _ <- MySqlConnector.setupDBConnection(settingsConfig)
      store <- initializeRepos()
    } yield store

  def migrationsOn(settingsConfig: MySqlConnectionSettings): IO[Unit] =
    IO.fromEither(
      Either.cond(
        settingsConfig.migrationSettings.isDefined,
        (),
        DataStoreStartupError("Migrations must be configured on if MySql database is enabled"))
    )

  def validateRepos(reposConfig: RepositoriesConfig): IO[Unit] = {
    val invalid = reposConfig.keys.diff(implementedRepositories)

    if (invalid.isEmpty) {
      IO.unit
    } else {
      val error = s"Invalid config provided to mysql; unimplemented repos included: $invalid"
      IO.raiseError(DataStoreStartupError(error))
    }
  }

  def initializeRepos(): IO[DataStore] = IO {
    val zones = Some(new MySqlZoneRepository())
    val batchChanges = Some(new MySqlBatchChangeRepository())
    val zoneChanges = Some(new MySqlZoneChangeRepository())
    DataStore(
      zoneRepository = zones,
      batchChangeRepository = batchChanges,
      zoneChangeRepository = zoneChanges)
  }
}
