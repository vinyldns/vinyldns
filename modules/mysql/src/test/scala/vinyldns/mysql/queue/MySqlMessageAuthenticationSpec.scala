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

package vinyldns.mysql.queue

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.TestZoneData.zoneChangePending
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.zone.{ZoneChangeType, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.MessageId
import vinyldns.core.queue.TestQueueSigning.signing

class MySqlMessageAuthenticationSpec
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with ProtobufConversions {
  private val queue = new MySqlMessageQueue(100, signing)
  private val commands: List[ZoneCommand] =
    List(pendingCreateAAAA, BatchChangeCommand("batch-id")) ++
      List(
        ZoneChangeType.Create,
        ZoneChangeType.Update,
        ZoneChangeType.Delete,
        ZoneChangeType.Sync,
        ZoneChangeType.AutomatedSync
      ).map(typ => zoneChangePending.copy(changeType = typ))

  "MySQL queue authentication" should {
    "round-trip every command type with an authenticated identity" in {
      commands.foreach { command =>
        val result = queue
          .parseMessage(
            MessageId(command.id),
            MessageType.fromCommand(command).value,
            queue.getBytes(command),
            0,
            30
          )
          .right
          .value
        result.command shouldBe command
        result.authenticatedIdentity.map(_.commandId) shouldBe Some(command.id)
      }
    }

    "reject unsigned protobufs and batch notifications" in {
      List(
        (pendingCreateAAAA.id, 1, toPB(pendingCreateAAAA).toByteArray),
        (zoneChangePending.id, 2, toPB(zoneChangePending).toByteArray),
        ("batch-id", 3, "batch-id".getBytes("UTF-8"))
      ).foreach {
        case (id, typ, bytes) =>
          queue.parseMessage(MessageId(id), typ, bytes, 0, 30).isLeft shouldBe true
      }
    }

    "reject changed payloads, row IDs and message types before returning commands" in {
      val bytes = queue.getBytes(pendingCreateAAAA)
      val changed = bytes.clone()
      changed(changed.length - 33) = (changed(changed.length - 33) ^ 1).toByte
      queue.parseMessage(MessageId(pendingCreateAAAA.id), 1, changed, 0, 30).isLeft shouldBe true
      queue.parseMessage(MessageId("substituted-id"), 1, bytes, 0, 30).isLeft shouldBe true
      queue.parseMessage(MessageId(pendingCreateAAAA.id), 2, bytes, 0, 30).isLeft shouldBe true
    }

    "reject disagreement between the signed command ID and the decoded payload" in {
      val bytes = signing.sign(1, "different-id", toPB(pendingCreateAAAA).toByteArray)
      queue.parseMessage(MessageId("different-id"), 1, bytes, 0, 30).isLeft shouldBe true
    }
  }
}
