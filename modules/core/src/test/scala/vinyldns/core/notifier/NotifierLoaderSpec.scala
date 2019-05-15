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

import cats.scalatest.{EitherMatchers, EitherValues, ValidatedMatchers}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.mockito._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.membership.UserRepository
import cats.effect.IO
import org.mockito.Mockito._

import scala.collection.JavaConverters._

object Mocks extends MockitoSugar {
  val mockUserRepository: UserRepository = mock[UserRepository]
}

object MockNotifierProvider extends MockitoSugar {

  val mockNotifier: Notifier = mock[Notifier]

}

class MockNotifierProvider extends NotifierProvider {

  def load(config: NotifierConfig, userRepo: UserRepository) =
    IO.pure(MockNotifierProvider.mockNotifier)
}

class FailingProvider extends NotifierProvider {

  def load(config: NotifierConfig, userRepo: UserRepository) =
    IO.raiseError(new IllegalStateException("always failing"))

}

class NotifierLoaderSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with EitherValues
    with EitherMatchers
    with ValidatedMatchers {

  val placeholderConfig: Config = ConfigFactory.parseMap(Map[String, String]().asJava)
  val goodConfig = NotifierConfig("vinyldns.core.notifier.MockNotifierProvider", placeholderConfig)

  import Mocks._
  import MockNotifierProvider._

  "loadAll" should {

    "return some notifier with no configs" in {

      val notifier = NotifierLoader.loadAll(List.empty, mockUserRepository).unsafeRunSync()

      notifier shouldNot be(null)
      notifier.notify(Notification(3)).unsafeRunSync() shouldBe (())
    }

    "return a notifier for valid config of one notifier" in {
      val notifier = NotifierLoader.loadAll(List(goodConfig), mockUserRepository).unsafeRunSync()

      notifier shouldNot be(null)

      val notification = Notification(3)

      reset(mockNotifier)

      when(mockNotifier.notify(notification)).thenReturn(IO.unit)

      notifier.notify(notification).unsafeRunSync()

      verify(mockNotifier).notify(notification)
    }

    "return a notifier for valid config of multiple notifiers" in {
      val notifier =
        NotifierLoader.loadAll(List(goodConfig, goodConfig), mockUserRepository).unsafeRunSync()

      notifier shouldNot be(null)

      val notification = Notification(3)

      reset(mockNotifier)

      when(mockNotifier.notify(notification)).thenReturn(IO.unit)

      notifier.notify(notification).unsafeRunSync()

      verify(mockNotifier, times(2)).notify(notification)

    }

    "Error if a configured provider cannot be found" in {
      val badProvider =
        NotifierConfig("vinyldns.core.notifier.NotFoundNotifierProvider", placeholderConfig)

      val load = NotifierLoader.loadAll(List(goodConfig, badProvider), mockUserRepository)

      a[ClassNotFoundException] shouldBe thrownBy(load.unsafeRunSync())
    }

    "Error if a provider throws exception" in {

      val exceptionProvider =
        NotifierConfig("vinyldns.core.notifier.FailingProvider", placeholderConfig)

      val load = NotifierLoader.loadAll(List(goodConfig, exceptionProvider), mockUserRepository)

      a[IllegalStateException] shouldBe thrownBy(load.unsafeRunSync())
    }

  }

}
