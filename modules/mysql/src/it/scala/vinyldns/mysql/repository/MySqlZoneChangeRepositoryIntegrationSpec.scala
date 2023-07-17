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
import cats.effect.{ContextShift, IO}
import cats.implicits._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.TestMembershipData.{dummyAuth, dummyGroup, okGroup, okUser}
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.ZoneChangeStatus.ZoneChangeStatus
import vinyldns.core.domain.zone._
import vinyldns.core.TestZoneData.{okZone, testConnection}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.TestZoneData.okZone
import vinyldns.core.TestZoneData.testConnection
import vinyldns.core.domain.Encrypted
import vinyldns.mysql.TestMySqlInstance

import scala.concurrent.duration._
import scala.util.Random

class MySqlZoneChangeRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private var repo: ZoneChangeRepository = _

  private val zoneRepo= TestMySqlInstance.zoneRepository.asInstanceOf[MySqlZoneRepository]

  object TestData {

    def randomZoneChange: ZoneChange =
      ZoneChange(
        zone = okZone,
        userId = UUID.randomUUID().toString,
        changeType = ZoneChangeType.Create,
        status = ZoneChangeStatus.Synced,
        systemMessage = Some("test")
      )

    val goodUser: User = User(s"live-test-acct", "key", Encrypted("secret"))

    val zones: IndexedSeq[Zone] = for { i <- 1 to 3 } yield Zone(
      s"${goodUser.userName}.zone$i.",
      "test@test.com",
      status = ZoneStatus.Active,
      connection = testConnection
    )

    val statuses: List[ZoneChangeStatus] = ZoneChangeStatus.Pending :: ZoneChangeStatus.Failed ::
      ZoneChangeStatus.Synced :: Nil

    val changes
        : IndexedSeq[ZoneChange] = for { zone <- zones; status <- statuses } yield ZoneChange(
      zone,
      zone.account,
      ZoneChangeType.Update,
      status,
      created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(Random.nextInt(1000))
    )

    val failedChanges
    : IndexedSeq[ZoneChange] = for { zone <- zones } yield ZoneChange(
      zone,
      zone.account,
      ZoneChangeType.Update,
      status= ZoneChangeStatus.Failed,
      created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(Random.nextInt(1000))
    )
    val successChanges
    : IndexedSeq[ZoneChange] = for { zone <- zones } yield ZoneChange(
      zone,
      zone.account,
      ZoneChangeType.Update,
      status= ZoneChangeStatus.Synced,
      created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(Random.nextInt(1000))
    )

    val groups = (11 until 20)
      .map(num => okGroup.copy(name = num.toString, id = UUID.randomUUID().toString))
      .toList

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

    val dummyAclRule =
      ACLRule(
        accessLevel = AccessLevel.Read,
        groupId = Some(dummyGroup.id)
      )

    val testZone = (11 until 20).map { num =>
      val z =
        okZone.copy(
          name = num.toString + ".",
          id = UUID.randomUUID().toString,
          adminGroupId = testZoneAdminGroupId,
          acl = testZoneAcl
        )
      // add the dummy acl rule to the first zone
      if (num == 11) z.addACLRule(dummyAclRule) else z
    }

    val deletedZoneChanges
    : IndexedSeq[ZoneChange] = for { testZone <- testZone } yield {
      ZoneChange(
      testZone.copy(status = ZoneStatus.Deleted),
      testZone.account,
      ZoneChangeType.Create,
      ZoneChangeStatus.Synced,
      created = DateTime.now().minusSeconds(Random.nextInt(1000))
    )}

    def saveZones(zones: Seq[Zone]): IO[Unit] =
      zones.foldLeft(IO.unit) {
        case (acc, z) =>
          acc.flatMap { _ =>
            zoneRepo.save(z).map(_ => ())
          }
      }

    def deleteZones(zones: Seq[Zone]): IO[Unit] =
      zones.foldLeft(IO.unit) {
        case (acc, z) =>
          acc.flatMap { _ =>
            zoneRepo.deleteTx(z).map(_ => ())
          }
      }

    def saveZoneChanges(zoneChanges: Seq[ZoneChange]): IO[Unit] =
      zoneChanges.foldLeft(IO.unit) {
        case (acc, zc) =>
          acc.flatMap { _ =>
            repo.save(zc).map(_ => ())
          }
      }
  }

  import TestData._

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.zoneChangeRepository

  override protected def beforeEach(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone_change")
    }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM zone_change")
    }
    super.afterAll()
  }

  "MySqlZoneChangeRepository" should {
    "save a change" in {
      val change = changes(1)
      // save the change
      val saveResponse = repo.save(change).unsafeRunSync()
      saveResponse should equal(change)

      // verify change in repo
      val listResponse = repo.listZoneChanges(change.zone.id).unsafeRunSync()
      listResponse.items should equal(List(change))
    }

    "update a change" in {
      val change = changes(1).copy(systemMessage = Some("original"))
      val updatedChange = change.copy(systemMessage = Some("updated"))
      // save the change
      val firstSaveResponse = repo.save(change).unsafeRunSync()
      firstSaveResponse should equal(change)

      // update change
      val updateResponse = repo.save(updatedChange).unsafeRunSync()
      updateResponse should equal(updatedChange)

      // verify change in repo
      val listResponse = repo.listZoneChanges(change.zone.id).unsafeRunSync()
      listResponse.items should equal(List(updatedChange))
    }

    "get all changes for a zone in order" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val expectedChanges =
        changes
          .filter(_.zoneId == zones(1).id)
          .sortBy(_.created.toEpochMilli)
          .reverse

      // nextId should be none since default maxItems > 3
      val listResponse = repo.listZoneChanges(zones(1).id).unsafeRunSync()
      listResponse.items should equal(expectedChanges)
      listResponse.nextId should equal(None)
      listResponse.startFrom should equal(None)
    }

    "get all failedChanges for a failed zone changes" in {
      zones.map(zoneRepo.save(_)).toList.parSequence.unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val changeSetupResults = failedChanges.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val expectedChanges =
        failedChanges.toList

      val listResponse = repo.listFailedZoneChanges().unsafeRunSync()
      listResponse should contain theSameElementsAs(expectedChanges)
    }

    "get empty list in failedChanges for a success zone changes" in {
      val changeSetupResults = successChanges.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val listResponse = repo.listFailedZoneChanges().unsafeRunSync()
      listResponse shouldBe List()
    }

    "get zone changes using a maxItems of 1" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.toEpochMilli)
        .reverse
      val expectedChanges = List(zoneOneChanges(0))
      val expectedNext = Some(zoneOneChanges(1).created.toEpochMilli.toString)

      val listResponse =
        repo.listZoneChanges(zones(1).id, startFrom = None, maxItems = 1).unsafeRunSync()
      listResponse.items.size should equal(1)
      listResponse.items should equal(expectedChanges)
      listResponse.nextId should equal(expectedNext)
      listResponse.startFrom should equal(None)
    }

    "page zone changes using a startFrom and maxItems" in {
      val changeSetupResults = changes.map(repo.save(_)).toList.parSequence
      changeSetupResults
        .unsafeRunTimed(5.minutes)
        .getOrElse(
          fail("timeout waiting for changes to save in MySqlZoneChangeRepositoryIntegrationSpec")
        )

      val zoneOneChanges = changes
        .filter(_.zoneId == zones(1).id)
        .sortBy(_.created.toEpochMilli)
        .reverse
      val expectedPageOne = List(zoneOneChanges(0))
      val expectedPageOneNext = Some(zoneOneChanges(1).created.toEpochMilli.toString)
      val expectedPageTwo = List(zoneOneChanges(1))
      val expectedPageTwoNext = Some(zoneOneChanges(2).created.toEpochMilli.toString)
      val expectedPageThree = List(zoneOneChanges(2))

      // get first page
      val pageOne =
        repo.listZoneChanges(zones(1).id, startFrom = None, maxItems = 1).unsafeRunSync()
      pageOne.items.size should equal(1)
      pageOne.items should equal(expectedPageOne)
      pageOne.nextId should equal(expectedPageOneNext)
      pageOne.startFrom should equal(None)

      // get second page
      val pageTwo =
        repo.listZoneChanges(zones(1).id, startFrom = pageOne.nextId, maxItems = 1).unsafeRunSync()
      pageTwo.items.size should equal(1)
      pageTwo.items should equal(expectedPageTwo)
      pageTwo.nextId should equal(expectedPageTwoNext)
      pageTwo.startFrom should equal(pageOne.nextId)

      // get final page
      // next id should be none now
      val pageThree =
        repo.listZoneChanges(zones(1).id, startFrom = pageTwo.nextId, maxItems = 1).unsafeRunSync()
      pageThree.items.size should equal(1)
      pageThree.items should equal(expectedPageThree)
      pageThree.nextId should equal(None)
      pageThree.startFrom should equal(pageTwo.nextId)
    }

    "get authorized zones" in {
      // store all of the zones
      saveZones(testZone).unsafeRunSync()
      // save the change
      saveZoneChanges(deletedZoneChanges).unsafeRunSync()

      // query for all zones for the ok user, he should have access to all of the zones
      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )

      repo.listDeletedZones(okUserAuth).unsafeRunSync().zoneDeleted should contain theSameElementsAs deletedZoneChanges

      // dummy user only has access to one zone
      (repo.listDeletedZones(dummyAuth).unsafeRunSync().zoneDeleted should contain).only(deletedZoneChanges.head)

      deleteZones(testZone).unsafeRunSync()


    }
    "return an empty list of zones if the user is not authorized to any" in {
      val unauthorized = AuthPrincipal(
        signedInUser = User("not-authorized", "not-authorized", "not-authorized"),
        memberGroupIds = Seq.empty
      )

      val f =
        for {
          _ <- saveZones(testZone)
          _ <- saveZoneChanges(deletedZoneChanges)
          zones <- repo.listDeletedZones(unauthorized)
        } yield zones

      f.unsafeRunSync().zoneDeleted shouldBe empty
      deleteZones(testZone).unsafeRunSync()

    }

    "not return zones when access is revoked" in {
      // ok user can access both zones, dummy can only access first zone
      val zones = testZone.take(2)
      val zoneChange = deletedZoneChanges.take(2)
      val addACL = saveZones(zones)
      val addACLZc = saveZoneChanges(zoneChange)


      val okUserAuth = AuthPrincipal(
        signedInUser = okUser,
        memberGroupIds = groups.map(_.id)
      )
      addACL.unsafeRunSync()
      addACLZc.unsafeRunSync()

      (repo.listDeletedZones(okUserAuth).unsafeRunSync().zoneDeleted should contain). allElementsOf(zoneChange)

      // dummy user only has access to first zone
      (repo.listDeletedZones(dummyAuth).unsafeRunSync().zoneDeleted should contain).only(zoneChange.head)

      // revoke the access for the dummy user
      val revoked = zones(0).deleteACLRule(dummyAclRule)
      val revokedZc = zoneChange(0).copy(zone=revoked)
      zoneRepo.save(revoked).unsafeRunSync()
      repo.save(revokedZc).unsafeRunSync()

      // ok user can still access zones
      (repo.listDeletedZones(okUserAuth).unsafeRunSync().zoneDeleted should contain).allElementsOf(Seq( zoneChange(1)))

      // dummy user can not access the revoked zone
      repo.listDeletedZones(dummyAuth).unsafeRunSync().zoneDeleted shouldBe empty
      deleteZones(testZone).unsafeRunSync()

    }


  }
}
