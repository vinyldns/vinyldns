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

package vinyldns.api.engine

import java.util.concurrent.Executors

import cats.effect.IO
import fs2.Scheduler
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.xbill.DNS
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.domain.dns.DnsProtocol.{NoError, Refused}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{ChangeSet, RecordChangeRepository, RecordSetRepository, _}
import vinyldns.api.engine.RecordSetChangeHandler.{AlreadyApplied, Failure, ReadyToApply}
import vinyldns.api.repository.InMemoryBatchChangeRepository
import vinyldns.api.{CatsHelpers, Interfaces, VinylDNSTestData}
import vinyldns.core.domain.batch.{BatchChange, SingleAddChange, SingleChangeStatus}

import scala.concurrent.ExecutionContext

class RecordSetChangeHandlerSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with VinylDNSTestData
    with BeforeAndAfterEach
    with CatsHelpers {

  private val mockConn = mock[DnsConnection]
  private val mockRsRepo = mock[RecordSetRepository]
  private val mockChangeRepo = mock[RecordChangeRepository]
  private val mockDnsMessage = mock[DNS.Message]
  private val rsRepoCaptor = ArgumentCaptor.forClass(classOf[ChangeSet])
  private val changeRepoCaptor = ArgumentCaptor.forClass(classOf[ChangeSet])

  private val batchRepo = new InMemoryBatchChangeRepository

  private val rs = completeCreateAAAA.recordSet

  private val completeCreateAAAASingleChanges = rs.records.map { rdata =>
    SingleAddChange(
      rs.zoneId,
      "zoneName",
      rs.name,
      "fqdn",
      rs.typ,
      rs.ttl,
      rdata,
      SingleChangeStatus.Pending,
      None,
      None,
      None)
  }
  private val notUpdatedChange = SingleAddChange(
    "someId",
    "someName",
    "somerecord",
    "somerecord.zone.",
    RecordType.A,
    123,
    AData("1.1.1.1"),
    SingleChangeStatus.Pending,
    None,
    None,
    None)
  private val singleChanges = notUpdatedChange :: completeCreateAAAASingleChanges
  private val batchChange = BatchChange("userId", "userName", None, DateTime.now, singleChanges)

  private val rsChange =
    completeCreateAAAA.copy(singleBatchChangeIds = completeCreateAAAASingleChanges.map(_.id))
  private val cs = ChangeSet(rsChange)

  implicit val sched: Scheduler =
    Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(2))
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val underTest = RecordSetChangeHandler(mockRsRepo, mockChangeRepo, batchRepo)

  override protected def beforeEach(): Unit = {
    reset(mockConn, mockRsRepo, mockChangeRepo)
    batchRepo.clear()

    // seed the linked batch change in the DB
    await(batchRepo.save(batchChange))

    doReturn(IO.pure(Nil))
      .when(mockRsRepo)
      .getRecordSets(anyString, anyString, any(classOf[RecordType]))
  }

  "Handling Pending Changes" should {
    "complete the change successfully if already applied" in {
      doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "apply the change if not yet applied" in {
      // The second return is for verify
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(NoError(mockDnsMessage))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied and then verified
      verify(mockConn).applyChange(rsChange)
      verify(mockConn, times(2)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "bypass verify and fail if the dns update fails" in {
      // The second return is for verify
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(Left(Refused("dns failure")))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      // Our change should be failed
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // make sure the record was applied
      verify(mockConn).applyChange(rsChange)

      // make sure we only called resolve once when validating, ensures that verify was not called
      verify(mockConn, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage)
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "retry multiple times in verify if verify finds record does not exist" in {
      // All returns after first are for verify.  Retry 2 times and succeed
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(NoError(mockDnsMessage))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied and then verified
      verify(mockConn).applyChange(rsChange)

      // we will retry the verify 3 times based on the mock setup
      verify(mockConn, times(4)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change if retry expires" in {
      doReturn(Interfaces.result(List()))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(NoError(mockDnsMessage))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // make sure the record was applied and then verified
      verify(mockConn).applyChange(rsChange)

      // resolve called once when validating, 12x for retries
      verify(mockConn, times(13)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage)
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change in verify if verify errors" in {
      // All returns after first are for verify.  Retry 2 times and succeed
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(Left(Refused("dns-fail"))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(NoError(mockDnsMessage))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // make sure the record was applied and then verified
      verify(mockConn).applyChange(rsChange)

      // we will retry the verify 3 times based on the mock setup
      verify(mockConn, times(2)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage)
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change if validating fails with an error" in {
      // Stage an error on the first resolve, which will cause validate to fail
      doReturn(Interfaces.result(Left(Refused("dns-failure"))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // we failed in validation, so we should never issue a dns update
      verify(mockConn, never()).applyChange(rsChange)
      verify(mockConn, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage)
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change if applying fails with an error" in {
      doReturn(Interfaces.result(List()))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(Interfaces.result(Left(Refused("dns-fail")))).when(mockConn).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // we failed in apply, we should only resolve once
      verify(mockConn, times(1)).applyChange(rsChange)
      verify(mockConn, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage)
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "bypass the validate and verify steps if a wildcard record exists" in {
      // Return a wildcard record
      doReturn(IO.pure(List(rsChange.recordSet)))
        .when(mockRsRepo)
        .getRecordSets(anyString, anyString, any(classOf[RecordType]))

      // The second return is for verify
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(Right(NoError(mockDnsMessage))))
        .when(mockConn)
        .applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockConn).applyChange(rsChange)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockConn, never).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "bypass the validate and verify steps if a wildcard CNAME exists" in {
      // Return empty as the wildcard record matching the type
      doReturn(IO.pure(List.empty))
        .when(mockRsRepo)
        .getRecordSets(rsChange.recordSet.zoneId, "*", rsChange.recordSet.typ)

      // Return a wildcard matching CNAME
      doReturn(IO.pure(List(rsChange.recordSet)))
        .when(mockRsRepo)
        .getRecordSets(rsChange.recordSet.zoneId, "*", RecordType.CNAME)

      // The second return is for verify
      doReturn(Interfaces.result(List()))
        .doReturn(Interfaces.result(List(rs)))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(Interfaces.result(Right(NoError(mockDnsMessage))))
        .when(mockConn)
        .applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChange)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockConn).applyChange(rsChange)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockConn, never).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "bypass the validate and verify steps if change is ns" in {
      val rsChangeNs = completeCreateNS
      val rsNs = completeCreateNS.recordSet
      val csNs = ChangeSet(rsChangeNs)

      // Return a ns record
      doReturn(IO.pure(List(rsChangeNs.recordSet)))
        .when(mockRsRepo)
        .getRecordSets(anyString, anyString, any(classOf[RecordType]))

      // The second return is for verify
      doReturn(Interfaces.result(Right(List())))
        .doReturn(Interfaces.result(Right(List(rsNs))))
        .when(mockConn)
        .resolve(rsNs.name, rsChangeNs.zone.name, rsNs.typ)

      doReturn(Interfaces.result(Right(NoError(mockDnsMessage))))
        .when(mockConn)
        .applyChange(rsChangeNs)
      doReturn(IO.pure(csNs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(csNs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, rsChangeNs)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockConn).applyChange(rsChangeNs)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockConn, never).resolve(rsNs.name, rsChangeNs.zone.name, rsNs.typ)
    }

    "complete an update successfully if the requested record set change matches the DNS backend" in {
      val updateChange = rsChange.copy(
        changeType = RecordSetChangeType.Update,
        updates = Some(rsChange.recordSet.copy(ttl = 87)))
      doReturn(Interfaces.result(Right(List(updateChange.recordSet))))
        .when(mockConn)
        .resolve(rsChange.recordSet.name, rsChange.zone.name, rsChange.recordSet.typ)
      doReturn(Interfaces.result(Right(NoError(mockDnsMessage))))
        .when(mockConn)
        .applyChange(updateChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, updateChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      val batchChangeUpdates = await(batchRepo.getBatchChange(batchChange.id))
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id))
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail an update if current record does not match the DNS backend and the change has not already been applied" in {
      val updateChange = rsChange.copy(
        changeType = RecordSetChangeType.Update,
        updates = Some(rsChange.recordSet.copy(ttl = 87)))
      doReturn(Interfaces.result(Right(List(updateChange.recordSet.copy(ttl = 30)))))
        .when(mockConn)
        .resolve(rsChange.recordSet.name, rsChange.zone.name, rsChange.recordSet.typ)
      doReturn(Interfaces.result(Right(NoError(mockDnsMessage))))
        .when(mockConn)
        .applyChange(updateChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[ChangeSet])

      val test = underTest.apply(mockConn, updateChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(rsRepoCaptor.capture())
      verify(mockChangeRepo).save(changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      val changeSet = appliedCs.changes.head

      appliedCs.status shouldBe ChangeSetStatus.Complete
      changeSet.status shouldBe RecordSetChangeStatus.Failed
      changeSet.recordSet.status shouldBe RecordSetStatus.Inactive
      changeSet.systemMessage shouldBe Some(
        s"Failed validating update to DNS for change ${changeSet.id}:${changeSet.recordSet.name}: " +
          s"This record set is out of sync with the DNS backend; sync this zone before attempting to " +
          "update this record set.")

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
    }
  }

  "getProcessingStatus for Create" should {
    "return ReadyToApply if there are no records in the DNS backend" in {
      doReturn(Interfaces.result(Right(List())))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus =
        RecordSetChangeHandler.getProcessingStatus(rsChange, mockConn).unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if the change already exists in the DNS backend" in {
      doReturn(Interfaces.result(Right(List(rs))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus =
        RecordSetChangeHandler.getProcessingStatus(rsChange, mockConn).unsafeRunSync()
      processorStatus shouldBe an[AlreadyApplied]
    }

    "return Failure if changes exist in the DNS backend that do not match the requested change" in {
      doReturn(Interfaces.result(Right(List(rs.copy(ttl = 300)))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus =
        RecordSetChangeHandler.getProcessingStatus(rsChange, mockConn).unsafeRunSync()
      processorStatus shouldBe a[Failure]
    }
  }

  "getProcessingStatus for Update" should {
    "return ReadyToApply if change hasn't been applied and current record set matches DNS backend" in {
      val syncedRsChange =
        rsChange.copy(changeType = RecordSetChangeType.Update, updates = Some(rs.copy(ttl = 300)))
      doReturn(Interfaces.result(Right(List(syncedRsChange.updates.get))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus =
        RecordSetChangeHandler.getProcessingStatus(syncedRsChange, mockConn).unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return ReadyToApply if current record set doesn't match DNS backend and DNS backend has no records" in {
      doReturn(Interfaces.result(Right(List())))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus = RecordSetChangeHandler
        .getProcessingStatus(
          rsChange
            .copy(changeType = RecordSetChangeType.Update, updates = Some(rs.copy(ttl = 300))),
          mockConn)
        .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if the change already exists in the DNS backend" in {
      doReturn(Interfaces.result(Right(List(rsChange.recordSet))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus = RecordSetChangeHandler
        .getProcessingStatus(rsChange.copy(changeType = RecordSetChangeType.Update), mockConn)
        .unsafeRunSync()
      processorStatus shouldBe an[AlreadyApplied]
    }

    "return Failure if DNS backend changes exist and do not match current record set" in {
      doReturn(Interfaces.result(Right(List(rsChange.recordSet.copy(ttl = 300)))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus = RecordSetChangeHandler
        .getProcessingStatus(
          rsChange.copy(changeType = RecordSetChangeType.Update, updates = None),
          mockConn)
        .unsafeRunSync()
      processorStatus shouldBe a[Failure]
    }
  }

  "getProcessingStatus for Delete" should {
    "return ReadyToApply if there are records in the DNS backend" in {
      doReturn(Interfaces.result(Right(List(rs))))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus = RecordSetChangeHandler
        .getProcessingStatus(rsChange.copy(changeType = RecordSetChangeType.Delete), mockConn)
        .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if there are no records in the DNS backend" in {
      doReturn(Interfaces.result(Right(List())))
        .when(mockConn)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      val processorStatus = RecordSetChangeHandler
        .getProcessingStatus(rsChange.copy(changeType = RecordSetChangeType.Delete), mockConn)
        .unsafeRunSync()
      processorStatus shouldBe a[AlreadyApplied]
    }
  }
}
