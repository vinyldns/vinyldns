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

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBClient, AmazonDynamoDBClientBuilder}
import com.google.inject.AbstractModule
import controllers._
import controllers.datastores.{
  DynamoDBChangeLogStore,
  DynamoDBUserAccountStore,
  InMemoryChangeLogStore,
  InMemoryUserAccountStore
}
import play.api.{Configuration, Environment}
import vinyldns.core.crypto.CryptoAlgebra

class Module(environment: Environment, configuration: Configuration) extends AbstractModule {

  val settings = new Settings(configuration)

  def configure(): Unit = {
    val crypto = CryptoAlgebra.load(configuration.underlying.getConfig("crypto")).unsafeRunSync()
    bind(classOf[Authenticator]).toInstance(authenticator())
    bind(classOf[UserAccountStore]).toInstance(userAccountStore(crypto))
    bind(classOf[ChangeLogStore]).toInstance(changeLogStore(crypto))
  }

  private def authenticator(): Authenticator =
    /**
      * Why not load config here you ask?  Well, there is some ugliness in the LdapAuthenticator
      * that I am not looking to undo at this time.  There are private classes
      * that do some wrapping.  It all seems to work, so I am leaving it alone
      * to complete the Play framework upgrade
      */
    LdapAuthenticator(settings)

  private def userAccountStore(crypto: CryptoAlgebra) = {
    val useDummy = configuration.get[Boolean]("users.dummy")
    if (useDummy)
      new InMemoryUserAccountStore
    else {
      // Important!  For some reason the basic credentials get lost in Jenkins.  Set the aws system properties
      // just in case
      val dynamoAKID = configuration.get[String]("dynamo.key")
      val dynamoSecret = configuration.get[String]("dynamo.secret")
      val dynamoEndpoint = configuration.get[String]("dynamo.endpoint")
      val credentials = new BasicAWSCredentials(dynamoAKID, dynamoSecret)
      val dynamoClient = AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .withEndpointConfiguration(new EndpointConfiguration(dynamoEndpoint, "us-east-1"))
        .build()
        .asInstanceOf[AmazonDynamoDBClient]
      new DynamoDBUserAccountStore(dynamoClient, configuration, crypto)
    }
  }

  private def changeLogStore(crypto: CryptoAlgebra) = {
    val useDummy = configuration.get[Boolean]("changelog.dummy")
    if (useDummy)
      new InMemoryChangeLogStore
    else {
      val dynamoAKID = configuration.get[String]("dynamo.key")
      val dynamoSecret = configuration.get[String]("dynamo.secret")
      val dynamoEndpoint = configuration.get[String]("dynamo.endpoint")
      val credentials = new BasicAWSCredentials(dynamoAKID, dynamoSecret)
      val dynamoClient = AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(credentials))
        .withEndpointConfiguration(new EndpointConfiguration(dynamoEndpoint, "us-east-1"))
        .build()
        .asInstanceOf[AmazonDynamoDBClient]
      new DynamoDBChangeLogStore(dynamoClient, configuration, crypto)
    }
  }
}
