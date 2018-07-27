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

package controllers.datastores

import controllers.{ChangeLogMessage, Create, UserChangeMessage}
import models.UserAccount
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class InMemoryChangeLogStoreSpec extends Specification with Mockito {
  "InMemoryChangeLogStore" should {
    "accept a message and return it upon success" in {
      val underTest = new InMemoryChangeLogStore
      val userAcc = UserAccount("foo", "bar", None, None, None, DateTime.now, "ak", "sk")
      val message = UserChangeMessage("foo", "bar", DateTime.now, Create, userAcc, None)

      val result = underTest.log(message)
      result must beASuccessfulTry[ChangeLogMessage](message)
    }
  }
}
