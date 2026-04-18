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

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.repository.RepositoryName.{RepositoryName}

final case class DataStoreConfig(
    className: String,
    settings: Config,
    repositories: RepositoriesConfig = RepositoriesConfig.empty
)

final case class RepositoriesConfig(
    user: Option[Config],
    group: Option[Config],
    membership: Option[Config],
    groupChange: Option[Config],
    recordSet: Option[Config],
    recordChange: Option[Config],
    recordSetCache: Option[Config],
    zoneChange: Option[Config],
    zone: Option[Config],
    batchChange: Option[Config],
    userChange: Option[Config],
    task: Option[Config],
    appConfig: Option[Config]
) {

  lazy val configMap: Map[RepositoryName, Config] = List(
    user.map(RepositoryName.user -> _),
    group.map(RepositoryName.group -> _),
    membership.map(RepositoryName.membership -> _),
    groupChange.map(RepositoryName.groupChange -> _),
    recordSet.map(RepositoryName.recordSet -> _),
    recordChange.map(RepositoryName.recordChange -> _),
    recordSetCache.map(RepositoryName.recordSetCache -> _),
    zoneChange.map(RepositoryName.zoneChange -> _),
    zone.map(RepositoryName.zone -> _),
    batchChange.map(RepositoryName.batchChange -> _),
    userChange.map(RepositoryName.userChange -> _),
    task.map(RepositoryName.task -> _),
    appConfig.map(RepositoryName.appConfig -> _)
  ).flatten.toMap

  def hasKey(name: RepositoryName): Boolean = configMap.contains(name)

  def get(name: RepositoryName): Option[Config] = configMap.get(name)

  def nonEmpty: Boolean = configMap.nonEmpty

  def keys: Set[RepositoryName] = configMap.keySet
}

object RepositoriesConfig {
  private val emptyConfig: Config = ConfigFactory.empty()
  val empty: RepositoriesConfig =
    RepositoriesConfig(None, None, None, None, None, None, None, None, None, None, None, None, None)

  /** Creates a RepositoriesConfig that claims ownership of all the given repo names. */
  def forAll(names: List[RepositoryName]): RepositoriesConfig = {
    def opt(name: RepositoryName): Option[Config] = if (names.contains(name)) Some(emptyConfig) else None
    RepositoriesConfig(
      user          = opt(RepositoryName.user),
      group         = opt(RepositoryName.group),
      membership    = opt(RepositoryName.membership),
      groupChange   = opt(RepositoryName.groupChange),
      recordSet     = opt(RepositoryName.recordSet),
      recordChange  = opt(RepositoryName.recordChange),
      recordSetCache= opt(RepositoryName.recordSetCache),
      zoneChange    = opt(RepositoryName.zoneChange),
      zone          = opt(RepositoryName.zone),
      batchChange   = opt(RepositoryName.batchChange),
      userChange    = opt(RepositoryName.userChange),
      task          = opt(RepositoryName.task),
      appConfig     = opt(RepositoryName.appConfig)
    )
  }
}
