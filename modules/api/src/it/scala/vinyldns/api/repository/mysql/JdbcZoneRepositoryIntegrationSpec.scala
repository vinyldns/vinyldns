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

package vinyldns.api.repository.mysql

import java.util.UUID

import cats.effect._
import org.scalatest._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import scalikejdbc.DB
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.membership.User
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}
import vinyldns.core.domain.zone._

class JdbcZoneRepositoryIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with DnsConversions
    with VinylDNSTestData
    with GroupTestData
    with ResultHelpers
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with Inspectors {

  private var repo: ZoneRepository = _
  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.zoneRepository

  override protected def beforeEach(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone")
    }

  private val groups = (0 until 10)
    .map(num => okGroup.copy(name = num.toString, id = UUID.randomUUID().toString))
    .toList

  // We will add the dummy acl rule to only the first zone
  private val dummyAclRule =
    ACLRule(
      accessLevel = AccessLevel.Read,
      groupId = Some(dummyGroup.id)
    )

  // generate some ACLs
  private val groupAclRules = groups.map(
    g =>
      ACLRule(
        accessLevel = AccessLevel.Read,
        groupId = Some(g.id)
    ))

  private val userOnlyAclRule =
    ACLRule(
      accessLevel = AccessLevel.Read,
      userId = Some(okUser.id)
    )

  // the zone acl rule will have the user rule and all of the group rules
  private val testZoneAcl = ZoneACL(
    rules = Set(userOnlyAclRule) ++ groupAclRules
  )

  private val testZoneAdminGroupId = "foo"

  /**
    * each zone will have an admin group id that doesn't exist, but have the ACL we generated above
    * The okUser therefore should have access to all of the zones
    */
  private val testZones = (1 until 10).map { num =>
    val z =
      okZone.copy(
        name = num.toString + ".",
        id = UUID.randomUUID().toString,
        adminGroupId = testZoneAdminGroupId,
        acl = testZoneAcl
      )

    // add the dummy acl rule to the first zone
    if (num == 1) z.addACLRule(dummyAclRule) else z
  }

  private val jdbcSuperUserAuth = AuthPrincipal(dummyUser.copy(isSuper = true), Seq())

  private def testZone(name: String, adminGroupId: String = testZoneAdminGroupId) =
    okZone.copy(name = name, id = UUID.randomUUID().toString, adminGroupId = adminGroupId)

  private def saveZones(zones: Seq[Zone]): IO[Unit] =
    zones.foldLeft(IO.unit) {
      case (acc, cur) =>
        acc.flatMap { _ =>
          repo.save(cur).map(_ => ())
        }
    }

  "JdbcZoneRepository" should {
    "return the zone when it is saved" in {
      whenReady(repo.save(okZone).unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe okZone
      }
    }

    "get a zone by id" in {
      val f =
        for {
          _ <- repo.save(okZone)
          retrieved <- repo.getZone(okZone.id)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe Some(okZone)
      }
    }

    "return none if a zone is not found by id" in {
      whenReady(repo.getZone("doesnotexist").unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe empty
      }
    }

    "get a zone by name" in {
      val f =
        for {
          _ <- repo.save(okZone)
          retrieved <- repo.getZoneByName(okZone.name)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe Some(okZone)
      }
    }

    "return none if a zone is not found by name" in {
      whenReady(repo.getZoneByName("doesnotexist").unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe empty
      }
    }

    "get a list of zones by names" in {
      val f = saveZones(testZones)
      val testZonesList1 = testZones.toList.take(3)
      val testZonesList2 = testZones.toList.takeRight(5)
      val names1 = testZonesList1.map(zone => zone.name)
      val names2 = testZonesList2.map(zone => zone.name)

      whenReady(f.unsafeToFuture(), timeout) { _ =>
        whenReady(repo.getZonesByNames(names1.toSet).unsafeToFuture(), timeout) { retrieved =>
          retrieved should contain theSameElementsAs testZonesList1
        }
        whenReady(repo.getZonesByNames(names2.toSet).unsafeToFuture(), timeout) { retrieved =>
          retrieved should contain theSameElementsAs testZonesList2
        }
      }
    }

    "return empty list if zones are not found by names" in {
      whenReady(
        repo
          .getZonesByNames(Set("doesnotexist", "doesnotexist2", "reallydoesnotexist"))
          .unsafeToFuture(),
        timeout) { retrieved =>
        retrieved shouldBe empty
      }
    }

    "get a list of reverse zones by zone names filters" in {
      val testZones = Seq(
        testZone("0/67.345.12.in-addr.arpa."),
        testZone("67.345.12.in-addr.arpa."),
        testZone("anotherZone.in-addr.arpa."),
        testZone("extraZone.in-addr.arpa.")
      )

      val expectedZones = List(testZones(0), testZones(1), testZones(3))
      val f = saveZones(testZones)

      whenReady(f.unsafeToFuture(), timeout) { _ =>
        whenReady(
          repo.getZonesByFilters(Set("67.345.12.in-addr.arpa.", "extraZone")).unsafeToFuture(),
          timeout) { retrieved =>
          retrieved should contain theSameElementsAs expectedZones
        }
      }
    }

    "get authorized zones" in {
      // store all of the zones

      val f = saveZones(testZones)

      // query for all zones for the ok user, he should have access to all of the zones
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      whenReady(f.unsafeToFuture(), timeout) { _ =>
        whenReady(repo.listZones(okUserAuth).unsafeToFuture(), timeout) { retrieved =>
          retrieved should contain theSameElementsAs testZones
        }

        // dummy user only has access to one zone
        whenReady(repo.listZones(dummyUserAuth).unsafeToFuture(), timeout) { dummyZones =>
          (dummyZones should contain).only(testZones.head)
        }
      }
    }

    "get zones that are accessible by everyone" in {

      //user and group id being set to None implies EVERYONE access
      val allAccess = okZone.copy(
        name = "all-access.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(
          rules = Set(
            ACLRule(
              accessLevel = AccessLevel.Read,
              userId = None,
              groupId = None
            )
          )
        )
      )

      val noAccess = okZone.copy(
        name = "no-access.",
        id = UUID.randomUUID().toString,
        adminGroupId = testZoneAdminGroupId,
        acl = ZoneACL()
      )

      val testZones = Seq(allAccess, noAccess)

      val f =
        for {
          saved <- saveZones(testZones)
          everyoneZones <- repo.listZones(dummyUserAuth)
        } yield everyoneZones

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        (retrieved should contain).only(allAccess)
      }
    }

    "not return deleted zones" in {
      val zoneToDelete = okZone.copy(
        name = "zone-to-delete.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(
          rules = Set(
            ACLRule(
              accessLevel = AccessLevel.Read,
              userId = None,
              groupId = None
            )
          )
        )
      )

      // save it and make sure it is saved first by immediately getting it
      val f =
        for {
          _ <- repo.save(zoneToDelete)
          retrieved <- repo.getZone(zoneToDelete.id)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { saved =>
        // delete the zone, set the status to Deleted
        val deleted = saved.map(_.copy(status = ZoneStatus.Deleted)).get
        val del =
          for {
            _ <- repo.save(deleted)
            retrieved <- repo.getZone(deleted.id)
          } yield retrieved

        // the result should be None
        whenReady(del.unsafeToFuture(), timeout) { retrieved =>
          retrieved shouldBe empty
        }
      }
    }

    "return an empty list of zones if the user is not authorized to any" in {
      val unauthorized = AuthPrincipal(
        signedInUser = User("not-authorized", "not-authorized", "not-authorized"),
        memberGroupIds = Seq.empty
      )

      val f =
        for {
          _ <- saveZones(testZones)
          zones <- repo.listZones(unauthorized)
        } yield zones

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe empty
      }
    }

    "not return zones when access is revoked" in {
      // ok user can access both zones, dummy can only access first zone
      val zones = testZones.take(2)
      val addACL = saveZones(zones)

      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      whenReady(addACL.unsafeToFuture(), timeout) { _ =>
        whenReady(repo.listZones(okUserAuth).unsafeToFuture(), timeout) { retrieved =>
          retrieved should contain theSameElementsAs zones
        }

        // dummy user only has access to first zone
        whenReady(repo.listZones(dummyUserAuth).unsafeToFuture(), timeout) { dummyZones =>
          (dummyZones should contain).only(zones.head)
        }

        // revoke the access for the dummy user
        val revoked = zones(0).deleteACLRule(dummyAclRule)
        val revokeACL = repo.save(revoked)

        whenReady(revokeACL.unsafeToFuture(), timeout) { _ =>
          // ok user can still access zones
          whenReady(repo.listZones(okUserAuth).unsafeToFuture(), timeout) { retrieved =>
            val expected = Seq(revoked, zones(1))
            retrieved should contain theSameElementsAs expected
          }

          // dummy user can not access the revoked zone
          whenReady(repo.listZones(dummyUserAuth).unsafeToFuture(), timeout) { dummyZones =>
            dummyZones shouldBe empty
          }
        }
      }
    }

    "omit zones for groups if the user has more than 30 groups" in {

      /**
        * Somewhat complex setup.  We only support 30 accessors right now, or 29 groups as the max
        * number of groups a user belongs to.
        *
        * When we query for zones, we will truncate any groups over 29.
        *
        * So the test setup here creates 40 groups along with 40 zones, where the group id
        * is the admin group of each zone.
        *
        * When we query, we should only get back 29 zones (the user id is always considered as an accessor id)
        */
      val groups = (1 to 40).map { num =>
        val groupName = "%02d".format(num)
        okGroup.copy(
          name = groupName,
          id = UUID.randomUUID().toString
        )
      }

      val zones = groups.map { group =>
        val zoneName = group.name + "."
        okZone.copy(
          name = zoneName,
          id = UUID.randomUUID().toString,
          adminGroupId = group.id,
          acl = ZoneACL()
        )
      }

      val auth = AuthPrincipal(okUser, groups.map(_.id))

      val f =
        for {
          _ <- saveZones(zones)
          retrieved <- repo.listZones(auth)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        // we should not have more than 29 zones
        retrieved.length shouldBe 29
        retrieved.headOption.map(_.name) shouldBe Some("01.")
        retrieved.lastOption.map(_.name) shouldBe Some("29.")
      }
    }

    "return all zones if the user is a super user" in {

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(jdbcSuperUserAuth)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs testZones
      }
    }

    "apply the zone filter as a super user" in {

      val testZones = Seq(
        testZone("system-test"),
        testZone("system-temp"),
        testZone("no-match")
      )

      val expectedZones = Seq(testZones(0), testZones(1))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(jdbcSuperUserAuth, zoneNameFilter = Some("system"))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs expectedZones
      }
    }

    "apply the zone filter as a normal user" in {

      val testZones = Seq(
        testZone("system-test", adminGroupId = "foo"),
        testZone("system-temp", adminGroupId = "foo"),
        testZone("system-nomatch", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0), testZones(1)).sortBy(_.name)

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("system"))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        (retrieved should contain).theSameElementsInOrderAs(expectedZones)
      }
    }

    "apply paging when searching as a super user" in {
      // we have 10 zones in test zones, let's page through and check
      val sorted = testZones.sortBy(_.name)
      val expectedFirstPage = sorted.take(4)
      val expectedSecondPage = sorted.drop(4).take(4)
      val expectedThirdPage = sorted.drop(8).take(4)

      whenReady(saveZones(testZones).unsafeToFuture(), timeout) { _ =>
        whenReady(
          repo.listZones(jdbcSuperUserAuth, offset = None, pageSize = 4).unsafeToFuture(),
          timeout) { firstPage =>
          (firstPage should contain).theSameElementsInOrderAs(expectedFirstPage)
        }

        whenReady(
          repo.listZones(jdbcSuperUserAuth, offset = Some(4), pageSize = 4).unsafeToFuture(),
          timeout) { secondPage =>
          (secondPage should contain).theSameElementsInOrderAs(expectedSecondPage)
        }

        whenReady(
          repo.listZones(jdbcSuperUserAuth, offset = Some(8), pageSize = 4).unsafeToFuture(),
          timeout) { thirdPage =>
          (thirdPage should contain).theSameElementsInOrderAs(expectedThirdPage)
        }
      }
    }

    "apply paging when doing an authorized zone search" in {
      // create 10 zones, but our user should only have access to 5 of them
      val differentAdminGroupId = UUID.randomUUID().toString

      val testZones = (0 until 10).map { num =>
        val z =
          okZone.copy(
            name = num.toString + ".",
            id = UUID.randomUUID().toString,
            adminGroupId = testZoneAdminGroupId,
            acl = ZoneACL()
          )

        // we are going to have 5 zones that havea different admin group id
        if (num % 2 == 0) z.copy(adminGroupId = differentAdminGroupId) else z
      }

      val sorted = testZones.sortBy(_.name)
      val filtered = sorted.filter(_.adminGroupId == testZoneAdminGroupId)
      val expectedFirstPage = filtered.take(2)
      val expectedSecondPage = filtered.drop(2).take(2)
      val expectedThirdPage = filtered.drop(4).take(2)

      // make sure our auth is a member of the testZoneAdminGroup
      val auth = AuthPrincipal(dummyUser, Seq(testZoneAdminGroupId))

      whenReady(saveZones(testZones).unsafeToFuture(), timeout) { _ =>
        whenReady(repo.listZones(auth, offset = None, pageSize = 2).unsafeToFuture(), timeout) {
          firstPage =>
            (firstPage should contain).theSameElementsInOrderAs(expectedFirstPage)
        }

        whenReady(repo.listZones(auth, offset = Some(2), pageSize = 2).unsafeToFuture(), timeout) {
          secondPage =>
            (secondPage should contain).theSameElementsInOrderAs(expectedSecondPage)
        }

        whenReady(repo.listZones(auth, offset = Some(4), pageSize = 2).unsafeToFuture(), timeout) {
          thirdPage =>
            (thirdPage should contain).theSameElementsInOrderAs(expectedThirdPage)
        }
      }
    }

    "get zones by admin group" in {
      val differentAdminGroupId = UUID.randomUUID().toString

      val testZones = (1 until 10).map { num =>
        val z =
          okZone.copy(
            name = num.toString + ".",
            id = UUID.randomUUID().toString,
            adminGroupId = testZoneAdminGroupId,
            acl = testZoneAcl
          )

        // we are going to have 5 zones that have a different admin group id
        if (num % 2 == 0) z.copy(adminGroupId = differentAdminGroupId) else z
      }

      val expectedZones = testZones.filter(_.adminGroupId == differentAdminGroupId)

      val f =
        for {
          _ <- saveZones(testZones)
          zones <- repo.getZonesByAdminGroupId(differentAdminGroupId)
        } yield zones

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs expectedZones
      }
    }
  }
}
