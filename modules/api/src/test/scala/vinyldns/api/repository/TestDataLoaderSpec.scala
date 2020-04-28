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

package vinyldns.api.repository

import java.util.UUID

import cats.effect.IO
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Matchers._
import org.mockito.Mockito._
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone.{Zone, ZoneRepository}
import vinyldns.core.TestMembershipData._

class TestDataLoaderSpec extends AnyWordSpec with Matchers with MockitoSugar {

  val userRepo: UserRepository = mock[UserRepository]
  doReturn(IO.pure(okUser)).when(userRepo).save(any[User])
  val groupRepo: GroupRepository = mock[GroupRepository]
  doReturn(IO.pure(okGroup)).when(groupRepo).save(any[Group])
  val membershipRepo: MembershipRepository = mock[MembershipRepository]
  doReturn(IO.pure(Set()))
    .when(membershipRepo)
    .saveMembers(any[String], any[Set[String]], anyBoolean)

  "loadTestData" should {
    "succeed if filtered appropriately" in {
      val zoneRepo = mock[ZoneRepository]

      val doNotDelete = Zone("another.shared.", "email", shared = true)
      val toDelete = Set(TestDataLoader.sharedZone, TestDataLoader.nonTestSharedZone)
      val zoneResponse = toDelete + doNotDelete

      // this mock doesnt matter
      doReturn(IO.pure(Right(doNotDelete))).when(zoneRepo).save(any[Zone])

      // have zone repo return 3 zones to delete
      doReturn(IO.pure(zoneResponse)).when(zoneRepo).getZonesByFilters(any[Set[String]])

      // should filter down to 2 for this to succeed
      val out = TestDataLoader.loadTestData(userRepo, groupRepo, zoneRepo, membershipRepo)

      noException should be thrownBy out.unsafeRunSync()
    }
    "fail if more than 2 zones are filtered" in {
      val zoneRepo = mock[ZoneRepository]

      // mimic of non-test-shared, will not be filtered out
      val dataClone = TestDataLoader.nonTestSharedZone.copy(id = UUID.randomUUID().toString)
      val toDelete = Set(TestDataLoader.sharedZone, TestDataLoader.nonTestSharedZone)
      val zoneResponse = toDelete + dataClone

      // not mocking zoneRepo.save on purpose, it shouldnt be reached

      // have zone repo return 3 zones to delete
      doReturn(IO.pure(zoneResponse)).when(zoneRepo).getZonesByFilters(any[Set[String]])

      // should filters would not remove the duplicate non.test.shared
      val out = TestDataLoader.loadTestData(userRepo, groupRepo, zoneRepo, membershipRepo)

      a[RuntimeException] should be thrownBy out.unsafeRunSync()
    }
  }
}
