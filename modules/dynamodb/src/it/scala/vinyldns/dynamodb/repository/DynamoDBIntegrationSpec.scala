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

import cats.effect.IO
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._

trait DynamoDBIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with Inspectors {

  // port is defined in the docker/docker-compose.yml file for dynamodb
  val dynamoIntegrationConfig: DynamoDBDataStoreSettings = getDynamoConfig(19003)

  def getDynamoConfig(port: Int): DynamoDBDataStoreSettings = {
    DynamoDBDataStoreSettings("vinyldnsTest",
      "notNeededForDynamoDbLocal",
      s"http://localhost:$port",
      "us-east-1")
  }

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

  /* wait until the repo is ready, could take time if the table has to be created */
  def waitForRepo[A](call: IO[A]): Unit = {
    var notReady = call.unsafeRunTimed(5.seconds).isEmpty
    while (notReady) {
      Thread.sleep(2000)
      notReady = call.unsafeRunTimed(5.seconds).isEmpty
    }
  }
}
