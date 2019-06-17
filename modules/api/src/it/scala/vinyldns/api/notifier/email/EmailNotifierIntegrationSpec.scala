package vinyldns.api.notifier.email

import com.typesafe.config.{Config, ConfigFactory}
import vinyldns.core.notifier._
import vinyldns.api.MySqlApiIntegrationSpec
import vinyldns.mysql.MySqlIntegrationSpec
import org.scalatest.{Matchers, WordSpecLike}
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.AData
import org.joda.time.DateTime
import vinyldns.core.TestMembershipData._
import java.nio.file.{Files, Path, Paths}
import cats.effect.IO
import scala.collection.JavaConverters._
import org.scalatest.BeforeAndAfterEach
import cats.implicits._

class EmailNotifierIntegrationSpec
    extends MySqlApiIntegrationSpec
    with MySqlIntegrationSpec
    with Matchers
    with WordSpecLike
    with BeforeAndAfterEach {

  import vinyldns.api.domain.DomainValidations._

  val emailConfig: Config = ConfigFactory.load().getConfig("vinyldns.email.settings")

  val targetDirectory = Paths.get("../../target")

  override def beforeEach: Unit =
    deleteEmailFiles(targetDirectory).unsafeRunSync()

  override def afterEach: Unit =
    deleteEmailFiles(targetDirectory).unsafeRunSync()

  "Email Notifier" should {

    "send an email" in {
      val batchChange = BatchChange(
        okUser.id,
        okUser.userName,
        None,
        DateTime.now,
        List(
          SingleAddChange(
            "some-zone-id",
            "zone-name",
            "record-name",
            "a" * HOST_MAX_LENGTH,
            RecordType.A,
            300,
            AData("1.1.1.1"),
            SingleChangeStatus.Complete,
            None,
            None,
            None)),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved
      )

      val program = for {
        _ <- userRepository.save(okUser)
        notifier <- new EmailNotifierProvider()
          .load(NotifierConfig("", emailConfig), userRepository)
        _ <- notifier.notify(Notification(batchChange))
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
    IO(Files.newDirectoryStream(path, "*.eml")).bracket { s =>
      IO {
        s.iterator.asScala.toList
      }
    } { s =>
      IO(s.close)
    }

}
