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

import cats.effect.IO
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

trait CryptoAlgebra {
  def encrypt(value: String): String
  def decrypt(value: String): String
}

object CryptoAlgebra {
  private val logger = LoggerFactory.getLogger("CryptoAlgebra")

  def load(cryptoConfig: Config): IO[CryptoAlgebra] =
    for {
      className <- IO(cryptoConfig.getString("type"))
      _ <- IO {
        // SECURITY: NoOpCrypto (plaintext storage) must be explicitly allowed via config/env,
        // never inferred from the runtime environment. Fails closed when unset or unparsable.
        if (className == "vinyldns.core.crypto.NoOpCrypto") {
          if (!allowInsecureNoOp(cryptoConfig)) {
            throw new IllegalArgumentException(
              "SECURITY ERROR: NoOpCrypto (plaintext storage) is not permitted unless explicitly allowed. " +
              "Set CRYPTO_TYPE environment variable to 'vinyldns.core.crypto.JavaCrypto' and " +
              "CRYPTO_SECRET to a 64-character hex string (32-byte AES key). " +
              "For local development/testing only, set crypto.allow-insecure-noop = true " +
              "(or VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING=true)."
            )
          }
          logger.warn(
            "SECURITY WARNING: NoOpCrypto (plaintext storage) is active via crypto.allow-insecure-noop. " +
            "This should ONLY be used for local development/testing. Do NOT use in any production environment."
          )
        }
      }
      classInstance <- IO(
        Class
          .forName(className)
          .getDeclaredConstructor(classOf[Config])
          .newInstance(cryptoConfig)
          .asInstanceOf[CryptoAlgebra]
      )
    } yield classInstance

  private def allowInsecureNoOp(cryptoConfig: Config): Boolean =
    cryptoConfig.hasPath("allow-insecure-noop") && cryptoConfig.getBoolean("allow-insecure-noop")
}
