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

package vinyldns.core.notifier
import vinyldns.core.domain.membership.{GroupRepository, UserRepository}
import cats.effect.IO
import cats.implicits._
import cats.effect.ContextShift

object NotifierLoader {

  def loadAll(configs: List[NotifierConfig], userRepository: UserRepository, groupRepository: GroupRepository)(
      implicit cs: ContextShift[IO]
  ): IO[AllNotifiers] =
    for {
      notifiers <- configs.parTraverse(load(_, userRepository, groupRepository))
    } yield AllNotifiers(notifiers)

  def load(config: NotifierConfig, userRepository: UserRepository, groupRepository: GroupRepository): IO[Notifier] =
    for {
      provider <- IO(
        Class
          .forName(config.className)
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[NotifierProvider]
      )
      notifier <- provider.load(config, userRepository, groupRepository)
    } yield notifier

}
