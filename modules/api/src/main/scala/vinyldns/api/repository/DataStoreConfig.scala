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

import com.typesafe.config.Config
import vinyldns.api.repository.RepositoryName.RepositoryName

final case class DataStoreConfig(
    className: String,
    settings: Config,
    repositories: RepositoriesConfig)

object RepositoryName extends Enumeration {
  type RepositoryName = Value
  val user, group, membership, groupChange, recordSet, recordChange, zoneChange, zone, batchChange =
    Value
}

final case class RepositoriesConfig(
    user: Option[Config],
    group: Option[Config],
    membership: Option[Config],
    groupChange: Option[Config],
    recordSet: Option[Config],
    recordChange: Option[Config],
    zoneChange: Option[Config],
    zone: Option[Config],
    batchChange: Option[Config]) {

  def asMap: Map[RepositoryName, Config] =
    List(
      user.map(x => RepositoryName.user -> x),
      group.map(x => RepositoryName.group -> x),
      membership.map(x => RepositoryName.membership -> x),
      groupChange.map(x => RepositoryName.groupChange -> x),
      recordSet.map(x => RepositoryName.recordSet -> x),
      recordChange.map(x => RepositoryName.recordChange -> x),
      zoneChange.map(x => RepositoryName.zoneChange -> x),
      zone.map(x => RepositoryName.zone -> x),
      batchChange.map(x => RepositoryName.batchChange -> x)
    ).flatten.toMap
}
