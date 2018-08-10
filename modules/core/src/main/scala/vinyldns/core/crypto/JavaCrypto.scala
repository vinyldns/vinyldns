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
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.xml.bind.DatatypeConverter
import scodec.bits.ByteVector

/**
  * Provides functions for encrypting and decrypting data using a user-defined secret stored in the configuration.
  *
  * Uses 256-bit AES key with CBC/PKCS5Padding algorithm.
  */
class JavaCrypto(cryptoConfig: Config) extends CryptoAlgebra {

  private val zeroByte = 0.toByte
  private val zeroChar = 0.toChar

  // Assumes that secret is stored as 64-char hex string (32 bytes)
  private val secret = DatatypeConverter.parseHexBinary(cryptoConfig.getString("secret"))
  private val ivLength = 16 // Java AES has a fixed block size of 16 bytes, regardless of key size

  private val encryptedPrefix = "ENC:" // Prefix used to indicate encrypted strings

  // Enables Java Cryptography Extension (JCE) Unlimited Strength Policy Jurisdiction for the application.
  // Required for 256-bit keys and padding cryptography mode.
  // TODO: Remove once Java is updated to version 9, where it is enabled by default.
  Security.setProperty("crypto.policy", "unlimited")

  /*
   * Attempt to encrypt a string. If the string is already encrypted, do not double encrypt.
   */
  @throws(classOf[GeneralSecurityException])
  def encrypt(value: String): String =
    if (value.startsWith(encryptedPrefix)) {
      // Return already encrypted string
      value
    } else {
      // Encode
      val inBytes = StandardCharsets.UTF_8.encode(value)

      // Generate a new initialization vector (IV)
      val iv = new Array[Byte](ivLength)
      new SecureRandom().nextBytes(iv)

      // Generate the key with secret
      val secretKeySpec = new SecretKeySpec(secret, "AES")

      // Perform encryption
      val encryptedBytes = cipher(secretKeySpec, iv, encryptMode = true).doFinal(inBytes.array)

      // Convert cipher text string and pre-pend IV
      try {
        encryptedPrefix + (ByteVector(iv) ++ ByteVector(encryptedBytes)).toBase64
      } finally {
        // Clean up memory
        nullByteArray(inBytes.array)
        nullByteArray(iv)
        nullByteArray(encryptedBytes)
      }
    }

  /*
   * Attempt to decrypt a string. If the string is not prefixed with our encrypted prefix, don't attempt to decrypt.
   */
  @throws(classOf[GeneralSecurityException])
  def decrypt(base64EncryptedData: String): String =
    if (!base64EncryptedData.startsWith(encryptedPrefix)) {
      // Don't attempt to decrypt data that hasn't been encrypted with our encrypted prefix;
      // simply return passed-in string
      base64EncryptedData
    } else {
      // Remove the encrypted prefix
      val actualString = base64EncryptedData.substring(encryptedPrefix.length)

      // Decode bytes from String
      val inBytes = ByteVector
        .fromBase64(actualString)
        .getOrElse(throw new GeneralSecurityException("Base-64 decoding failed!"))

      // Extract IV
      val decryptIv = inBytes.take(ivLength.toLong)

      // Extract cipher text
      val cipherText = inBytes.drop(ivLength.toLong)

      // Decrypt cipher text with IV and key
      val plainTextBytes =
        cipher(new SecretKeySpec(secret, "AES"), decryptIv.toArray, encryptMode = false)
          .doFinal(cipherText.toArray)

      try {
        // Need to trim string since decrypted padded data can result in longer lengths despite same data
        new String(plainTextBytes, "UTF-8").trim
      } finally {
        // Clean up memory
        nullCharArray(actualString.toCharArray)
        nullByteArray(inBytes.toArray)
        nullByteArray(decryptIv.toArray)
        nullByteArray(cipherText.toArray)
        nullByteArray(plainTextBytes)
      }
    }

  /*
   * Helper to instantiate a cipher
   */
  private def cipher(keySpec: SecretKeySpec, iv: Array[Byte], encryptMode: Boolean): Cipher = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val mode = if (encryptMode) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
    cipher.init(mode, keySpec, new IvParameterSpec(iv))
    cipher
  }

  // Helpers to clean up sensitive data in memory
  private def nullCharArray(arr: Array[Char]): Unit =
    for (i <- arr.indices) arr(i) = zeroChar
  private def nullByteArray(arr: Array[Byte]): Unit =
    for (i <- arr.indices) arr(i) = zeroByte
}
