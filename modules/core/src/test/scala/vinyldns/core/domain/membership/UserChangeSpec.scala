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

package vinyldns.core.domain.membership
import cats.scalatest.EitherMatchers
import java.time.Instant
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.temporal.ChronoUnit

class UserChangeSpec extends AnyWordSpec with Matchers with EitherMatchers with EitherValues {

  private val newUser = User("foo", "key", "secret")
  private val currentDate = Instant.now.truncatedTo(ChronoUnit.MILLIS)

  "apply" should {
    "succeed for CreateUser" in {
      val result = UserChange("foo", newUser, "bar", currentDate, None, UserChangeType.Create)
      result shouldBe Right(UserChange.CreateUser(newUser, "bar", currentDate, "foo"))
    }
    "succeed for UpdateUser" in {
      val result =
        UserChange("foo", newUser, "bar", currentDate, Some(newUser), UserChangeType.Update)
      result shouldBe Right(UserChange.UpdateUser(newUser, "bar", currentDate, newUser, "foo"))
    }
    "fail for invalid parameters" in {
      val result = UserChange("foo", newUser, "bar", currentDate, None, UserChangeType.Update)
      result shouldBe left
      result.left.value shouldBe an[IllegalArgumentException]
    }
  }
}
