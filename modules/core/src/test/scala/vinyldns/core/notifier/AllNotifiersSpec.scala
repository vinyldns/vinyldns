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
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import cats.effect.IO
import org.scalatest.BeforeAndAfterEach
import cats.effect.ContextShift
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AllNotifiersSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with EitherValues
    with EitherMatchers
    with ValidatedMatchers
    with BeforeAndAfterEach {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  val mockNotifiers = List.fill(3)(mock[Notifier])

  val notification = Notification("anything")

  override def beforeEach: Unit =
    mockNotifiers.foreach { mock =>
      reset(mock)
      when(mock.notify(notification)).thenReturn(IO.unit)
    }

  "notifier" should {
    "notify all contained notifiers" in {

      val notifier = AllNotifiers(mockNotifiers)

      notifier.notify(notification)

      mockNotifiers.foreach(verify(_).notify(notification))
    }

    "suppress errors from notifiers" in {
      val notifier = AllNotifiers(mockNotifiers)

      when(mockNotifiers(2).notify(notification)).thenReturn(IO.raiseError(new Exception("fail")))

      notifier.notify(notification).unsafeRunSync()

      mockNotifiers.foreach(verify(_).notify(notification))
    }
  }

}
