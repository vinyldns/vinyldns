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

package vinyldns.api.notifier.email

import vinyldns.core.notifier.{Notifier, NotifierConfig, NotifierProvider}
import vinyldns.core.domain.membership.{GroupRepository, UserRepository}
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import cats.effect.{Blocker, ContextShift, IO}

import javax.mail._

class EmailNotifierProvider extends NotifierProvider {
  import EmailNotifierConfig._

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def load(config: NotifierConfig, userRepository: UserRepository, groupRepository: GroupRepository): IO[Notifier] =
    for {
      emailConfig <- Blocker[IO].use(
        ConfigSource.fromConfig(config.settings).loadF[IO, EmailNotifierConfig](_)
      )
      session <- createSession(emailConfig)
    } yield new EmailNotifier(emailConfig, session, userRepository, groupRepository)

  def createSession(config: EmailNotifierConfig): IO[Session] = IO {
    val username = config.smtp.getProperty("mail.smtp.username")
    val password = config.smtp.getProperty("mail.smtp.password")
    val auth = config.smtp.getProperty("mail.smtp.auth")
    if (auth=="true") {
      Session.getInstance(config.smtp, new Authenticator() {
        override protected def getPasswordAuthentication: PasswordAuthentication =
          new PasswordAuthentication(username, password)
      })
    } else Session.getInstance(config.smtp)
  }
}
