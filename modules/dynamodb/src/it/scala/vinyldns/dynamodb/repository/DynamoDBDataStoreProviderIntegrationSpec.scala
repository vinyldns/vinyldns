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
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.core.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.core.repository.{DataStore, DataStoreConfig}
import vinyldns.core.repository.RepositoryName._


class DynamoDBDataStoreProviderIntegrationSpec extends DynamoDBIntegrationSpec {

  val config: Config = ConfigFactory.load()
  val dynamoDBConfig: DataStoreConfig =
    pureconfig.loadConfigOrThrow[DataStoreConfig](config, "dynamodb")
  val provider: DynamoDBDataStoreProvider = new DynamoDBDataStoreProvider()

  logger.info("Loading all dynamodb tables in DynamoDBDataStoreProviderSpec")
  val dataStore: DataStore = provider.load(dynamoDBConfig).unsafeRunSync()
  logger.info("DynamoDBDataStoreProviderSpec load complete")

  def setup(): Unit = ()

  def tearDown(): Unit = {
    val deletes = dynamoDBConfig.repositories.configMap.map {
      case (_, config) => {
        val asDynamo = pureconfig.loadConfigOrThrow[DynamoDBRepositorySettings](config)
        val request = new DeleteTableRequest().withTableName(asDynamo.tableName)
        testDynamoDBHelper.deleteTable(request)
      }
    }
    logger.info("Deleting all tables created by provider in DynamoDBDataStoreProviderSpec")
    deletes.toList.parSequence.unsafeRunSync()
    logger.info("DynamoDBDataStoreProviderSpec delete complete")
  }

  "DynamoDBDataStoreProvider" should {
    "properly load configured repos" in {
      dataStore.get[UserRepository](user) shouldBe defined
      dataStore.get[GroupRepository](group) shouldBe defined
      dataStore.get[MembershipRepository](membership) shouldBe defined
      dataStore.get[GroupChangeRepository](groupChange) shouldBe defined
      dataStore.get[RecordSetRepository](recordSet) shouldBe defined
      dataStore.get[RecordChangeRepository](recordChange) shouldBe defined
      dataStore.get[ZoneChangeRepository](zoneChange) shouldBe defined
    }
    "not load configured off repos" in {
      dataStore.get[ZoneRepository](zone) shouldBe empty
      dataStore.get[BatchChangeRepository](batchChange) shouldBe empty
    }
    "validate a loaded repo works" in {
      val testUser = User(
        "provider-load-test-user",
        "provider-load-test-access",
        "provider-load-test-secret"
      )
      val userRepo = dataStore.get[UserRepository](user)

      val save = userRepo.map(_.save(testUser)).parSequence
      save.unsafeRunSync() shouldBe Some(testUser)

      val get = userRepo.map(_.getUser(testUser.id)).parSequence
      get.unsafeRunSync().flatten shouldBe Some(testUser)
    }
  }
}
