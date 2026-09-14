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

import actions._
import cats.effect.{ContextShift, IO, Timer}
import com.google.inject.AbstractModule
import controllers._
import controllers.repository.{PortalDataAccessor, PortalDataAccessorProvider}
import org.slf4j.LoggerFactory
import play.api.{Configuration, Environment}
import tasks.UserSyncTask
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{UserChangeRepository, UserRepository}
import vinyldns.core.health.HealthService
import vinyldns.core.repository.DataStoreLoader
import vinyldns.core.task.{TaskRepository, TaskScheduler}

// $COVERAGE-OFF$
class VinylDNSModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  private val logger = LoggerFactory.getLogger(classOf[VinylDNSModule])
  val settings = new Settings(configuration)
  implicit val t: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  override def configure(): Unit = {
    val startApp = for {
      cryptoConf <- settings.cryptoConfig
      crypto <- CryptoAlgebra.load(cryptoConf)
      repoConfigs <- settings.dataStoreConfigs
      loaderResponse <- DataStoreLoader
        .loadAll[PortalDataAccessor](repoConfigs, crypto, PortalDataAccessorProvider)
      auth = authenticator()
      syncProvider = buildSyncProvider(auth)
      pollingInterval = resolvePollingInterval()
      healthService = new HealthService(auth.healthCheck() :: loaderResponse.healthChecks)
      repositories = loaderResponse.accessor
      userAccessor = new UserAccountAccessor(
        repositories.userRepository,
        repositories.userChangeRepository
      )
      _ <- syncProvider match {
        case NoOpUserSyncProvider => IO.unit
        case _ =>
          TaskScheduler
            .schedule(
              new UserSyncTask(userAccessor, syncProvider, pollingInterval, dryRun = settings.userSyncDryRun),
              repositories.taskRepository
            )
            .compile
            .drain
            .start
      }
    } yield {
      bind(classOf[SecuritySupport]).to(classOf[LegacySecuritySupport])
      bind(classOf[CryptoAlgebra]).toInstance(crypto)
      bind(classOf[Authenticator]).toInstance(auth)
      bind(classOf[UserRepository]).toInstance(repositories.userRepository)
      bind(classOf[UserChangeRepository]).toInstance(repositories.userChangeRepository)
      bind(classOf[TaskRepository]).toInstance(repositories.taskRepository)
      bind(classOf[HealthService]).toInstance(healthService)
    }

    startApp.unsafeRunSync()
  }

  private def buildSyncProvider(auth: Authenticator): UserSyncProvider =
    settings.userSyncProvider match {
      case "graph-api" =>
        settings.validateOidcConfig()
        new GraphApiUserSyncProvider(
          settings.oidcTenantId,
          settings.oidcClientId,
          settings.oidcSecret,
          settings.graphApiUsernameAttribute
        )
      case "ldap" =>
        new LdapUserSyncProvider(auth)
      case "none" =>
        // fall back to legacy LDAP sync config for backward compat
        if (settings.ldapSyncEnabled) new LdapUserSyncProvider(auth)
        else NoOpUserSyncProvider
      case unknown =>
        throw new IllegalArgumentException(
          s"""Unrecognized user-sync.provider="$unknown". Valid values are "ldap", "graph-api", or "none"."""
        )
    }

  private def resolvePollingInterval() =
    settings.userSyncProvider match {
      case "graph-api" | "ldap" => settings.userSyncPollingInterval
      case _ =>
        if (settings.ldapSyncEnabled) settings.ldapSyncPollingInterval
        else settings.userSyncPollingInterval
    }

  private def authenticator(): Authenticator = {
    val needsLdap = settings.userSyncProvider == "ldap" ||
      (settings.userSyncProvider == "none" && settings.ldapSyncEnabled) ||
      !settings.oidcEnabled

    if (needsLdap) {
      settings.validateLdapConfig()
      LdapAuthenticator(settings)
    } else
      new NoOpAuthenticator
  }
}
// $COVERAGE-ON$
