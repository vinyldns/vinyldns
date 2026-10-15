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

package vinyldns.core.crypto

import java.nio.charset.StandardCharsets
import java.security.{GeneralSecurityException, SecureRandom, Security}

import com.typesafe.config.Config
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, IvParameterSpec, SecretKeySpec}
import javax.xml.bind.DatatypeConverter
import scodec.bits.ByteVector

/**
  * Provides functions for encrypting and decrypting data using a user-defined secret stored in the configuration.
  *
  * New values are encrypted with 256-bit AES-GCM (authenticated encryption) and serialized as
  * `ENC:v2:` + base64(IV || ciphertext || tag).
  *
  * Values written by earlier releases (`ENC:` + base64(IV || AES-CBC ciphertext)) can still be decrypted so that
  * existing data keeps working, but they are never produced anymore.
  */
class JavaCrypto(cryptoConfig: Config) extends CryptoAlgebra {

  private val zeroByte = 0.toByte
  private val zeroChar = 0.toChar

  // Assumes that secret is stored as 64-char hex string (32 bytes)
  private val secret = DatatypeConverter.parseHexBinary(cryptoConfig.getString("secret"))

  private val encryptedPrefix = JavaCrypto.EncryptedPrefix
  private val gcmPrefix = JavaCrypto.GcmPrefix

  private val gcmIvLength = 12 // Recommended IV length for AES-GCM
  private val gcmTagLengthBits = 128
  private val cbcIvLength = 16 // Java AES has a fixed block size of 16 bytes, regardless of key size

  // Enables Java Cryptography Extension (JCE) Unlimited Strength Policy Jurisdiction for the application.
  // Required for 256-bit keys and padding cryptography mode.
  // TODO: Remove once Java is updated to version 9, where it is enabled by default.
  Security.setProperty("crypto.policy", "unlimited")

  /*
   * Attempt to encrypt a string. If the string is already encrypted, do not double encrypt.
   *
   * A value carrying an encrypted prefix is only passed through when it is well formed: authenticated (`ENC:v2:`)
   * values must verify under our key, and legacy (`ENC:`) values must be structurally valid. No legacy decryption is
   * performed here, so this method cannot be used as a padding oracle.
   */
  @throws(classOf[GeneralSecurityException])
  def encrypt(value: String): String =
    if (value.startsWith(gcmPrefix)) {
      // Throws a uniform GeneralSecurityException if the value was not produced with our key or was tampered with
      val verified = decryptGcm(value.substring(gcmPrefix.length))
      nullByteArray(verified)
      value
    } else if (value.startsWith(encryptedPrefix)) {
      if (!isWellFormedLegacy(value.substring(encryptedPrefix.length))) throw invalidCiphertext
      value
    } else {
      encryptGcm(value)
    }

  /*
   * Attempt to decrypt a string. If the string is not prefixed with our encrypted prefix, don't attempt to decrypt.
   */
  @throws(classOf[GeneralSecurityException])
  def decrypt(base64EncryptedData: String): String =
    if (base64EncryptedData.startsWith(gcmPrefix)) {
      val plainTextBytes = decryptGcm(base64EncryptedData.substring(gcmPrefix.length))
      try new String(plainTextBytes, StandardCharsets.UTF_8)
      finally nullByteArray(plainTextBytes)
    } else if (base64EncryptedData.startsWith(encryptedPrefix)) {
      decryptLegacy(base64EncryptedData.substring(encryptedPrefix.length))
    } else {
      // Don't attempt to decrypt data that hasn't been encrypted with our encrypted prefix;
      // simply return passed-in string
      base64EncryptedData
    }

  private def encryptGcm(value: String): String = {
    val inBytes = value.getBytes(StandardCharsets.UTF_8)

    // Generate a new, unique initialization vector (IV) for every encryption
    val iv = new Array[Byte](gcmIvLength)
    JavaCrypto.random.nextBytes(iv)

    val encryptedBytes = gcmCipher(iv, encryptMode = true).doFinal(inBytes)

    try {
      gcmPrefix + (ByteVector(iv) ++ ByteVector(encryptedBytes)).toBase64
    } finally {
      // Clean up memory
      nullByteArray(inBytes)
      nullByteArray(iv)
      nullByteArray(encryptedBytes)
    }
  }

  // Any failure (bad base64, truncated data, wrong key, tampering) results in the same exception and message
  private def decryptGcm(payload: String): Array[Byte] = {
    val inBytes = ByteVector.fromBase64(payload).map(_.toArray).getOrElse(throw invalidCiphertext)
    try {
      if (inBytes.length < gcmIvLength + gcmTagLengthBits / 8) throw invalidCiphertext
      val iv = java.util.Arrays.copyOfRange(inBytes, 0, gcmIvLength)
      try {
        gcmCipher(iv, encryptMode = false).doFinal(inBytes, gcmIvLength, inBytes.length - gcmIvLength)
      } catch {
        case _: GeneralSecurityException | _: IllegalArgumentException => throw invalidCiphertext
      } finally nullByteArray(iv)
    } finally nullByteArray(inBytes)
  }

  // Legacy AES-CBC values: base64(16 byte IV || N * 16 byte blocks), N >= 1
  private def isWellFormedLegacy(payload: String): Boolean =
    ByteVector.fromBase64(payload).exists { bytes =>
      val ctLength = bytes.length - cbcIvLength
      ctLength > 0 && ctLength % cbcIvLength == 0
    }

  // Kept only to read data written by earlier releases; all failures are normalized to one exception
  private def decryptLegacy(payload: String): String = {
    val inBytes = ByteVector.fromBase64(payload).map(_.toArray).getOrElse(throw invalidCiphertext)
    try {
      val iv = java.util.Arrays.copyOfRange(inBytes, 0, math.min(cbcIvLength, inBytes.length))
      val plainTextBytes =
        try {
          val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
          cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(iv))
          cipher.doFinal(inBytes, cbcIvLength, inBytes.length - cbcIvLength)
        } catch {
          case _: GeneralSecurityException | _: IllegalArgumentException => throw invalidCiphertext
        } finally nullByteArray(iv)

      try {
        // Preserves the historical behavior for values written by earlier releases
        new String(plainTextBytes, StandardCharsets.UTF_8).trim
      } finally nullByteArray(plainTextBytes)
    } finally {
      nullCharArray(payload.toCharArray)
      nullByteArray(inBytes)
    }
  }

  private def gcmCipher(iv: Array[Byte], encryptMode: Boolean): Cipher = {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val mode = if (encryptMode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
    cipher.init(mode, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(gcmTagLengthBits, iv))
    cipher
  }

  private def invalidCiphertext: GeneralSecurityException =
    new GeneralSecurityException(JavaCrypto.InvalidCiphertextMessage)

  // Helpers to clean up sensitive data in memory
  private def nullCharArray(arr: Array[Char]): Unit =
    for (i <- arr.indices) arr(i) = zeroChar
  private def nullByteArray(arr: Array[Byte]): Unit =
    for (i <- arr.indices) arr(i) = zeroByte
}

object JavaCrypto {
  val EncryptedPrefix = "ENC:" // Prefix used to indicate encrypted strings
  val GcmPrefix: String = EncryptedPrefix + "v2:" // Prefix used for authenticated (AES-GCM) values
  val InvalidCiphertextMessage = "Invalid encrypted value"

  private val random = new SecureRandom()
}
