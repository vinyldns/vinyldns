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

package vinyldns.route53.backend

import com.amazonaws.auth._
import org.slf4j.LoggerFactory
import com.amazonaws.services.securitytoken.{
  AWSSecurityTokenService,
  AWSSecurityTokenServiceClientBuilder
}

private[backend] sealed trait Route53Credentials extends Serializable {
  def provider: AWSCredentialsProvider
}

private[backend] final case object DefaultCredentials extends Route53Credentials {
  def provider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain
}

private[backend] final case class BasicCredentials(accessKeyId: String, secretKey: String)
    extends Route53Credentials {

  private final val logger = LoggerFactory.getLogger(classOf[Route53Backend])

  def provider: AWSCredentialsProvider =
    try {
      new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey))
    } catch {
      case e: IllegalArgumentException =>
        logger.error(
          "Error when using accessKey/secret: {}. Using DefaultProviderChain.",
          e.getMessage()
        )
        new DefaultAWSCredentialsProviderChain
    }
}

private[backend] final case class STSCredentials(
    roleArn: String,
    sessionName: String,
    externalId: Option[String] = None,
    longLivedCreds: Route53Credentials = DefaultCredentials
) extends Route53Credentials {

  def provider: AWSCredentialsProvider = {
    lazy val stsClient: AWSSecurityTokenService =
      AWSSecurityTokenServiceClientBuilder
        .standard()
        .withCredentials(longLivedCreds.provider)
        .build()
    val builder = new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, sessionName)
      .withStsClient(stsClient)
    externalId match {
      case Some(externalId) =>
        builder
          .withExternalId(externalId)
          .build()
      case None =>
        builder.build()
    }
  }
}

object Route53Credentials {
  class Builder {
    private var basicCreds: Option[BasicCredentials] = None
    private var stsCreds: Option[STSCredentials] = None

    def basicCredentials(accessKeyId: String, secretKey: String): Builder = {
      basicCreds = Option(BasicCredentials(accessKeyId, secretKey))
      this
    }

    def withRole(roleArn: String, sessionName: String): Builder = {
      stsCreds = Option(STSCredentials(roleArn, sessionName))
      this
    }

    def withRole(roleArn: String, sessionName: String, externalId: String): Builder = {
      stsCreds = Option(
        STSCredentials(
          roleArn,
          sessionName,
          Option(externalId)
        )
      )
      this
    }

    def build(): Route53Credentials =
      stsCreds.map(_.copy(longLivedCreds = longLivedCreds)).getOrElse(longLivedCreds)

    private def longLivedCreds: Route53Credentials = basicCreds.getOrElse(DefaultCredentials)
  }

  def builder: Builder = new Builder
}
