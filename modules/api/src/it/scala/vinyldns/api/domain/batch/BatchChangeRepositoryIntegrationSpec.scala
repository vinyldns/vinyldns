package vinyldns.api.domain.batch

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpecLike}
import vinyldns.api.MySqlApiIntegrationSpec
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.batch.{BatchChange, SingleAddChange, SingleChangeStatus}
import vinyldns.core.domain.record.{AData, RecordType}
import vinyldns.mysql.MySqlIntegrationSpec

class BatchChangeRepositoryIntegrationSpec
    extends MySqlApiIntegrationSpec
    with MySqlIntegrationSpec
    with Matchers
    with WordSpecLike {

  import vinyldns.api.domain.DomainValidations._

  "MySqlBatchChangeRepository" should {
    "successfully save single change with max-length input name" in {
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
            SingleChangeStatus.Pending,
            None,
            None,
            None))
      )

      val f = for {
        saveBatchChangeResult <- batchChangeRepository.save(batchChange)
        getSingleChangesResult <- batchChangeRepository.getSingleChanges(
          batchChange.changes.map(_.id))
      } yield (saveBatchChangeResult, getSingleChangesResult)
      val (saveResponse, singleChanges) = f.unsafeRunSync()

      saveResponse shouldBe batchChange
      singleChanges.foreach(_.inputName.length shouldBe HOST_MAX_LENGTH)
    }
  }
}
