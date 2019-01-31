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

import java.util.concurrent.CompletionStage

import com.google.inject.AbstractModule
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.Clients
import org.pac4j.oidc.client.OidcClient
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.http.DefaultHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import play.mvc.Result

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

  //val tokenEndpoint = configuration.get[String]("oidc.token-endpoint")

  override def configure(): Unit =
    if (enabled) {
      bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

      val clients = new Clients(baseUrl + "/callback", oidcClient)
      val config = new Config(clients)
      config.setHttpActionAdapter(new DefaultHttpActionAdapter())
      config.addAuthorizer("admin", new RequireAnyRoleAuthorizer[CommonProfile]("ROLE_ADMIN"))
      bind(classOf[Config]).toInstance(config)

      // callback
      val callbackController = new TestCallbackController()
      callbackController.setSaveInSession(false)
      //callbackController.setDefaultUrl(tokenEndpoint)
      //callbackController.setDefaultUrl()
      // callbackController.setDefaultClient(oidcClient.getName)
      // callbackController.setMultiProfile(true)
      bind(classOf[CallbackController]).toInstance(callbackController)

      // logout
      val logoutController = new LogoutController()
      logoutController.setDefaultUrl("/?defaulturlafterlogout")
      bind(classOf[LogoutController]).toInstance(logoutController)

      // security components used in controllers
      bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
    }

  lazy val oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId(clientId)
    oidcConfiguration.setSecret(secret)
    oidcConfiguration.setDiscoveryURI(discoveryUrl)
    // oidcConfiguration.setUseNonce(true)
    // oidcConfiguration.setResponseMode("form_post")
    oidcConfiguration.setScope("openid profile email")

    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](oidcConfiguration)

    oidcClient.addAuthorizationGenerator { (ctx, profile) =>
      profile.addRole("ROLE_ADMIN")
      profile
    }
    //oidcClient.addAuthorizationGenerator(new RoleAdminAuthGenerator)
    oidcClient
  }
}

class TestCallbackController extends CallbackController {
  override def callback(): CompletionStage[Result] = {
    println("IN CALLBACK")
    println(config.getCallbackLogic)
    println(config.getHttpActionAdapter)
    println(getDefaultUrl)
    println(getSaveInSession)

    //oidcClient
    /*
        private String defaultUrl;

    private Boolean saveInSession;

    private Boolean multiProfile;

    private String defaultClient;
     */
    super.callback()
  }
}
