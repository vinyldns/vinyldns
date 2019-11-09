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
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.mockito.MockitoSugar
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone.{Zone, ZoneStatus}

import scala.concurrent.duration._

class MySqlZoneRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with BeforeAndAfterEach {
  val repo = spy(new MySqlZoneRepository())

  override def beforeEach(): Unit =
    reset(repo)

  "MySqlZoneRepository.save" should {
    "only call retryWithBackoff once if saveTx is successful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Active)

      doReturn(IO.pure(Right(zoneInput)))
        .when(repo)
        .saveTx(zoneInput)

      val result = repo.save(zoneInput).unsafeRunSync()
      verify(repo).save(zoneInput)
      verify(repo)
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo).saveTx(zoneInput)

      result shouldEqual Right(zoneInput)
    }

    "only retry the max amount if saveTx is unsuccessful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Active)

      doReturn(IO.raiseError(new RuntimeException()))
        .when(repo)
        .saveTx(zoneInput)

      a[RuntimeException] shouldBe thrownBy(repo.save(zoneInput).unsafeRunSync())

      verify(repo).save(zoneInput)
      // initial call + max retries
      verify(repo, times(repo.MAX_RETRIES + 1))
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo, times(repo.MAX_RETRIES + 1)).saveTx(zoneInput)
    }

    "retry until saveTx is successful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Active)

      doReturn(IO.raiseError(new RuntimeException))
        .doReturn(IO.raiseError(new RuntimeException))
        .doReturn(IO.pure(Right(zoneInput)))
        .when(repo)
        .saveTx(zoneInput)

      val result = repo.save(zoneInput).unsafeRunSync()
      verify(repo).save(zoneInput)
      verify(repo, times(3))
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo, times(3)).saveTx(zoneInput)

      result shouldEqual Right(zoneInput)
    }

    "only call retryWithBackoff once if deleteTx is successful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Deleted)

      doReturn(IO.pure(zoneInput))
        .when(repo)
        .deleteTx(zoneInput)

      val result = repo.save(zoneInput).unsafeRunSync()
      verify(repo).save(zoneInput)
      verify(repo)
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo).deleteTx(zoneInput)

      result shouldEqual Right(zoneInput)
    }

    "only retry the max amount if deleteTx is unsuccessful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Deleted)

      doReturn(IO.raiseError(new RuntimeException()))
        .when(repo)
        .deleteTx(zoneInput)

      a[RuntimeException] shouldBe thrownBy(repo.save(zoneInput).unsafeRunSync())

      verify(repo).save(zoneInput)
      // initial call + max retries
      verify(repo, times(repo.MAX_RETRIES + 1))
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo, times(repo.MAX_RETRIES + 1)).deleteTx(zoneInput)
    }

    "retry until deleteTx is successful" in {
      val zoneInput = Zone("ok.", "test@test.com", ZoneStatus.Deleted)

      doReturn(IO.raiseError(new RuntimeException))
        .doReturn(IO.raiseError(new RuntimeException))
        .doReturn(IO.pure(zoneInput))
        .when(repo)
        .deleteTx(zoneInput)

      val result = repo.save(zoneInput).unsafeRunSync()
      verify(repo).save(zoneInput)
      verify(repo, times(3))
        .retryWithBackoff[DuplicateZoneError, Zone](
          any[Zone => IO[Either[DuplicateZoneError, Zone]]](),
          any[Zone],
          any[FiniteDuration],
          any[Int]
        )
      verify(repo, times(3)).deleteTx(zoneInput)

      result shouldEqual Right(zoneInput)
    }
  }

}
