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

package vinyldns.core.protobuf

import cats.scalatest.EitherMatchers
import org.scalatest.{EitherValues, Matchers, WordSpec}
import vinyldns.core.domain.{HighValueDomainError, SingleChangeError, ZoneDiscoveryError}
import vinyldns.core.domain.batch.{SingleAddChange, SingleChangeStatus, SingleDeleteRRSetChange}
import vinyldns.core.domain.record.{AData, RecordType}

class BatchChangeProtobufConversionsSpec
    extends WordSpec
    with Matchers
    with BatchChangeProtobufConversions
    with EitherMatchers
    with EitherValues {

  private val testDVError = SingleChangeError(ZoneDiscoveryError("some-zone-name"))

  private val testAddChange = SingleAddChange(
    Some("zoneId"),
    Some("zoneName"),
    Some("recordName"),
    "inputName",
    RecordType.A,
    100,
    AData("127.0.0.1"),
    SingleChangeStatus.Pending,
    Some("systemMessage"),
    Some("recordChangeId"),
    Some("recordSetId"),
    List(testDVError),
    "id"
  )
  private val testDeleteRRSetChangeWithoutRecordData = SingleDeleteRRSetChange(
    Some("zoneId"),
    Some("zoneName"),
    Some("recordName"),
    "inputName",
    RecordType.A,
    None,
    SingleChangeStatus.Pending,
    Some("systemMessage"),
    Some("recordChangeId"),
    Some("recordSetId"),
    List(testDVError, SingleChangeError(HighValueDomainError("hvd"))),
    "id"
  )
  private val testDeleteRRSetChangeWithRecordData = SingleDeleteRRSetChange(
    Some("zoneId"),
    Some("zoneName"),
    Some("recordName"),
    "inputName",
    RecordType.A,
    Some(AData("1.1.1.1")),
    SingleChangeStatus.Pending,
    Some("systemMessage"),
    Some("recordChangeId"),
    Some("recordSetId"),
    List(testDVError, SingleChangeError(HighValueDomainError("hvd"))),
    "id"
  )

  "BatchChangeProtobufConversions" should {
    "round trip single add changes with all values provided" in {
      val pb = toPB(testAddChange)
      val roundTrip = fromPB(pb.toOption.get)

      roundTrip shouldBe Right(testAddChange)
    }

    "round trip single delete changes with all values provided" in {
      val rrSetWithoutDataPb = toPB(testDeleteRRSetChangeWithoutRecordData)
      val rrSetWithDataPb = toPB(testDeleteRRSetChangeWithRecordData)
      val rrSetWithoutDataRoundTrip = fromPB(rrSetWithoutDataPb.toOption.get)
      val rrSetWithDataRoundTrip = fromPB(rrSetWithDataPb.toOption.get)

      rrSetWithoutDataRoundTrip shouldBe Right(testDeleteRRSetChangeWithoutRecordData)
      rrSetWithDataRoundTrip shouldBe Right(testDeleteRRSetChangeWithRecordData)
    }

    "round trip single add changes when optional values are not present" in {
      val tst = SingleAddChange(
        None,
        None,
        None,
        "testInputName",
        RecordType.A,
        100,
        AData("127.0.0.1"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List(),
        "some-id"
      )
      val pb = toPB(tst)

      val roundTrip = fromPB(pb.right.value)

      roundTrip shouldBe Right(tst)
    }

    "round trip single delete changes when optional values are not present" in {
      val tst = SingleDeleteRRSetChange(
        None,
        None,
        None,
        "testInputName",
        RecordType.A,
        None,
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List(),
        "some-id"
      )
      val pb = toPB(tst)

      val roundTrip = fromPB(pb.right.value)

      roundTrip.right.value shouldBe tst
    }

    "round trip single changes in NeedsReview state" in {
      val tst = SingleDeleteRRSetChange(
        None,
        None,
        None,
        "testInputName",
        RecordType.A,
        None,
        SingleChangeStatus.NeedsReview,
        None,
        None,
        None,
        List(),
        "some-id"
      )
      val pb = toPB(tst)

      val roundTrip = fromPB(pb.right.value)

      roundTrip.right.value shouldBe tst
    }

    "round trip single changes in Rejected state" in {
      val tst = SingleDeleteRRSetChange(
        None,
        None,
        None,
        "testInputName",
        RecordType.A,
        None,
        SingleChangeStatus.Rejected,
        None,
        None,
        None,
        List(testDVError),
        "some-id"
      )
      val pb = toPB(tst)
      val roundTrip = fromPB(pb.right.value)

      roundTrip.right.value shouldBe tst
    }
  }
}
