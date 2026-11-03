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

package vinyldns.core.queue

import java.nio.charset.StandardCharsets.UTF_8

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class QueueMessageSigningSpec extends AnyWordSpec with Matchers with EitherValues {
  import TestQueueSigning._

  private val payload = "an authorized command".getBytes(UTF_8)
  private val encoded = signing.sign(1, "command-id", payload)

  "Queue message authentication" should {
    "preserve the exact payload and bind its command identity for every message type" in {
      (1 to 3).foreach { typ =>
        val bytes = signing.sign(typ, "command-id", payload)
        val result = signing.verify(typ, bytes).right.value
        result.bytes shouldBe payload
        result.identity.commandId shouldBe "command-id"
      }
    }

    "reject modification of every byte of the envelope, including its signature" in {
      encoded.indices.foreach { index =>
        val altered = encoded.clone()
        altered(index) = (altered(index) ^ 1).toByte
        signing.verify(1, altered).isLeft shouldBe true
      }
    }

    "reject missing, unsigned, truncated and extended envelopes" in {
      List(Array.emptyByteArray, payload, encoded.dropRight(1), encoded ++ Array[Byte](0))
        .foreach(bytes => signing.verify(1, bytes).isLeft shouldBe true)
    }

    "reject replacement of the transport message type" in {
      signing.verify(2, encoded).isLeft shouldBe true
    }

    "reject a valid message addressed to a different deployment" in {
      val other = QueueMessageSigning.fromConfig(
        config.withValue("audience", ConfigValueFactory.fromAnyRef("another-deployment"))
      )
      other.verify(1, encoded).isLeft shouldBe true
    }

    "reject a signature generated with a different secret" in {
      val other = QueueMessageSigning.fromConfig(
        config.withValue("keys.test", ConfigValueFactory.fromAnyRef("11" * 32))
      )
      other.verify(1, encoded).isLeft shouldBe true
    }

    "accept rotation keys without changing the replay identity" in {
      val rotatedConfig = config
        .withValue("keys.next", ConfigValueFactory.fromAnyRef("11" * 32))
        .withValue("active-key-id", ConfigValueFactory.fromAnyRef("next"))
      val rotated = QueueMessageSigning.fromConfig(rotatedConfig)
      val oldIdentity = rotated.verify(1, encoded).right.value.identity
      val newIdentity =
        rotated.verify(1, rotated.sign(1, "command-id", payload)).right.value.identity
      newIdentity shouldBe oldIdentity
      QueueMessageSigning
        .fromConfig(rotatedConfig.withoutPath("keys.test"))
        .verify(1, encoded)
        .isLeft shouldBe true
    }

    "bind content to the identity without allowing command ID substitution" in {
      val original = signing.verify(1, encoded).right.value.identity
      val differentContent =
        signing.verify(1, signing.sign(1, "command-id", Array[Byte](1))).right.value.identity
      differentContent.key shouldBe original.key
      differentContent.digest should not be original.digest
      signing
        .verify(1, signing.sign(1, "other-id", payload))
        .right
        .value
        .identity
        .key should not be original.key
    }

    "fail closed when configuration is missing or invalid" in {
      val invalid = List(
        ConfigFactory.empty(),
        config.withoutPath("keys"),
        config.withValue("active-key-id", ConfigValueFactory.fromAnyRef("missing")),
        config.withValue("audience", ConfigValueFactory.fromAnyRef("")),
        config.withValue("keys.test", ConfigValueFactory.fromAnyRef("short")),
        config.withValue("keys.test", ConfigValueFactory.fromAnyRef("zz" * 32))
      )
      invalid.foreach(
        c => an[IllegalArgumentException] should be thrownBy QueueMessageSigning.fromConfig(c)
      )
    }
  }
}
