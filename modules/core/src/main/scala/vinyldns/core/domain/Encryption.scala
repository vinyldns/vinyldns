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

package vinyldns.core.domain

import vinyldns.core.crypto.CryptoAlgebra

// Keep key material out of logs and error messages; use `.value` explicitly
final case class Encrypted private (value: String) extends AnyVal {
  override def toString: String = Encryption.RedactedKey
}

object Encryption {
  // Returned in place of keys; on update it means "keep the stored key"
  val RedactedKey: String = "********"

  def isRedacted(x: Encrypted): Boolean = x.value == RedactedKey

  def apply(crypto: CryptoAlgebra, value: String): Encrypted = Encrypted(crypto.encrypt(value))
  def decrypt(crypto: CryptoAlgebra, x: Encrypted): String = crypto.decrypt(x.value)
}

object EncryptFromJson {
  def fromString(name: String): Either[String, Encrypted] =
    Option(Encrypted(name)).toRight[String](s"Unsupported format")
}
