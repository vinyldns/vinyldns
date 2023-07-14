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

import java.util.UUID

import cats.effect._
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone._
import vinyldns.core.TestZoneData.okZone
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.mysql.{TestMySqlInstance, TransactionProvider}
import vinyldns.mysql.TestMySqlInstance.groupRepository

class MySqlZoneRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with TransactionProvider {

  private var repo: ZoneRepository = _

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
      )
  )

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

  private def testZone(name: String, adminGroupId: String = testZoneAdminGroupId) =
    okZone.copy(name = name, id = UUID.randomUUID().toString, adminGroupId = adminGroupId)

  private def saveZones(zones: Seq[Zone]): IO[Unit] =
    zones.foldLeft(IO.unit) {
      case (acc, cur) =>
        acc.flatMap { _ =>
          repo.save(cur).map(_ => ())
        }
    }

  "MySqlZoneRepository" should {
    "return the zone when it is saved" in {
      repo.save(okZone).unsafeRunSync() shouldBe Right(okZone)
    }

    "return the updated zone when it is saved with new values" in {
      repo.save(okZone).unsafeRunSync() shouldBe Right(okZone)
      val updatedZone = okZone.copy(adminGroupId = "newestGroup")
      repo.save(updatedZone).unsafeRunSync() shouldBe Right(updatedZone)

      repo.getZoneByName(updatedZone.name).unsafeRunSync().get.adminGroupId shouldBe "newestGroup"
    }

    "return an error when attempting to save a duplicate zone" in {
      repo.save(okZone).unsafeRunSync() shouldBe Right(okZone)
      repo.save(okZone.copy(id = "newId")).unsafeRunSync() shouldBe
        Left(DuplicateZoneError("ok.zone.recordsets."))
    }

    "get a zone by id" in {
      val f =
        for {
          _ <- repo.save(okZone)
          retrieved <- repo.getZone(okZone.id)
        } yield retrieved

      f.unsafeRunSync() shouldBe Some(okZone)
    }

    "return none if a zone is not found by id" in {
      repo.getZone("doesnotexist").unsafeRunSync() shouldBe empty
    }

    "get a zone by name" in {
      val f =
        for {
          _ <- repo.save(okZone)
          retrieved <- repo.getZoneByName(okZone.name)
        } yield retrieved

      f.unsafeRunSync() shouldBe Some(okZone)
    }

    "return none if a zone is not found by name" in {
      repo.getZoneByName("doesnotexist").unsafeRunSync() shouldBe empty
    }

    "get a list of zones by set of IDs" in {
      val zone = testZone("test1.")
      val testZones = Seq(
        zone
      )
      saveZones(testZones).unsafeRunSync()

      repo.getZones(Set(zone.id)).unsafeRunSync() should contain theSameElementsAs List(zone)
    }

    "get a list of zones by names" in {
      saveZones(testZones).unsafeRunSync()
      val testZonesList1 = testZones.toList.take(3)
      val testZonesList2 = testZones.toList.takeRight(5)
      val names1 = testZonesList1.map(zone => zone.name)
      val names2 = testZonesList2.map(zone => zone.name)

      repo
        .getZonesByNames(names1.toSet)
        .unsafeRunSync() should contain theSameElementsAs testZonesList1
      repo
        .getZonesByNames(names2.toSet)
        .unsafeRunSync() should contain theSameElementsAs testZonesList2
    }

    "return empty list if zones are not found by names" in {
      repo
        .getZonesByNames(Set("doesnotexist", "doesnotexist2", "reallydoesnotexist"))
        .unsafeRunSync() shouldBe empty
    }

    "get a list of reverse zones by zone names filters" in {
      val testZones = Seq(
        testZone("0/67.345.12.in-addr.arpa."),
        testZone("67.345.12.in-addr.arpa."),
        testZone("anotherZone.in-addr.arpa."),
        testZone("extraZone.in-addr.arpa.")
      )

      val expectedZones = List(testZones(0), testZones(1), testZones(3))
      saveZones(testZones).unsafeRunSync()

      repo
        .getZonesByFilters(Set("67.345.12.in-addr.arpa.", "extraZone"))
        .unsafeRunSync() should contain theSameElementsAs expectedZones
    }

    "get authorized zones" in {
      // store all of the zones

      val f = saveZones(testZones)

      // query for all zones for the ok user, he should have access to all of the zones
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()
      repo.listZones(okUserAuth).unsafeRunSync().zones should contain theSameElementsAs testZones

      // dummy user only has access to one zone
      (repo.listZones(dummyAuth).unsafeRunSync().zones should contain).only(testZones.head)
    }

    "get authorized zone by admin group name" in {

      executeWithinTransaction { db: DB =>
        groupRepository.save(db, okGroup.copy(id = testZoneAdminGroupId))
      }.unsafeRunSync()

      // store all of the zones

      val f = saveZones(testZones)

      // query for all zones for the ok user, he should have access to all of the zones
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()
      repo.listZonesByAdminGroupIds(okUserAuth, None, 100, Set(testZoneAdminGroupId)).unsafeRunSync().zones should contain theSameElementsAs testZones

      // dummy user only has access to one zone
      (repo.listZonesByAdminGroupIds(dummyAuth, None, 100, Set(testZoneAdminGroupId)).unsafeRunSync().zones should contain).only(testZones.head)

      // delete the group created to test
      groupRepository.delete(okGroup).unsafeRunSync()
    }

    "get all zones" in {
      // store all of the zones
      val privateZone = okZone.copy(
        name = "private-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL()
      )

      val sharedZone = okZone.copy(
        name = "shared-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(),
        shared = true
      )

      val testZones = Seq(privateZone, sharedZone)

      val f = saveZones(testZones)

      // query for all zones for the ok user, should have all of the zones returned
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()
      repo
        .listZones(okUserAuth, ignoreAccess = true)
        .unsafeRunSync()
        .zones should contain theSameElementsAs testZones

      // dummy user only have all of the zones returned
      repo
        .listZones(dummyAuth, ignoreAccess = true)
        .unsafeRunSync()
        .zones should contain theSameElementsAs testZones
    }

    "get all zones by admin group name" in {

      executeWithinTransaction { db: DB =>
        groupRepository.save(db, okGroup)
      }.unsafeRunSync()

      val group = groupRepository.getGroupsByName(okGroup.name).unsafeRunSync()
      val groupId = group.head.id

      // store all of the zones
      val privateZone = okZone.copy(
        name = "private-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(),
        adminGroupId = groupId
      )

      val sharedZone = okZone.copy(
        name = "shared-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(),
        shared = true,
        adminGroupId = groupId
      )

      val testZones = Seq(privateZone, sharedZone)

      val f = saveZones(testZones)

      // query for all zones for the ok user, should have all of the zones returned
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()

      repo
        .listZonesByAdminGroupIds(okUserAuth, None, 100, Set(groupId), ignoreAccess = true)
        .unsafeRunSync()
        .zones should contain theSameElementsAs testZones

      // dummy user only have all of the zones returned
      repo
        .listZonesByAdminGroupIds(dummyAuth, None, 100, Set(groupId), ignoreAccess = true)
        .unsafeRunSync()
        .zones should contain theSameElementsAs testZones


      // delete the group created to test
      groupRepository.delete(okGroup).unsafeRunSync()
    }

    "check pagination while filtering zones by admin group" in {

      executeWithinTransaction { db: DB =>
        groupRepository.save(db, okGroup)
      }.unsafeRunSync()

      val group = groupRepository.getGroupsByName(okGroup.name).unsafeRunSync()
      val groupId = group.head.id

      // store all of the zones
      val privateZone = okZone.copy(
        name = "private-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(),
        adminGroupId = groupId
      )

      val sharedZone = okZone.copy(
        name = "shared-zone.",
        id = UUID.randomUUID().toString,
        acl = ZoneACL(),
        shared = true,
        adminGroupId = groupId
      )

      val testZones = Seq(privateZone, sharedZone)

      val f = saveZones(testZones)

      // query for all zones for the ok user, should have all of the zones returned
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()

      val page1 = repo
        .listZonesByAdminGroupIds(okUserAuth, None, 1, Set(groupId), ignoreAccess = true)
        .unsafeRunSync()
      page1.zones.head shouldBe testZones.head

      val page2 = repo
        .listZonesByAdminGroupIds(okUserAuth, page1.nextId, 1, Set(groupId), ignoreAccess = true)
        .unsafeRunSync()
      page2.zones.head shouldBe testZones.last

      // delete the group created to test
      groupRepository.delete(okGroup).unsafeRunSync()
    }

    "get empty list when no matching admin group name is found while filtering zones by group name" in {

      executeWithinTransaction { db: DB =>
        groupRepository.save(db, okGroup.copy(id = testZoneAdminGroupId))
      }.unsafeRunSync()

      // store all of the zones

      val f = saveZones(testZones)

      // query for all zones for the ok user, he should have access to all of the zones
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      f.unsafeRunSync()
      repo.listZonesByAdminGroupIds(okUserAuth, None, 100, Set()).unsafeRunSync().zones shouldBe empty

      // delete the group created to test
      groupRepository.delete(okGroup).unsafeRunSync()
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
          _ <- saveZones(testZones)
          everyoneZones <- repo.listZones(dummyAuth)
        } yield everyoneZones

      (f.unsafeRunSync().zones should contain).only(allAccess)
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

      val saved = f.unsafeRunSync()

      val deleted = saved.map(_.copy(status = ZoneStatus.Deleted)).get
      val del =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getZone(deleted.id)
        } yield retrieved

      // the result should be None
      del.unsafeRunSync() shouldBe empty
    }

    "return an empty list of zones if the user is not authorized to any" in {
      val unauthorized = AuthPrincipal(
        signedInUser = User("not-authorized", "not-authorized", Encrypted("not-authorized")),
        memberGroupIds = Seq.empty
      )

      val f =
        for {
          _ <- saveZones(testZones)
          zones <- repo.listZones(unauthorized)
        } yield zones

      f.unsafeRunSync().zones shouldBe empty
    }

    "not return zones when access is revoked" in {
      // ok user can access both zones, dummy can only access first zone
      val zones = testZones.take(2)
      val addACL = saveZones(zones)

      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )
      addACL.unsafeRunSync()

      (repo.listZones(okUserAuth).unsafeRunSync().zones should contain).allElementsOf(zones)

      // dummy user only has access to first zone
      (repo.listZones(dummyAuth).unsafeRunSync().zones should contain).only(zones.head)

      // revoke the access for the dummy user
      val revoked = zones(0).deleteACLRule(dummyAclRule)
      repo.save(revoked).unsafeRunSync()

      // ok user can still access zones
      (repo.listZones(okUserAuth).unsafeRunSync().zones should contain)
        .allElementsOf(Seq(revoked, zones(1)))

      // dummy user can not access the revoked zone
      repo.listZones(dummyAuth).unsafeRunSync().zones shouldBe empty
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

      val retrieved = f.unsafeRunSync().zones
      retrieved.length shouldBe 29
      retrieved.headOption.map(_.name) shouldBe Some("01.")
      retrieved.lastOption.map(_.name) shouldBe Some("29.")
    }

    "return all zones if the user is a super user" in {

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(superUserAuth)
        } yield retrieved

      f.unsafeRunSync().zones should contain theSameElementsAs testZones
    }

    "apply the zone filter as a super user" in {

      val testZones = Seq(
        testZone("system-test."),
        testZone("system-temp."),
        testZone("no-match.")
      )

      val expectedZones = Seq(testZones(0), testZones(1))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(superUserAuth, zoneNameFilter = Some("system*"))
        } yield retrieved

      f.unsafeRunSync().zones should contain theSameElementsAs expectedZones
    }

    "apply the zone filter as a normal user" in {

      val testZones = Seq(
        testZone("system-test.", adminGroupId = "foo"),
        testZone("system-temp.", adminGroupId = "foo"),
        testZone("system-nomatch.", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0), testZones(1)).sortBy(_.name)

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("system*"))
        } yield retrieved

      (f.unsafeRunSync().zones should contain).theSameElementsInOrderAs(expectedZones)
    }

    "support case insensitivity in the zone filter" in {

      val testZones = Seq(
        testZone("system-test.", adminGroupId = "foo"),
        testZone("system-temp.", adminGroupId = "foo"),
        testZone("system-nomatch.", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0), testZones(1)).sortBy(_.name)

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("SyStEm*"))
        } yield retrieved

      (f.unsafeRunSync().zones should contain).theSameElementsInOrderAs(expectedZones)
    }

    "support starts with wildcard" in {

      val testZones = Seq(
        testZone("system-test.", adminGroupId = "foo"),
        testZone("system-temp.", adminGroupId = "foo"),
        testZone("system-nomatch.", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0), testZones(1)).sortBy(_.name)

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("system*"))
        } yield retrieved

      (f.unsafeRunSync().zones should contain).theSameElementsInOrderAs(expectedZones)
    }

    "support ends with wildcard" in {

      val testZones = Seq(
        testZone("system-test.", adminGroupId = "foo"),
        testZone("system-temp.", adminGroupId = "foo"),
        testZone("system-nomatch.", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0))

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("*test"))
        } yield retrieved

      (f.unsafeRunSync().zones should contain).theSameElementsInOrderAs(expectedZones)
    }

    "support contains wildcard" in {
      val testZones = Seq(
        testZone("system-jokerswild.", adminGroupId = "foo"),
        testZone("system-wildcard.", adminGroupId = "foo"),
        testZone("system-nomatch.", adminGroupId = "bar")
      )

      val expectedZones = Seq(testZones(0), testZones(1))

      val auth = AuthPrincipal(dummyUser, Seq("foo"))

      val f =
        for {
          _ <- saveZones(testZones)
          retrieved <- repo.listZones(auth, zoneNameFilter = Some("*wild*"))
        } yield retrieved

      (f.unsafeRunSync().zones should contain).theSameElementsInOrderAs(expectedZones)
    }

    "apply paging when searching as a super user" in {
      // we have 10 zones in test zones, let's page through and check
      val sorted = testZones.sortBy(_.name)
      val expectedFirstPage = sorted.take(4)
      val expectedSecondPage = sorted.slice(4, 8)
      val expectedThirdPage = sorted.slice(8, 12)

      saveZones(testZones).unsafeRunSync()

      (repo
        .listZones(superUserAuth, None, None, 4)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedFirstPage)

      (repo
        .listZones(superUserAuth, None, Some(sorted(3).name), 4)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedSecondPage)

      (repo
        .listZones(superUserAuth, None, Some(sorted(7).name), 4)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedThirdPage)

      repo
        .listZones(superUserAuth, None, Some(sorted(7).name), 4)
        .unsafeRunSync()
        .nextId shouldBe None
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

        // we are going to have 5 zones that have a different admin group id
        if (num % 2 == 0) z.copy(adminGroupId = differentAdminGroupId) else z
      }

      val sorted = testZones.sortBy(_.name)
      val filtered = sorted.filter(_.adminGroupId == testZoneAdminGroupId)
      val expectedFirstPage = filtered.take(2)
      val expectedSecondPage = filtered.slice(2, 4)
      val expectedThirdPage = filtered.slice(4, 6)

      // make sure our auth is a member of the testZoneAdminGroup
      val auth = AuthPrincipal(dummyUser, Seq(testZoneAdminGroupId))

      saveZones(testZones).unsafeRunSync()

      (repo
        .listZones(auth, None, None, 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedFirstPage)

      (repo
        .listZones(auth, None, Some(filtered(1).name), 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedSecondPage)

      (repo
        .listZones(auth, None, Some(filtered(3).name), 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedThirdPage)
    }

    "apply paging when doing a zone search as a zone admin with ACL rules" in {
      // create 10 zones, but our user should only have access to 5 of them
      val differentAdminGroupId = UUID.randomUUID().toString

      val testZones = (0 until 10).map { num =>
        val z =
          okZone.copy(
            name = num.toString + ".",
            id = UUID.randomUUID().toString,
            adminGroupId = testZoneAdminGroupId,
            acl = ZoneACL(
              rules = Set(
                ACLRule(
                  accessLevel = AccessLevel.Read,
                  userId = Some(dummyUser.id),
                  groupId = None
                )
              )
            )
          )

        // we are going to have 5 zones that have a different admin group id
        if (num % 2 == 0) z.copy(adminGroupId = differentAdminGroupId, acl = ZoneACL()) else z
      }

      val sorted = testZones.sortBy(_.name)
      val filtered = sorted.filter(_.adminGroupId == testZoneAdminGroupId)
      val expectedFirstPage = filtered.take(2)
      val expectedSecondPage = filtered.slice(2, 4)
      val expectedThirdPage = filtered.slice(4, 6)

      // make sure our auth is a member of the testZoneAdminGroup
      val auth = AuthPrincipal(dummyUser, Seq(testZoneAdminGroupId))

      saveZones(testZones).unsafeRunSync()

      (repo
        .listZones(auth, None, None, 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedFirstPage)

      (repo
        .listZones(auth, None, Some(filtered(1).name), 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedSecondPage)

      (repo
        .listZones(auth, None, Some(filtered(3).name), 2)
        .unsafeRunSync()
        .zones should contain).theSameElementsInOrderAs(expectedThirdPage)
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

      f.unsafeRunSync() should contain theSameElementsAs expectedZones
    }

    "check if an id has an ACL rule for at least one of the zones" in {

      val zoneId = UUID.randomUUID().toString

      val testZones = (1 until 3).map { num =>
        okZone.copy(
          name = num.toString + ".",
          id = zoneId,
          adminGroupId = testZoneAdminGroupId,
          acl = testZoneAcl
        )
      }

      val f =
        for {
          _ <- saveZones(testZones)
          zones <- repo.getFirstOwnedZoneAclGroupId(testZoneAdminGroupId)
        } yield zones

      f.unsafeRunSync() shouldBe Some(zoneId)
    }

    "return None when group id is not in any ACL rule" in {
      val testZones = (1 until 3).map { num =>
        okZone.copy(
          name = num.toString + ".",
          id = UUID.randomUUID().toString,
          adminGroupId = testZoneAdminGroupId,
          acl = testZoneAcl
        )
      }

      val f =
        for {
          _ <- saveZones(testZones)
          zones <- repo.getFirstOwnedZoneAclGroupId(UUID.randomUUID().toString + "dummy")
        } yield zones

      f.unsafeRunSync() shouldBe None
    }

    "return zones which have zone sync scheduled" in {
      // okZone with recurrence schedule
      repo.save(okZone).unsafeRunSync() shouldBe Right(okZone)
      val updatedOkZone = okZone.copy(recurrenceSchedule = Some("0/5 0 0 ? * * *"))
      repo.save(updatedOkZone).unsafeRunSync() shouldBe Right(updatedOkZone)
      repo.getZoneByName(updatedOkZone.name).unsafeRunSync().get.recurrenceSchedule shouldBe Some("0/5 0 0 ? * * *")

      // dummyZone without recurrence schedule
      val dummyZone = okZone.copy(name = "dummy.", id = "5615c19c-cb00-4734-9acd-fbfdca0e6fce")
      repo.save(dummyZone).unsafeRunSync() shouldBe Right(dummyZone)
      repo.getZoneByName(dummyZone.name).unsafeRunSync().get.recurrenceSchedule shouldBe None

      // Only get zone with recurrence schedule
      repo.getAllZonesWithSyncSchedule.unsafeRunSync() shouldBe Set(updatedOkZone)
    }
  }
}
