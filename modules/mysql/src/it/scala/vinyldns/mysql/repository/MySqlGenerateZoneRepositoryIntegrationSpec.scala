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
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.TestMembershipData.{okAuth, okGroup}
import vinyldns.core.TestZoneData.{generateBindZone, generatePdnsZone}
import vinyldns.core.domain.zone._
import vinyldns.mysql.{TestMySqlInstance, TransactionProvider}

import java.util.UUID


class MySqlGenerateZoneRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with TransactionProvider {

  private var repo: GenerateZoneRepository = _


  repo = TestMySqlInstance.generateZoneRepository

  private val testZones = (1 until 10).map { num =>
      generateBindZone.copy(
        zoneName = num.toString + ".",
        id = UUID.randomUUID().toString,
        groupId = "foo"
      )
  }

  private def saveZones(zones: Seq[GenerateZone]): IO[Unit] =
    zones.foldLeft(IO.unit) {
      case (acc, cur) =>
        acc.flatMap { _ =>
          repo.save(cur).map(_ => ())
        }
    }


  override protected def beforeEach(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM generate_zone")
    }
  for {
      _ <- saveZoneData(repo, generateBindZone)
      _ <- saveZoneData(repo, generatePdnsZone)

    } yield ()}.unsafeRunSync()

  def saveZoneData(
                     repo: GenerateZoneRepository,
                     zone: GenerateZone
                   ): IO[GenerateZone] =
    executeWithinTransaction {  _ =>
      repo.save(zone)
    }

  "MySqlGenerateZoneRepository.save" should {
    "return the generate bind zone response" in {
      repo.save(generateBindZone).unsafeRunSync() shouldBe generateBindZone
    }
    "return the generate powerDNS zone response" in {
      repo.save(generatePdnsZone).unsafeRunSync() shouldBe generatePdnsZone
    }
  }
  "MySqlGenerateZoneRepository.delete" should {
    "delete a zone" in {
      val toBeDeleted = generateBindZone
      saveZoneData(repo, toBeDeleted).unsafeRunSync() shouldBe toBeDeleted
      repo.getGenerateZoneById(toBeDeleted.id).unsafeRunSync() shouldBe Some(toBeDeleted)

      val deleted = toBeDeleted.copy(status = GenerateZoneStatus.Deleted)
      repo.delete(deleted).unsafeRunSync() shouldBe deleted
      repo.getGenerateZoneById(deleted.id).unsafeRunSync() shouldBe None
    }
  }
  "MySqlGenerateZoneRepository.getGenerateZoneByName" should {
    "retrieve a zones" in {
      repo.getGenerateZoneByName(generateBindZone.zoneName).unsafeRunSync() shouldBe Some(generateBindZone)
    }

    "returns None when zone does not exist" in {
      repo.getGenerateZoneByName("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlGenerateZoneRepository.getGenerateZoneById" should {
    "retrieve a zone" in {
      repo.getGenerateZoneById(generateBindZone.id).unsafeRunSync() shouldBe Some(generateBindZone)
    }

    "returns none when zone does not exist" in {
      repo.getGenerateZoneById("no-existo").unsafeRunSync() shouldBe None
    }
  }
  "MySqlGenerateZoneRepository.listGenerateZones" should {
    "get a list of zones" in {
      saveZones(testZones).unsafeRunSync()
      repo.listGenerateZones(okAuth).unsafeRunSync().generatedZones.head shouldBe testZones.head
    }
    "get a list of zones by name filter" in {
      saveZones(testZones).unsafeRunSync()
      repo.listGenerateZones(okAuth, zoneNameFilter=Some("1.")).unsafeRunSync().generatedZones.head shouldBe testZones.head
    }
  }
  "MySqlGenerateZoneRepository.listGeneratedZonesByAdminGroupIds" should {
    "get a list of zones" in {
      saveZones(testZones).unsafeRunSync()
      // testZones has foo as owner group id so its listing generateBindZone which has okgroup id.
      repo.listGeneratedZonesByAdminGroupIds(okAuth,adminGroupIds=Set(okGroup.id)).unsafeRunSync().generatedZones.head shouldBe generateBindZone
    }
  }
}
