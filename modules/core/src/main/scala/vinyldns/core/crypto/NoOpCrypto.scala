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

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Provides a no op implementation of Crypto.  Does not actually do any encryption.
  *
  * Note: This is not recommended for use in production!
  *
  * @param config - [[com.typesafe.config.Config]] that holds the no-op configuration.  This is required in order
  *               to dynamically load this Crypto implementation.  However, it is not used.
  */
class NoOpCrypto(val config: Config) extends CryptoAlgebra {
  def this() {
    this(ConfigFactory.load())
  }
  def encrypt(value: String): String = value
  def decrypt(value: String): String = value
}

object NoOpCrypto {
  val instance = new NoOpCrypto()
}
