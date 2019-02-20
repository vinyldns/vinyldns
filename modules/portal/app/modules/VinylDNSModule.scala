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

import com.google.inject.AbstractModule
import controllers._
import controllers.repository.{PortalDataAccessor, PortalDataAccessorProvider}
import play.api.{Configuration, Environment}
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{UserChangeRepository, UserRepository}
import vinyldns.core.health.HealthService
import vinyldns.core.repository.DataStoreLoader

// $COVERAGE-OFF$
class VinylDNSModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  val settings = new Settings(configuration)

  def configure(): Unit = {
    val startApp = for {
      cryptoConf <- settings.cryptoConfig
      crypto <- CryptoAlgebra.load(cryptoConf)
      repoConfigs <- settings.dataStoreConfigs
      loaderResponse <- DataStoreLoader
        .loadAll[PortalDataAccessor](repoConfigs, crypto, PortalDataAccessorProvider)
      auth = authenticator()
      healthService = new HealthService(auth.healthCheck() :: loaderResponse.healthChecks)
    } yield {
      val repositories = loaderResponse.accessor
      bind(classOf[CryptoAlgebra]).toInstance(crypto)
      bind(classOf[Authenticator]).toInstance(auth)
      bind(classOf[UserRepository]).toInstance(repositories.userRepository)
      bind(classOf[UserChangeRepository]).toInstance(repositories.userChangeRepository)
      bind(classOf[HealthService]).toInstance(healthService)
    }

    startApp.unsafeRunSync()
  }

  private def authenticator(): Authenticator =
    /**
      * Why not load config here you ask?  Well, there is some ugliness in the LdapAuthenticator
      * that I am not looking to undo at this time.  There are private classes
      * that do some wrapping.  It all seems to work, so I am leaving it alone
      * to complete the Play framework upgrade
      */
    LdapAuthenticator(settings)
}
// $COVERAGE-ON$
