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

package vinyldns.sqs.queue
import com.amazonaws.services.sqs.model.Message
import org.scalatest.{Matchers, WordSpec}

class SqsMessageTypeSpec extends WordSpec with Matchers {
  import SqsMessageType._

  "fromString" should {
    "parse a SqsRecordSetChangeMessage" in {
      fromString(SqsRecordSetChangeMessage.name) shouldBe Right(SqsRecordSetChangeMessage)
    }

    "parse a SqsZoneChangeMessage" in {
      fromString(SqsZoneChangeMessage.name) shouldBe Right(SqsZoneChangeMessage)
    }

    "parse a SqsBatchChangeMessage" in {
      fromString(SqsBatchChangeMessage.name) shouldBe Right(SqsBatchChangeMessage)
    }

    "return InvalidMessageTypeValue for invalid string" in {
      val invalidString = "invalid-message-type-string"
      fromString(invalidString) shouldBe Left(InvalidMessageTypeValue(invalidString))
    }
  }

  "fromMessage" should {
    "return MessageTypeNotFound for message with missing body" in {
      fromMessage(new Message()) shouldBe Left(MessageTypeNotFound)
    }
  }
}
