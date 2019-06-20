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

package controllers.repository

import cats.data.ValidatedNel
import cats.implicits._
import vinyldns.core.domain.membership.{UserChangeRepository, UserRepository}
import vinyldns.core.repository.DataStoreLoader.getRepoOf
import vinyldns.core.repository.RepositoryName._
import vinyldns.core.repository.{DataAccessorProvider, DataStore, DataStoreConfig}
import vinyldns.core.task.TaskRepository

// $COVERAGE-OFF$
object PortalDataAccessorProvider extends DataAccessorProvider[PortalDataAccessor] {
  def repoNames: List[RepositoryName] =
    List(user, userChange)

  def create(
      dataStores: List[(DataStoreConfig, DataStore)]): ValidatedNel[String, PortalDataAccessor] =
    (
      getRepoOf[UserRepository](dataStores, user),
      getRepoOf[UserChangeRepository](dataStores, userChange),
      getRepoOf[TaskRepository](dataStores, task)
    ).mapN(PortalDataAccessor)
}
// $COVERAGE-ON$
