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

import com.amazonaws.services.dynamodbv2.model._
import org.slf4j.LoggerFactory
import vinyldns.core.domain.DomainHelpers.omitTrailingDot
import vinyldns.core.domain.record._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.proto.VinylDNSProto

trait DynamoDBRecordSetConversions extends ProtobufConversions {

  import DynamoDBRecordSetRepository._

  private[repository] val recordSetTableName: String

  private val logger = LoggerFactory.getLogger(classOf[DynamoDBRecordSetConversions])

  def toWriteRequest(recordSetChange: RecordSetChange): WriteRequest = recordSetChange match {
    case failed if recordSetChange.status == RecordSetChangeStatus.Failed =>
      unsuccessful(failed)
    case complete if recordSetChange.status == RecordSetChangeStatus.Complete =>
      successful(complete)
    case notComplete => saveRecordSet(notComplete)
  }

  def toWriteRequest(recordSet: RecordSet): WriteRequest = saveRecordSet(recordSet)

  def toWriteRequests(changeSet: ChangeSet): Seq[WriteRequest] =
    changeSet.changes.map(toWriteRequest)

  def toWriteRequests(recordSets: List[RecordSet]): Seq[WriteRequest] =
    recordSets.map(toWriteRequest)

  private[repository] def deleteRecordSetFromTable(recordSetId: String): WriteRequest =
    new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(recordSetIdKey(recordSetId)))

  /* simply writes the record set in the table, if it already exists it will be overridden (which is ok) */
  private def saveRecordSet(change: RecordSetChange): WriteRequest =
    putRecordSetInTable(toItem(change.recordSet))

  private def saveRecordSet(recordSet: RecordSet): WriteRequest =
    putRecordSetInTable(toItem(recordSet))

  private def successful(change: RecordSetChange): WriteRequest = change.changeType match {
    case RecordSetChangeType.Delete => applySuccessfulDelete(change)
    case _ => applySuccessfulUpdateOrCreate(change)
  }

  private def unsuccessful(change: RecordSetChange): WriteRequest = change.changeType match {
    case RecordSetChangeType.Create => revertCreate(change)
    case _ => revertUpdateOrDelete(change)
  }

  /* reverts a failed change by restoring the record set change's "updates" attribute */
  private def revertUpdateOrDelete(failedChange: RecordSetChange): WriteRequest =
    putRecordSetInTable(failedChange.updates.map(toItem).get)

  /* reverts a failed create by deleting it from the table */
  private def revertCreate(failedChange: RecordSetChange): WriteRequest =
    deleteRecordSetFromTable(failedChange.recordSet.id)

  /* applies a successful change by putting the record set itself */
  private def applySuccessfulUpdateOrCreate(successfulChange: RecordSetChange): WriteRequest =
    putRecordSetInTable(toItem(successfulChange.recordSet))

  /* successful deletes get removed from the record set table via a delete request */
  private def applySuccessfulDelete(delete: RecordSetChange): WriteRequest =
    deleteRecordSetFromTable(delete.recordSet.id)

  private def putRecordSetInTable(item: java.util.HashMap[String, AttributeValue]): WriteRequest =
    new WriteRequest().withPutRequest(new PutRequest().withItem(item))

  private def recordSetIdKey(recordSetId: String): java.util.HashMap[String, AttributeValue] = {
    val key = new java.util.HashMap[String, AttributeValue]()
    key.put(RECORD_SET_ID, new AttributeValue(recordSetId))
    key
  }

  def toItem(recordSet: RecordSet): java.util.HashMap[String, AttributeValue] = {
    val recordSetBlob = toPB(recordSet).toByteArray

    val bb = ByteBuffer.allocate(recordSetBlob.length) //convert byte array to byte buffer
    bb.put(recordSetBlob)
    bb.position(0)

    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(ZONE_ID, new AttributeValue(recordSet.zoneId))
    item.put(RECORD_SET_TYPE, new AttributeValue(recordSet.typ.toString))
    item.put(RECORD_SET_NAME, new AttributeValue(recordSet.name))
    item.put(RECORD_SET_ID, new AttributeValue(recordSet.id))
    item.put(RECORD_SET_BLOB, new AttributeValue().withB(bb))
    item.put(RECORD_SET_SORT, new AttributeValue(omitTrailingDot(recordSet.name.toLowerCase)))
    item
  }

  def fromItem(item: java.util.Map[String, AttributeValue]): RecordSet =
    try {
      val recordSetBlob = item.get(RECORD_SET_BLOB)
      fromPB(VinylDNSProto.RecordSet.parseFrom(recordSetBlob.getB.array()), None)
    } catch {
      case ex: Throwable =>
        logger.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
}
