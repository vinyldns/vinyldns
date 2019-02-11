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

import com.google.inject.AbstractModule
import org.pac4j.core.client.Clients
import org.pac4j.oidc.client.OidcClient
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.core.config.Config
import org.pac4j.core.http.callback.NoParameterCallbackUrlResolver
import org.pac4j.core.profile.CommonProfile
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.http.DefaultHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}

/**
  * Guice DI module to be included in application.conf
  */
class SecurityModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  lazy val discoveryUrl: String = configuration.get[String]("oidc.metadata-url")
  lazy val clientId: String = configuration.get[String]("oidc.client-id")
  lazy val baseUrl: String = configuration.get[String]("oidc.redirect-uri")
  lazy val secret: String = configuration.get[String]("oidc.secret")
  lazy val scope: String = configuration.get[String]("oidc.scope")
  lazy val oidcUsernameField: String =
    configuration.getOptional[String]("oidc.jwt-username-field").getOrElse("username")

  val oidcEnabled: Boolean = configuration.getOptional[Boolean]("oidc.enabled").getOrElse(false)

  override def configure(): Unit = {
    // need to bind something, just empty module in the case this is disabled
    val clients = if (oidcEnabled) {
      new Clients(baseUrl + "/callback", oidcClient)
    } else {
      new Clients()
    }

    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

    val config = new Config(clients)
    config.setHttpActionAdapter(new DefaultHttpActionAdapter())
    bind(classOf[Config]).toInstance(config)

    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/")
    callbackController.setMultiProfile(false)
    bind(classOf[CallbackController]).toInstance(callbackController)

    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/")
    logoutController.setDestroySession(true)
    logoutController.setCentralLogout(true)
    bind(classOf[LogoutController]).toInstance(logoutController)

    // security components used in controllers
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
  }

  lazy val oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId(clientId)
    oidcConfiguration.setSecret(secret)
    oidcConfiguration.setDiscoveryURI(discoveryUrl)
    oidcConfiguration.setScope(scope)

    oidcConfiguration.setExpireSessionWithToken(true)
    oidcConfiguration.setUseNonce(true)

    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)

    oidcClient.setCallbackUrlResolver(new NoParameterCallbackUrlResolver())
    oidcClient.addAuthorizationGenerator { (_, profile) =>
      profile.addRole("USER")
      profile
    }

    oidcClient
  }
}
