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

package vinyldns.mysql.repository

import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito._
import vinyldns.core.TestZoneData.generateBindZoneAuthorized

class MySqlGenerateZoneRepositorySpec
    extends AnyWordSpec
    with Matchers
    {
  val repo = spy(new MySqlGenerateZoneRepository())


  "MySqlGenerateZoneRepository.save" should {
    "only call once if save is successful" in {
      doReturn(IO.pure(generateBindZoneAuthorized))
        .when(repo)
        .save(generateBindZoneAuthorized)

      val result = repo.save(generateBindZoneAuthorized).unsafeRunSync()

      verify(repo, times(1)).save(generateBindZoneAuthorized)
      result shouldEqual generateBindZoneAuthorized
    }
  }
}
