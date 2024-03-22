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

package vinyldns.api.notifier.sns

import vinyldns.core.notifier.{Notifier, NotifierConfig, NotifierProvider}
import vinyldns.core.domain.membership.{GroupRepository, UserRepository}
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import cats.effect.{Blocker, ContextShift, IO}
import com.amazonaws.services.sns.AmazonSNS
import org.slf4j.LoggerFactory
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials

class SnsNotifierProvider extends NotifierProvider {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val logger = LoggerFactory.getLogger(classOf[SnsNotifierProvider])

  def load(config: NotifierConfig, userRepository: UserRepository, groupRepository: GroupRepository): IO[Notifier] =
    for {
      snsConfig <- Blocker[IO].use(
        ConfigSource.fromConfig(config.settings).loadF[IO, SnsNotifierConfig](_)
      )
      client <- createClient(snsConfig)
    } yield new SnsNotifier(snsConfig, client)

  def createClient(config: SnsNotifierConfig): IO[AmazonSNS] = IO {
    logger.error(
      "Setting up sns notifier client with settings: " +
        s"service endpoint: ${config.serviceEndpoint}; " +
        s"signing region: ${config.signingRegion}; " +
        s"topic name: ${config.topicArn}"
    )
    AmazonSNSClientBuilder.standard
      .withEndpointConfiguration(
        new EndpointConfiguration(config.serviceEndpoint, config.signingRegion)
      )
      .withCredentials(
        new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(config.accessKey, config.secretKey)
        )
      )
      .build()
  }

}
