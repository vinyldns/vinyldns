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

package vinyldns.api.domain.batch

import cats.data.NonEmptyList
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.core.domain.{DomainValidationErrorType, Fqdn, SingleChangeError, ZoneDiscoveryError}
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.{AAAAData, AData, CNAMEData}

class BatchChangeInputSpec extends AnyWordSpec with Matchers {

  "BatchChangeInput" should {
    "ensure trailing dot on A, AAAA, and CNAME fqdn" in {
      val changeA = AddChangeInput("apex.test.com", A, Some(100), AData("1.1.1.1"))
      val changeAAAA =
        AddChangeInput("aaaa.test.com", AAAA, Some(3600), AAAAData("1:2:3:4:5:6:7:8"))
      val changeCname =
        AddChangeInput("cname.test.com", CNAME, Some(100), CNAMEData(Fqdn("testing.test.com")))
      val changeADotted = AddChangeInput("adot.test.com.", A, Some(100), AData("1.1.1.1"))
      val changeAAAADotted =
        AddChangeInput("aaaadot.test.com.", AAAA, Some(3600), AAAAData("1:2:3:4:5:6:7:8"))
      val changeCnameDotted =
        AddChangeInput("cnamedot.test.com.", CNAME, Some(100), CNAMEData(Fqdn("testing.test.com.")))

      val input = BatchChangeInput(
        None,
        List(changeA, changeAAAA, changeCname, changeADotted, changeAAAADotted, changeCnameDotted),
        None
      )

      input.changes(0).inputName shouldBe "apex.test.com."
      input.changes(1).inputName shouldBe "aaaa.test.com."
      input.changes(2).inputName shouldBe "cname.test.com."
      input.changes(3).inputName shouldBe "adot.test.com."
      input.changes(4).inputName shouldBe "aaaadot.test.com."
      input.changes(5).inputName shouldBe "cnamedot.test.com."
    }
  }
  "asNewStoredChange" should {
    "Convert an AddChangeInput into SingleAddChange" in {
      val changeA = AddChangeInput("some.test.com", A, None, AData("1.1.1.1"))
      val converted = changeA.asNewStoredChange(
        NonEmptyList.of(ZoneDiscoveryError("test")),
        VinylDNSTestHelpers.defaultTtl
      )

      converted shouldBe a[SingleAddChange]
      val asAdd = converted.asInstanceOf[SingleAddChange]

      asAdd.zoneId shouldBe None
      asAdd.zoneName shouldBe None
      asAdd.recordName shouldBe None
      asAdd.inputName shouldBe "some.test.com."
      asAdd.typ shouldBe A
      asAdd.ttl shouldBe VinylDNSTestHelpers.defaultTtl
      asAdd.recordData shouldBe AData("1.1.1.1")
      asAdd.status shouldBe SingleChangeStatus.NeedsReview
      asAdd.systemMessage shouldBe None
      asAdd.recordChangeId shouldBe None
      asAdd.recordSetId shouldBe None
    }
    "Convert a DeleteChangeInput into SingleDeleteRRSetChange" in {
      val changeA = DeleteRRSetChangeInput("some.test.com", A)
      val converted = changeA.asNewStoredChange(
        NonEmptyList.of(ZoneDiscoveryError("test")),
        VinylDNSTestHelpers.defaultTtl
      )

      converted shouldBe a[SingleDeleteRRSetChange]
      val asDelete = converted.asInstanceOf[SingleDeleteRRSetChange]

      asDelete.zoneId shouldBe None
      asDelete.zoneName shouldBe None
      asDelete.recordName shouldBe None
      asDelete.inputName shouldBe "some.test.com."
      asDelete.typ shouldBe A
      asDelete.status shouldBe SingleChangeStatus.NeedsReview
      asDelete.systemMessage shouldBe None
      asDelete.recordChangeId shouldBe None
      asDelete.recordSetId shouldBe None
    }
  }
  "apply from SingleChange" should {
    "properly convert changes to adds and deletes" in {
      val singleAddChange = SingleAddChange(
        Some("testZoneId"),
        Some("testZoneName"),
        Some("testRname"),
        "testRname.testZoneName.",
        A,
        1234,
        AData("1.2.3.4"),
        SingleChangeStatus.NeedsReview,
        Some("msg"),
        None,
        None,
        List(SingleChangeError(DomainValidationErrorType.ZoneDiscoveryError, "test err"))
      )

      val expectedAddChange =
        AddChangeInput("testRname.testZoneName.", A, Some(1234), AData("1.2.3.4"))

      val singleDelChange = SingleDeleteRRSetChange(
        Some("testZoneId"),
        Some("testZoneName"),
        Some("testRname"),
        "testRname.testZoneName.",
        A,
        None,
        SingleChangeStatus.NeedsReview,
        Some("msg"),
        None,
        None,
        List(SingleChangeError(DomainValidationErrorType.ZoneDiscoveryError, "test err"))
      )

      val expectedDelChange =
        DeleteRRSetChangeInput("testRname.testZoneName.", A)

      val change = BatchChange(
        "userId",
        "userName",
        Some("comments"),
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        List(singleAddChange, singleDelChange),
        Some("owner"),
        BatchChangeApprovalStatus.PendingReview
      )

      val expectedInput =
        BatchChangeInput(
          Some("comments"),
          List(expectedAddChange, expectedDelChange),
          Some("owner")
        )

      BatchChangeInput(change) shouldBe expectedInput
    }
  }
}
