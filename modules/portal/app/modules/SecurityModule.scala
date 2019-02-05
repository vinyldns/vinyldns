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
import org.pac4j.play.scala.{
  DefaultSecurityComponents,
  Pac4jScalaTemplateHelper,
  SecurityComponents
}

/**
  * Guice DI module to be included in application.conf
  */
class SecurityModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  //val authEndpoint = configuration.get[String]("oidc.authorization-endpoint")
  lazy val discoveryUrl = configuration.get[String]("oidc.oidc-metadata")
  lazy val clientId = configuration.get[String]("oidc.client-id")
  lazy val baseUrl = configuration.get[String]("oidc.redirect-uri")
  lazy val secret = configuration.get[String]("oidc.secret")
  lazy val enabled = configuration.get[Boolean]("oidc.enabled")

  override def configure(): Unit =
    if (enabled) {
      bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

      val clients = new Clients(baseUrl + "/callback", oidcClient)
      val config = new Config(clients)
      config.setHttpActionAdapter(new DefaultHttpActionAdapter())
      // config.addAuthorizer("admin", new RequireAnyRoleAuthorizer[CommonProfile]("ROLE_ADMIN"))
      bind(classOf[Config]).toInstance(config)

      bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

      // callback
      val callbackController = new CallbackController()
      callbackController.setDefaultUrl("/")
      callbackController.setMultiProfile(false)
      bind(classOf[CallbackController]).toInstance(callbackController)

      // security components used in controllers
      bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
    }

  lazy val oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId(clientId)
    oidcConfiguration.setSecret(secret)
    oidcConfiguration.setDiscoveryURI(discoveryUrl)
    oidcConfiguration.setUseNonce(true)
    oidcConfiguration.setWithState(true)

    oidcConfiguration.setScope("openid profile email")
    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)

    oidcClient.setCallbackUrlResolver(new NoParameterCallbackUrlResolver())
    oidcClient.addAuthorizationGenerator { (ctx, profile) =>
      profile.addRole("ROLE_ADMIN")
      profile
    }

    oidcClient
  }
}
