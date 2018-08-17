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
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{GroupChangeRepository, MembershipRepository}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.ZoneChangeRepository
import vinyldns.api.repository.dynamodb.DynamoDbDataStore
import vinyldns.api.repository.mysql.MySqlDataStore
import cats.effect.IO
import cats.implicits._
import com.typesafe.config.Config
import vinyldns.api.domain.membership.{GroupRepository, UserRepository}
import vinyldns.api.domain.zone.ZoneRepository

object DataStoreProvider {

  // TODO placeholder, need to actually dynamically load
  def load(config: Config): IO[DataStore] =
    for {
      className <- IO(config.getString("type"))
      dataStore <- IO(className match {
        case "vinyldns.api.repository.mysql.MySqlDataStore" => new MySqlDataStore(config)
        case _ => new DynamoDbDataStore()
      })
    } yield dataStore

  def loadAll(configs: List[Config]): IO[DataAccessor] = {
    val dataStores = configs.map(load).parSequence
    dataStores.flatMap { stores =>
      val userRepos = stores.flatMap(_.userRepository)
      val groupRepos = stores.flatMap(_.groupRepository)
      val membershipRepos = stores.flatMap(_.membershipRepository)
      val groupChangeRepos = stores.flatMap(_.groupChangeRepository)
      val recordSetRepos = stores.flatMap(_.recordSetRepository)
      val recordChangeRepos = stores.flatMap(_.recordChangeRepository)
      val zoneChangeRepos = stores.flatMap(_.zoneChangeRepository)
      val zoneRepos = stores.flatMap(_.zoneRepository)
      val batchChangeRepos = stores.flatMap(_.batchChangeRepository)

      val accessor: ValidatedNel[String, DataAccessor] =
        (
          extractRepo(userRepos, "user"),
          extractRepo(groupRepos, "group"),
          extractRepo(membershipRepos, "membership"),
          extractRepo(groupChangeRepos, "groupChange"),
          extractRepo(recordSetRepos, "recordSet"),
          extractRepo(recordChangeRepos, "recordChange"),
          extractRepo(zoneChangeRepos, "zoneChange"),
          extractRepo(zoneRepos, "zone"),
          extractRepo(batchChangeRepos, "batchChange")).mapN(DataAccessor)

      val asEither =
        accessor.leftMap(err => new RuntimeException(err.toList.mkString(", "))).toEither

      IO.fromEither(asEither)
    }
  }

  def extractRepo[A](repos: List[A], name: String): ValidatedNel[String, A] =
    repos match {
      case h :: Nil => h.validNel
      case Nil => s"Invalid config! Must have one repo of type $name".invalidNel[A]
      case _ =>
        s"Invalid config! May not have more than one repo of type $name"
          .invalidNel[A]
    }
}

case class DataAccessor(
    userRepository: UserRepository,
    groupRepository: GroupRepository,
    membershipRepository: MembershipRepository,
    groupChangeRepository: GroupChangeRepository,
    recordSetRepository: RecordSetRepository,
    recordChangeRepository: RecordChangeRepository,
    zoneChangeRepository: ZoneChangeRepository,
    zoneRepository: ZoneRepository,
    batchChangeRepository: BatchChangeRepository)

trait DataStore {
  def userRepository: Option[UserRepository]
  def groupRepository: Option[GroupRepository]
  def membershipRepository: Option[MembershipRepository]
  def groupChangeRepository: Option[GroupChangeRepository]
  def recordSetRepository: Option[RecordSetRepository]
  def recordChangeRepository: Option[RecordChangeRepository]
  def zoneChangeRepository: Option[ZoneChangeRepository]
  def zoneRepository: Option[ZoneRepository]
  def batchChangeRepository: Option[BatchChangeRepository]
}
