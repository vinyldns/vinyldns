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

package modules

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

import cats.effect.IO
import com.google.inject.AbstractModule
import controllers._
import play.api.{Configuration, Environment}
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{UserChangeRepository, UserRepository}
import vinyldns.dynamodb.repository.{
  DynamoDBDataStoreSettings,
  DynamoDBRepositorySettings,
  DynamoDBUserChangeRepository,
  DynamoDBUserRepository
}

class VinylDNSModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  val settings = new Settings(configuration)

  def configure(): Unit = {
    // Note: Leaving the unsafeRunSync here until we do full dynamic loading of the data store
    val crypto = CryptoAlgebra.load(configuration.underlying.getConfig("crypto")).unsafeRunSync()

    val dynamoConfig = configuration.get[Configuration]("dynamo")
    val dynamoSettings = DynamoDBDataStoreSettings(
      key = dynamoConfig.get[String]("key"),
      secret = dynamoConfig.get[String]("secret"),
      endpoint = dynamoConfig.get[String]("endpoint"),
      region = dynamoConfig.get[String]("region")
    )

    bind(classOf[Authenticator]).toInstance(authenticator())
    bind(classOf[UserRepository]).toInstance(userRepository(dynamoSettings, crypto))
    bind(classOf[UserChangeRepository]).toInstance(changeLogStore(dynamoSettings, crypto))
  }

  private def authenticator(): Authenticator =
    /**
      * Why not load config here you ask?  Well, there is some ugliness in the LdapAuthenticator
      * that I am not looking to undo at this time.  There are private classes
      * that do some wrapping.  It all seems to work, so I am leaving it alone
      * to complete the Play framework upgrade
      */
    LdapAuthenticator(settings)

  private def userRepository(
      dynamoSettings: DynamoDBDataStoreSettings,
      crypto: CryptoAlgebra): DynamoDBUserRepository = {
    for {
      repoSettings <- IO(
        DynamoDBRepositorySettings(
          tableName = configuration.get[String]("users.tablename"),
          provisionedReads = configuration.get[Long]("users.provisionedReadThroughput"),
          provisionedWrites = configuration.get[Long]("users.provisionedWriteThroughput")
        )
      )
      repo <- DynamoDBUserRepository(repoSettings, dynamoSettings, crypto)
    } yield repo
  }.unsafeRunSync()

  private def changeLogStore(
      dynamoSettings: DynamoDBDataStoreSettings,
      crypto: CryptoAlgebra): DynamoDBUserChangeRepository = {
    for {
      repoSettings <- IO(
        DynamoDBRepositorySettings(
          tableName = configuration.get[String]("changelog.tablename"),
          provisionedReads = configuration.get[Long]("changelog.provisionedReadThroughput"),
          provisionedWrites = configuration.get[Long]("changelog.provisionedWriteThroughput")
        )
      )
      repo <- DynamoDBUserChangeRepository(repoSettings, dynamoSettings, crypto)
    } yield repo
  }.unsafeRunSync()
}
