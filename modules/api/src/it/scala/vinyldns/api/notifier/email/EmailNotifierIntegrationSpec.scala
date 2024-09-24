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

package vinyldns.api.notifier.email

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.notifier._
import vinyldns.api.{MySqlApiIntegrationSpec, SkipCI}
import vinyldns.mysql.MySqlIntegrationSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.{AData, OwnerShipTransferStatus, RecordSetChange, RecordSetChangeStatus, RecordSetChangeType, RecordType}
import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.core.TestMembershipData._
import java.nio.file.{Files, Path, Paths}

import cats.effect.{IO, Resource}

import scala.collection.JavaConverters._
import org.scalatest.BeforeAndAfterEach
import cats.implicits._
import vinyldns.core.TestRecordSetData.{ownerShipTransfer, rsOk}
import vinyldns.core.TestZoneData.okZone

class EmailNotifierIntegrationSpec
    extends MySqlApiIntegrationSpec
    with MySqlIntegrationSpec
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  import vinyldns.api.domain.DomainValidations._

  val emailConfig: Config = ConfigFactory.load().getConfig("vinyldns.email.settings")

  val targetDirectory = Paths.get("../../docker/email")

  override def beforeEach: Unit =
    deleteEmailFiles(targetDirectory).unsafeRunSync()

  override def afterEach: Unit =
    deleteEmailFiles(targetDirectory).unsafeRunSync()

  "Email Notifier" should {

    "send an email for batch change" taggedAs (SkipCI) in {
      val batchChange = BatchChange(
        okUser.id,
        okUser.userName,
        None,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        List(
          SingleAddChange(
            Some("some-zone-id"),
            Some("zone-name"),
            Some("record-name"),
            "a" * HOST_MAX_LENGTH,
            RecordType.A,
            300,
            AData("1.1.1.1"),
            SingleChangeStatus.Complete,
            None,
            None,
            None
          )
        ),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved
      )

      val program = for {
        _ <- userRepository.save(okUser)
        notifier <- new EmailNotifierProvider()
          .load(NotifierConfig("", emailConfig), userRepository, groupRepository)
        _ <- notifier.notify(Notification(batchChange))
        emailFiles <- retrieveEmailFiles(targetDirectory)
      } yield emailFiles

      val files = program.unsafeRunSync()

      files.length should be(1)

    }
    "send an email for recordSetChange ownerShip transfer" taggedAs (SkipCI) in {
      val recordSetChange = RecordSetChange(
        okZone,
        rsOk.copy(ownerGroupId= Some(okGroup.id),recordSetGroupChange =
          Some(ownerShipTransfer.copy(ownerShipTransferStatus = OwnerShipTransferStatus.PendingReview, requestedOwnerGroupId = Some(dummyGroup.id)))),
        "system",
        RecordSetChangeType.Create,
        RecordSetChangeStatus.Complete
      )

      val program = for {
        _ <- userRepository.save(okUser)
        notifier <- new EmailNotifierProvider()
          .load(NotifierConfig("", emailConfig), userRepository, groupRepository)
        _ <- notifier.notify(Notification(recordSetChange))
        emailFiles <- retrieveEmailFiles(targetDirectory)
      } yield emailFiles

      val files = program.unsafeRunSync()

      files.length should be(1)

    }
  }

  def deleteEmailFiles(path: Path): IO[Unit] =
    for {
      files <- retrieveEmailFiles(path)
      _ <- files.traverse { file =>
        IO(Files.delete(file))
      }
    } yield ()

  def retrieveEmailFiles(path: Path): IO[List[Path]] =
    Resource.fromAutoCloseable(IO(Files.newDirectoryStream(path, "*.eml"))).use { s =>
      IO {
        s.iterator.asScala.toList
      }
    }

}
