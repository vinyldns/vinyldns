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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import cats.implicits._
import com.typesafe.config.Config

import scala.collection.JavaConverters._

final case class CommandIdentity(commandId: String, key: String, digest: String)
final case class VerifiedQueuePayload(bytes: Array[Byte], identity: CommandIdentity)

final class QueueMessageSigning private (
    activeKeyId: String,
    keys: Map[String, SecretKeySpec],
    audience: String
) {
  import QueueMessageSigning._

  private def mac(keyId: String, bytes: Array[Byte]): Array[Byte] = {
    val key = keys.getOrElse(keyId, throw InvalidSignature("Unknown queue signing key"))
    val algorithm = Mac.getInstance("HmacSHA256")
    algorithm.init(key)
    algorithm.doFinal(bytes)
  }

  private def intent(messageType: Int, commandId: String, payload: Array[Byte]): Array[Byte] =
    encode { out =>
      out.writeInt(Version)
      out.writeUTF(audience)
      out.writeInt(messageType)
      out.writeUTF(commandId)
      out.writeInt(payload.length)
      out.write(payload)
    }

  def sign(messageType: Int, commandId: String, payload: Array[Byte]): Array[Byte] = {
    require(commandId.nonEmpty, "Queue command ID must not be empty")
    val signed = encode { out =>
      out.writeInt(Version)
      out.writeUTF(activeKeyId)
      out.write(intent(messageType, commandId, payload))
    }
    signed ++ mac(activeKeyId, signed)
  }

  def verify(
      messageType: Int,
      envelope: Array[Byte]
  ): Either[Throwable, VerifiedQueuePayload] =
    Either.catchNonFatal {
      if (envelope.length <= SignatureLength)
        throw InvalidSignature("Missing signed queue envelope")
      val signed = envelope.dropRight(SignatureLength)
      val in = new DataInputStream(new ByteArrayInputStream(signed))
      if (in.readInt() != Version)
        throw InvalidSignature("Unsupported or unsigned queue envelope")
      val keyId = in.readUTF()
      if (!MessageDigest.isEqual(mac(keyId, signed), envelope.takeRight(SignatureLength)))
        throw InvalidSignature("Invalid queue message signature")
      if (in.readInt() != Version || in.readUTF() != audience || in.readInt() != messageType)
        throw InvalidSignature("Queue envelope version, audience or message type mismatch")
      val commandId = in.readUTF()
      if (commandId.isEmpty)
        throw InvalidSignature("Empty signed queue command ID")
      val length = in.readInt()
      if (length < 0 || length != in.available())
        throw InvalidSignature("Invalid signed queue payload length")
      val payload = new Array[Byte](length)
      in.readFully(payload)
      val identityKey = encode { out =>
        out.writeUTF(audience)
        out.writeUTF(commandId)
      }
      VerifiedQueuePayload(
        payload,
        CommandIdentity(
          commandId,
          digest(identityKey),
          digest(intent(messageType, commandId, payload))
        )
      )
    }
}

object QueueMessageSigning {
  private val Version = 0x56445131
  private val SignatureLength = 32

  final case class InvalidSignature(reason: String) extends RuntimeException(reason)

  private def encode(write: DataOutputStream => Unit): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val out = new DataOutputStream(bytes)
    write(out)
    out.flush()
    bytes.toByteArray
  }

  private def digest(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString

  def fromConfig(config: Config): QueueMessageSigning = {
    require(
      config.hasPath("active-key-id") && config.hasPath("audience") && config.hasPath("keys"),
      "queue.signing requires active-key-id, audience and dedicated signing keys"
    )
    val activeKeyId = config.getString("active-key-id")
    val audience = config.getString("audience")
    require(audience.nonEmpty && audience.length <= 128, "Invalid queue signing audience")
    val keyConfig = config.getConfig("keys")
    val keys = keyConfig
      .root()
      .keySet()
      .asScala
      .map { id =>
        require(id.matches("[A-Za-z0-9_-]{1,64}"), "Invalid queue signing key ID")
        val hex = keyConfig.getString(id)
        require(
          hex.matches("[0-9a-fA-F]{64}"),
          "Queue signing keys must contain 64 hexadecimal characters"
        )
        id -> new SecretKeySpec(
          hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray,
          "HmacSHA256"
        )
      }
      .toMap
    require(keys.contains(activeKeyId), "Active queue signing key is not configured")
    new QueueMessageSigning(activeKeyId, keys, audience)
  }
}
