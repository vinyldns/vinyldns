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

import cats.effect.{Blocker, ContextShift, IO}
import com.typesafe.config.Config
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource

// TODO: Add delegation set id and VPC options especially wrt CreateZone
final case class Route53BackendConfig(
    id: String,
    accessKey: Option[String],
    secretKey: Option[String],
    roleArn: Option[String],
    externalId: Option[String],
    serviceEndpoint: String,
    signingRegion: String
)
final case class Route53ProviderConfig(backends: List[Route53BackendConfig])
object Route53ProviderConfig {

  def load(config: Config)(implicit cs: ContextShift[IO]): IO[Route53ProviderConfig] =
    Blocker[IO].use(
      ConfigSource.fromConfig(config).loadF[IO, Route53ProviderConfig](_)
    )
}
