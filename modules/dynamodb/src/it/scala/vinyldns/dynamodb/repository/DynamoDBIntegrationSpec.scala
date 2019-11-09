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

import java.util.UUID

import org.scalatest._
import org.slf4j.{Logger, LoggerFactory}

trait DynamoDBIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with Matchers
    with Inspectors {

  // port is defined in the docker/docker-compose.yml file for dynamodb
  val dynamoIntegrationConfig: DynamoDBDataStoreSettings = getDynamoConfig(19000)
  val logger: Logger = LoggerFactory.getLogger("DynamoDBIntegrationSpec")

  // only used for teardown
  lazy val testDynamoDBHelper: DynamoDBHelper =
    new DynamoDBHelper(DynamoDBClient(dynamoIntegrationConfig), logger)

  def getDynamoConfig(port: Int): DynamoDBDataStoreSettings =
    DynamoDBDataStoreSettings(
      "vinyldnsTest",
      "notNeededForDynamoDbLocal",
      s"http://localhost:$port",
      "us-east-1"
    )

  override protected def beforeAll(): Unit =
    setup()

  override protected def afterAll(): Unit =
    tearDown()

  /* Allows a spec to initialize the database */
  def setup(): Unit

  /* Allows a spec to clean up */
  def tearDown(): Unit

  /* Generates a random string useful to avoid data collision */
  def genString: String = UUID.randomUUID().toString

}
