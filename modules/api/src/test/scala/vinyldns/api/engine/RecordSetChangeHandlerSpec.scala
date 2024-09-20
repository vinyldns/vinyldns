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

import cats.effect.{IO, Timer}
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import vinyldns.api.backend.dns.DnsProtocol.{NotAuthorized, TryAgain}
import vinyldns.api.engine.RecordSetChangeHandler.{AlreadyApplied, ReadyToApply, Requeue, Failure}
import vinyldns.api.repository.InMemoryBatchChangeRepository
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, BatchChangeRepository, SingleAddChange, SingleChangeStatus}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{ChangeSet, RecordChangeRepository, RecordSetRepository, _}
import vinyldns.core.TestRecordSetData._

import scala.concurrent.ExecutionContext
import cats.effect.ContextShift
import scalikejdbc.{ConnectionPool, DB}
import vinyldns.core.domain.backend.{Backend, BackendResponse}
import vinyldns.mysql.TransactionProvider

class RecordSetChangeHandlerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with EitherValues
    with TransactionProvider {

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private val mockBackend = mock[Backend]
  private val mockRsRepo = mock[RecordSetRepository]
  private val mockBatchChangeRepo = mock[BatchChangeRepository]
  private val mockChangeRepo = mock[RecordChangeRepository]
  private val mockRecordSetDataRepo = mock[RecordSetCacheRepository]

  private val rsRepoCaptor = ArgumentCaptor.forClass(classOf[ChangeSet])
  private val changeRepoCaptor = ArgumentCaptor.forClass(classOf[ChangeSet])

  private val batchRepo = new InMemoryBatchChangeRepository

  private val rs = completeCreateAAAA.recordSet

  private val completeCreateAAAASingleChanges = rs.records.map { rdata =>
    SingleAddChange(
      Some(rs.zoneId),
      Some("zoneName"),
      Some(rs.name),
      "fqdn",
      rs.typ,
      rs.ttl,
      rdata,
      SingleChangeStatus.Pending,
      None,
      None,
      None
    )
  }
  private val notUpdatedChange = SingleAddChange(
    Some("someId"),
    Some("someName"),
    Some("somerecord"),
    "somerecord.zone.",
    RecordType.A,
    123,
    AData("1.1.1.1"),
    SingleChangeStatus.Pending,
    None,
    None,
    None
  )
  private val singleChanges = notUpdatedChange :: completeCreateAAAASingleChanges
  private val batchChange = BatchChange(
    "userId",
    "userName",
    None,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    singleChanges,
    approvalStatus = BatchChangeApprovalStatus.AutoApproved
  )

  private val rsChange =
    completeCreateAAAA.copy(singleBatchChangeIds = completeCreateAAAASingleChanges.map(_.id))
  private val cs = ChangeSet(rsChange)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

  private val underTest =
    RecordSetChangeHandler(mockRsRepo, mockChangeRepo, mockRecordSetDataRepo,batchRepo )

  override protected def beforeEach(): Unit = {
    reset(mockBackend, mockRsRepo, mockChangeRepo)
    batchRepo.clear()

    // seed the linked batch change in the DB
    batchRepo.save(batchChange).unsafeRunSync()

    doReturn(IO.pure(Nil))
      .when(mockRsRepo)
      .getRecordSets(anyString, anyString, any(classOf[RecordType]))

  }
  // Add connection to run tests
  ConnectionPool.add('default, "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'","sa","")
  "Handling Pending Changes" should {
    "complete the change successfully if already applied" in {
      doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB],any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB],any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB],any[ChangeSet])

      doReturn(IO.pure(List(rs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          systemMessage= None,
         status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id)
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "apply the change if not yet applied" in {
      // The second return is for verify
      doReturn(IO.pure(List()))
        .doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test"))).when(mockBackend).applyChange(rsChange)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied and then verified
      verify(mockBackend).applyChange(rsChange)
      verify(mockBackend, times(2)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          systemMessage= None,
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id)
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "bypass verify and fail if the dns update fails" in {
      // The second return is for verify
      doReturn(IO.pure(List()))
        .doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.raiseError(NotAuthorized("dns failure")))
        .when(mockBackend)
        .applyChange(rsChange)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      // Our change should be failed
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // make sure the record was applied
      verify(mockBackend).applyChange(rsChange)

      // make sure we only called resolve once when validating, ensures that verify was not called
      verify(mockBackend, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change in verify if verify errors" in {
      // All returns after first are for verify.  Retry 2 times and succeed
      doReturn(IO.pure(List()))
        .doReturn(IO.raiseError(NotAuthorized("dns-fail")))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test"))).when(mockBackend).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // make sure the record was applied and then verified
      verify(mockBackend).applyChange(rsChange)

      // we will retry the verify 3 times based on the mock setup
      verify(mockBackend, times(2)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "requeue the change in verify if permissible errors" in {
      doReturn(IO.pure(List()))
        .doReturn(IO.raiseError(TryAgain("dns-fail")))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test"))).when(mockBackend).applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      a[Requeue] shouldBe thrownBy(test.unsafeRunSync())
    }

    "fail the change if validating fails with an error" in {
      // Stage an error on the first resolve, which will cause validate to fail
      doReturn(IO.raiseError(NotAuthorized("dns-failure")))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // we failed in validation, so we should never issue a dns update
      verify(mockBackend, never()).applyChange(rsChange)
      verify(mockBackend, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail the change if applying fails with an error" in {
      doReturn(IO.pure(List()))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.raiseError(NotAuthorized("dns-fail")))
        .when(mockBackend)
        .applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, rsChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Inactive

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed

      // we failed in apply, we should only resolve once
      verify(mockBackend, times(1)).applyChange(rsChange)
      verify(mockBackend, times(1)).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Failed,
          recordChangeId = Some(rsChange.id),
          systemMessage = savedCs.changes.head.systemMessage
        )
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
      doReturn(IO.pure(List()))
        .doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test")))
        .when(mockBackend)
        .applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      val test = underTest.apply(mockBackend, rsChange)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockBackend).applyChange(rsChange)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockBackend, never).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id)
        )
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
      doReturn(IO.pure(List()))
        .doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test")))
        .when(mockBackend)
        .applyChange(rsChange)
      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      val test = underTest.apply(mockBackend, rsChange)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockBackend).applyChange(rsChange)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockBackend, never).resolve(rs.name, rsChange.zone.name, rs.typ)

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id)
        )
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
      doReturn(IO.pure(List()))
        .doReturn(IO.pure(List(rsNs)))
        .when(mockBackend)
        .resolve(rsNs.name, rsChangeNs.zone.name, rsNs.typ)

      doReturn(IO.pure(BackendResponse.NoError("test")))
        .when(mockBackend)
        .applyChange(rsChangeNs)
      doReturn(IO.pure(csNs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(csNs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])

      val test = underTest.apply(mockBackend, rsChangeNs)
      val res = test.unsafeRunSync()

      res.status shouldBe RecordSetChangeStatus.Complete

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      // Our change should be successful
      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      // make sure the record was applied
      verify(mockBackend).applyChange(rsChangeNs)

      // make sure we never called resolve, as we skip validate step and verify
      verify(mockBackend, never).resolve(rsNs.name, rsChangeNs.zone.name, rsNs.typ)
    }

    "complete an update successfully if the requested record set change matches the DNS backend" in {
      val updateChange = rsChange.copy(
        changeType = RecordSetChangeType.Update,
        updates = Some(rsChange.recordSet.copy(ttl = 87))
      )
      doReturn(IO.pure(List(updateChange.recordSet)))
        .when(mockBackend)
        .resolve(rsChange.recordSet.name, rsChange.zone.name, rsChange.recordSet.typ)
      doReturn(IO.pure(BackendResponse.NoError("test")))
        .when(mockBackend)
        .applyChange(updateChange)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      doReturn(IO.pure(List(updateChange.recordSet)))
        .when(mockRsRepo)
        .getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, updateChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      appliedCs.status shouldBe ChangeSetStatus.Complete
      appliedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete
      appliedCs.changes.head.recordSet.status shouldBe RecordSetStatus.Active

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Complete

      val batchChangeUpdates = batchRepo.getBatchChange(batchChange.id).unsafeRunSync()
      val updatedSingleChanges = completeCreateAAAASingleChanges.map { ch =>
        ch.copy(
          systemMessage= None,
          status = SingleChangeStatus.Complete,
          recordChangeId = Some(rsChange.id),
          recordSetId = Some(rsChange.recordSet.id)
        )
      }
      val scExpected = notUpdatedChange :: updatedSingleChanges
      batchChangeUpdates.get.changes shouldBe scExpected
    }

    "fail an update if current record does not match the DNS backend and the change has not already been applied" in {
      val updateChange = rsChange.copy(
        changeType = RecordSetChangeType.Update,
        updates = Some(rsChange.recordSet.copy(ttl = 87))
      )
      val dnsBackendRs = updateChange.recordSet.copy(ttl = 30)
      doReturn(IO.pure(List(dnsBackendRs)))
        .when(mockBackend)
        .resolve(rsChange.recordSet.name, rsChange.zone.name, rsChange.recordSet.typ)
      doReturn(IO.pure(BackendResponse.NoError("test")))
        .when(mockBackend)
        .applyChange(updateChange)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      doReturn(IO.pure(List(dnsBackendRs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val test = underTest.apply(mockBackend, updateChange)
      test.unsafeRunSync()

      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      val appliedCs = rsRepoCaptor.getValue
      val changeSet = appliedCs.changes.head

      appliedCs.status shouldBe ChangeSetStatus.Complete
      changeSet.status shouldBe RecordSetChangeStatus.Failed
      changeSet.recordSet.status shouldBe RecordSetStatus.Inactive
      changeSet.systemMessage shouldBe Some(
        "This record set is out of sync with the DNS backend. Sync this zone before attempting to update this record set."
      )

      val savedCs = changeRepoCaptor.getValue
      savedCs.status shouldBe ChangeSetStatus.Complete
      savedCs.changes.head.status shouldBe RecordSetChangeStatus.Failed
    }
  }

  "getProcessingStatus for Create" should {
    "return ReadyToApply if there are no records in the DNS backend" in {
      doReturn(IO.pure(List()))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            rsChange,
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if the change already exists in the DNS backend" in {
      doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List(rs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            rsChange,
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()
      processorStatus shouldBe an[AlreadyApplied]
    }

    "remove record from database for Add if record does not exist in DNS backend" in {
      doReturn(IO.pure(List()))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)

      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      doReturn(IO.pure(List(rs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            rsChange,
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()


      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      verify(mockRsRepo).getRecordSetsByName(rsChange.zoneId, rs.name)
      processorStatus shouldBe a[ReadyToApply]
    }
  }

  "getProcessingStatus for Update" should {
    "return ReadyToApply if change hasn't been applied and current record set matches DNS backend" in {
      val storedRs = rs.copy(ttl = 300)
      val syncedRsChange =
        rsChange.copy(changeType = RecordSetChangeType.Update, updates = Some(storedRs))
      doReturn(IO.pure(List(syncedRsChange.updates.get)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List(storedRs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            syncedRsChange,
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return ReadyToApply if current record set doesn't match DNS backend and DNS backend has no records" in {
      doReturn(IO.pure(List()))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus = RecordSetChangeHandler
        .syncAndGetProcessingStatusFromDnsBackend(
          rsChange
            .copy(changeType = RecordSetChangeType.Update, updates = Some(rs.copy(ttl = 300))),
          mockBackend,
          mockRsRepo,
          mockChangeRepo,
          mockRecordSetDataRepo,
          true
        )
        .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if the change already exists in the DNS backend" in {
      doReturn(IO.pure(List(rsChange.recordSet)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List(rsChange.recordSet)))
        .when(mockRsRepo)
        .getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus = RecordSetChangeHandler
        .syncAndGetProcessingStatusFromDnsBackend(
          rsChange.copy(changeType = RecordSetChangeType.Update),
          mockBackend,
          mockRsRepo,
          mockChangeRepo,
          mockRecordSetDataRepo,
          true
        )
        .unsafeRunSync()
      processorStatus shouldBe an[AlreadyApplied]
    }

    "sync in the DNS backend for update if record does not exist in database" in {
      doReturn(IO.pure(List(rs.copy(ttl = 100))))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)


      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRecordSetDataRepo).save(any[DB], any[ChangeSet])

      doReturn(IO.pure(List.empty))
        .when(mockRsRepo)
        .getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            rsChange
              .copy(changeType = RecordSetChangeType.Update, updates = Some(rs.copy(ttl = 100))),
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()


      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      verify(mockRsRepo).getRecordSetsByName(rsChange.zoneId, rs.name)
      processorStatus shouldBe a[ReadyToApply]
    }
  }

  "getProcessingStatus for Delete" should {
    "return ReadyToApply if there are records in the DNS backend" in {
      doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List(rs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus = RecordSetChangeHandler
        .syncAndGetProcessingStatusFromDnsBackend(
          rsChange.copy(changeType = RecordSetChangeType.Delete),
          mockBackend,
          mockRsRepo,
          mockChangeRepo,
          mockRecordSetDataRepo,
          true
        )
        .unsafeRunSync()
      processorStatus shouldBe a[ReadyToApply]
    }

    "return AlreadyApplied if there are no records in the DNS backend" in {
      doReturn(IO.pure(List()))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List.empty)).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus = RecordSetChangeHandler
        .syncAndGetProcessingStatusFromDnsBackend(
          rsChange.copy(changeType = RecordSetChangeType.Delete),
          mockBackend,
          mockRsRepo,
          mockChangeRepo,
          mockRecordSetDataRepo,
          true
        )
        .unsafeRunSync()
      processorStatus shouldBe a[AlreadyApplied]
    }

    "return Failed if there is a record in the DNS backend but not in vinyldns, and we try to delete it " in {
      doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)
      doReturn(IO.pure(List(rs))).when(mockRsRepo).getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus = RecordSetChangeHandler
        .syncAndGetProcessingStatusFromDnsBackend(
          rsChange.copy(changeType = RecordSetChangeType.Sync),
          mockBackend,
          mockRsRepo,
          mockChangeRepo,
          mockRecordSetDataRepo,
          true
        )
        .unsafeRunSync()
      processorStatus shouldBe a[Failure]
    }

    "sync in the DNS backend for Delete change if record exists" in {
      doReturn(IO.pure(List(rs)))
        .when(mockBackend)
        .resolve(rs.name, rsChange.zone.name, rs.typ)


      doReturn(IO.pure(cs)).when(mockChangeRepo).save(any[DB], any[ChangeSet])
      doReturn(IO.pure(cs)).when(mockRsRepo).apply(any[DB], any[ChangeSet])

      doReturn(IO.pure(List.empty))
        .when(mockRsRepo)
        .getRecordSetsByName(cs.zoneId, rs.name)

      val processorStatus =
        RecordSetChangeHandler
          .syncAndGetProcessingStatusFromDnsBackend(
            rsChange
              .copy(changeType = RecordSetChangeType.Delete),
            mockBackend,
            mockRsRepo,
            mockChangeRepo,
            mockRecordSetDataRepo,
            true
          )
          .unsafeRunSync()


      verify(mockRsRepo).apply(any[DB], rsRepoCaptor.capture())
      verify(mockChangeRepo).save(any[DB], changeRepoCaptor.capture())

      verify(mockRsRepo).getRecordSetsByName(rsChange.zoneId, rs.name)
      processorStatus shouldBe a[ReadyToApply]
    }
  }
}
