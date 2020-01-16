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

package vinyldns.dynamodb.repository

import java.nio.ByteBuffer

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.record.{
  RecordSet,
  RecordSetChange,
  RecordSetChangeType,
  RecordSetStatus
}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.TestMembershipData.okUser
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._

class DynamoDBRecordSetConversionsSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ProtobufConversions {

  import DynamoDBRecordSetRepository._

  private val underTest = new DynamoDBRecordSetConversions {
    private[repository] val recordSetTableName: String = "testTable"
  }

  private def theRecordSetIn(item: java.util.Map[String, AttributeValue]): RecordSet =
    fromPB(VinylDNSProto.RecordSet.parseFrom(item.get(RECORD_SET_BLOB).getB.array()))

  "DynamoDBRecordSetConversions" should {
    "convert from and to item" in {
      val rs = aaaa.copy(name = "MixedCase.")
      val item = underTest.toItem(rs).asScala

      item(RECORD_SET_ID).getS shouldBe rs.id
      item(RECORD_SET_TYPE).getS shouldBe rs.typ.toString
      item(ZONE_ID).getS shouldBe rs.zoneId
      item(RECORD_SET_NAME).getS shouldBe rs.name
      item(RECORD_SET_SORT).getS shouldBe "mixedcase"

      underTest.fromItem(item.asJava) shouldBe rs
    }

    "throw an error if fromItem cannot parse" in {
      intercept[UnexpectedDynamoResponseException] {
        val item = underTest.toItem(aaaa)
        val shouldFail = "HELLO".getBytes
        val bb = ByteBuffer.allocate(shouldFail.length) //convert byte array to byte buffer
        bb.put(shouldFail)
        bb.position(0)
        item.put(RECORD_SET_BLOB, new AttributeValue().withB(bb))

        underTest.fromItem(item)
      }
    }

    "toWriteRequests" should {
      "convert a ChangeSet to Write Requests" in {
        val result = underTest.toWriteRequests(pendingChangeSet)

        result.size shouldBe pendingChangeSet.changes.size

        val put1 = result.head.getPutRequest
        val change1 = pendingChangeSet.changes.head
        put1.getItem.get(RECORD_SET_ID).getS shouldBe change1.recordSet.id
        theRecordSetIn(put1.getItem) shouldBe change1.recordSet

        val put2 = result(1).getPutRequest
        val change2 = pendingChangeSet.changes(1)
        put2.getItem.get(RECORD_SET_ID).getS shouldBe change2.recordSet.id
        theRecordSetIn(put2.getItem) shouldBe change2.recordSet
      }
    }

    "toWriteRequest" should {

      val pendingDeleteAAAA = RecordSetChange(
        zone = zoneActive,
        recordSet = aaaa.copy(
          status = RecordSetStatus.PendingDelete,
          updated = Some(DateTime.now)
        ),
        userId = okUser.id,
        changeType = RecordSetChangeType.Delete,
        updates = Some(aaaa)
      )

      "convert a failed Add Record Set change" in {
        val failedAdd = pendingCreateAAAA.failed()
        val result = underTest.toWriteRequest(failedAdd)

        Option(result.getPutRequest) shouldBe None
        val delete = result.getDeleteRequest

        delete.getKey.get(RECORD_SET_ID).getS shouldBe failedAdd.recordSet.id
      }

      "convert a failed Update Record Set change" in {
        val failedUpdate = pendingUpdateAAAA.failed()
        val result = underTest.toWriteRequest(failedUpdate)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe pendingUpdateAAAA.recordSet.id
        theRecordSetIn(put.getItem) shouldBe pendingUpdateAAAA.updates.get
      }

      "convert a failed Delete Record Set change" in {
        val failedDelete = pendingDeleteAAAA.failed()
        val result = underTest.toWriteRequest(failedDelete)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe pendingDeleteAAAA.recordSet.id
        theRecordSetIn(put.getItem) shouldBe pendingDeleteAAAA.updates.get
      }

      "convert a successful Add Record Set change" in {
        val successAdd = pendingCreateAAAA.successful
        val result = underTest.toWriteRequest(successAdd)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe successAdd.recordSet.id
        theRecordSetIn(put.getItem) shouldBe successAdd.recordSet
      }

      "convert a successful Update Record Set change" in {
        val successUpdate = pendingUpdateAAAA.successful
        val result = underTest.toWriteRequest(successUpdate)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe successUpdate.recordSet.id
        theRecordSetIn(put.getItem) shouldBe successUpdate.recordSet
      }

      "convert a successful Delete Record Set change" in {
        val successDelete = pendingDeleteAAAA.successful
        val result = underTest.toWriteRequest(successDelete)

        Option(result.getPutRequest) shouldBe None

        val delete = result.getDeleteRequest
        delete.getKey.get(RECORD_SET_ID).getS shouldBe successDelete.recordSet.id
      }

      "store a pending Add Record Set change" in {
        val result = underTest.toWriteRequest(pendingCreateAAAA)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe pendingCreateAAAA.recordSet.id
        theRecordSetIn(put.getItem) shouldBe pendingCreateAAAA.recordSet
      }

      "store a pending Update Record Set change" in {
        val result = underTest.toWriteRequest(pendingUpdateAAAA)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe pendingUpdateAAAA.recordSet.id
        theRecordSetIn(put.getItem) shouldBe pendingUpdateAAAA.recordSet
      }

      "store a pending Delete Record Set change" in {
        val result = underTest.toWriteRequest(pendingDeleteAAAA)

        Option(result.getDeleteRequest) shouldBe None

        val put = result.getPutRequest
        put.getItem.get(RECORD_SET_ID).getS shouldBe pendingDeleteAAAA.recordSet.id
        theRecordSetIn(put.getItem) shouldBe pendingDeleteAAAA.recordSet
      }
    }
  }
}
