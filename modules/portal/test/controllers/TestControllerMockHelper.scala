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

package controllers
import cats.effect.IO
import models.TestApplicationData._
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.PlaySessionStore
import org.specs2.mock.Mockito
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, ControllerComponents, RequestHeader}
import play.api.test.Helpers.stubControllerComponents
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership._
import vinyldns.core.health.HealthService

import scala.util.Success

trait TestControllerMockHelper { this: Mockito =>

  val mockAuth: Authenticator = mock[Authenticator]
  val mockUserRepo: UserRepository = mock[UserRepository]
  val mockUserChangeRepo: UserChangeRepository = mock[UserChangeRepository]

  mockAuth.authenticate("frodo", "secondbreakfast").returns(Success(frodoDetails))
  mockUserRepo.getUser(anyString).returns(IO.pure(Some(frodoUser)))
  mockUserChangeRepo.save(any[UserChange]).returns(IO.pure(newFrodoLog))

  object MockCommonProfile extends CommonProfile {
    import scala.collection.JavaConverters._
    super.build("id", Map[String, AnyRef]("username" -> frodoUser.userName).asJava)
  }

  def buildMockPac4jScalaTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile] = {
    val accessor = mock[Pac4jScalaTemplateHelper[CommonProfile]]
    doReturn(Some(MockCommonProfile)).when(accessor).getCurrentProfile(any[RequestHeader])

    accessor
  }

  class MockedSecurityComponents() extends SecurityComponents {
    val components: ControllerComponents = stubControllerComponents()
    val config: Config = mock[Config]
    val playSessionStore: PlaySessionStore = mock[PlaySessionStore]
    val parser: BodyParsers.Default = mock[BodyParsers.Default]
  }

  val mockControllerComponents: SecurityComponents = new MockedSecurityComponents()
  implicit val pac4jScalaTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile] =
    buildMockPac4jScalaTemplateHelper

  def app: Application =
    GuiceApplicationBuilder()
      .disable[modules.VinylDNSModule]
      .bindings(
        bind[Authenticator].to(mockAuth),
        bind[UserRepository].to(mockUserRepo),
        bind[UserChangeRepository].to(mockUserChangeRepo),
        bind[CryptoAlgebra].to(new NoOpCrypto()),
        bind[HealthService].to(new HealthService(List()))
      )
      .configure(testConfigLdap)
      .build()
}
