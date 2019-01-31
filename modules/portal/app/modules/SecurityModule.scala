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
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import org.pac4j.core.client.Clients
import org.pac4j.oidc.client.{AzureAdClient, OidcClient}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.core.config.Config
import org.pac4j.oidc.config.{AzureAdOidcConfiguration, OidcConfiguration}
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.http.DefaultHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}

/**
  * Guice DI module to be included in application.conf
  */
class SecurityModule(environment: Environment, configuration: Configuration)
    extends AbstractModule {

  //val authEndpoint = configuration.get[String]("oidc.authorization-endpoint")
  val discoveryUrl = configuration.get[String]("oidc.oidc-metadata")
  val clientId = configuration.get[String]("oidc.client-id")
  val baseUrl = configuration.get[String]("oidc.redirect-uri")
  val secret = configuration.get[String]("oidc.secret")
  //val tenant = configuration.get[String]("oidc.tenant")
  val enabled = configuration.get[Boolean]("oidc.enabled")

  override def configure(): Unit =
    if (enabled) {
      bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

      val clients = new Clients(baseUrl + "/callback", oidcClient)
      val config = new Config(clients)
      config.setHttpActionAdapter(new DefaultHttpActionAdapter())
      bind(classOf[Config]).toInstance(config)

      // callback
      val callbackController = new CallbackController()
      callbackController.setDefaultUrl("/")

      // callbackController.setDefaultClient(oidcClient.getName)
      //callbackController.setMultiProfile(true)
      bind(classOf[CallbackController]).toInstance(callbackController)

      // logout
      val logoutController = new LogoutController()
      logoutController.setDefaultUrl("/")
      bind(classOf[LogoutController]).toInstance(logoutController)

      // security components used in controllers
      bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
    }

//  lazy val azureClient: AzureAdClient = {
//    val oidcConfiguration = new AzureAdOidcConfiguration()
//    oidcConfiguration.setClientId(clientId)
//    //oidcConfiguration.set
//    oidcConfiguration.setSecret(secret)
//
//    oidcConfiguration.setDiscoveryURI(discoveryUrl)
//    oidcConfiguration.setTenant(tenant)
//
//    new AzureAdClient(oidcConfiguration)
//  }

  lazy val oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId(clientId)
    //oidcConfiguration.set
    oidcConfiguration.setSecret(secret)

    oidcConfiguration.setDiscoveryURI(discoveryUrl)
    oidcConfiguration.setUseNonce(true)
    oidcConfiguration.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    oidcConfiguration.setResponseMode("form_post")
    //  oidcConfiguration.addCustomParam("display", "popup")
    //  oidcConfiguration.addCustomParam("prompt", "consent")
    //  oidcConfiguration.setResponseType("id_token")
//    oidcConfiguration.setResponseMode("form_post")
    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)

    // oidcClient.addAuthorizationGenerator(new RoleAdminAuthGenerator)
    oidcClient
  }
}
