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

package vinyldns.api.protobuf

import cats.scalatest.EitherMatchers
import org.scalatest.{EitherValues, Matchers, WordSpec}
import vinyldns.api.domain.batch.{SingleAddChange, SingleChangeStatus, SingleDeleteChange}
import vinyldns.api.domain.record.{AData, RecordType}

class BatchChangeProtobufConversionsSpec
    extends WordSpec
    with Matchers
    with BatchChangeProtobufConversions
    with EitherMatchers
    with EitherValues {

  private val testAddChange = SingleAddChange(
    "zoneId",
    "zoneName",
    "recordName",
    "inputName",
    RecordType.A,
    100,
    AData("127.0.0.1"),
    SingleChangeStatus.Pending,
    Some("systemMessage"),
    Some("recordChangeId"),
    Some("recordSetId"),
    "id"
  )
  private val testDeleteChange = SingleDeleteChange(
    "zoneId",
    "zoneName",
    "recordName",
    "inputName",
    RecordType.A,
    SingleChangeStatus.Pending,
    Some("systemMessage"),
    Some("recordChangeId"),
    Some("recordSetId"),
    "id"
  )

  "BatchChangeProtobufConversions" should {
    "round trip single add changes with all values provided" in {
      val pb = toPB(testAddChange)
      val roundTrip = fromPB(pb.asRight.value)

      roundTrip.asRight.value shouldBe testAddChange
    }

    "round trip single delete changes with all values provided" in {
      val pb = toPB(testDeleteChange)
      val roundTrip = fromPB(pb.asRight.value)

      roundTrip.asRight.value shouldBe testDeleteChange
    }

    "round trip single add changes when optional values are not present" in {
      val tst = testAddChange.copy(systemMessage = None, recordChangeId = None, recordSetId = None)
      val pb = toPB(tst)

      val roundTrip = fromPB(pb.asRight.value)

      roundTrip.right.value shouldBe tst
    }

    "round trip single delete changes when optional values are not present" in {
      val tst =
        testDeleteChange.copy(systemMessage = None, recordChangeId = None, recordSetId = None)
      val pb = toPB(tst)

      val roundTrip = fromPB(pb.right.value)

      roundTrip.right.value shouldBe tst
    }
  }
}
