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
        // SECURITY: Block NoOpCrypto in production (JAR) mode only
        // Allow in development (SBT) mode for testing convenience
        if (className == "vinyldns.core.crypto.NoOpCrypto" && isProductionJar()) {
          // Allow override for local testing via VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING env var
          val testingOverride = System.getenv("VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING")
          if (testingOverride == null || testingOverride.toLowerCase != "true") {
            throw new IllegalArgumentException(
              "SECURITY ERROR: NoOpCrypto (plaintext storage) is not permitted in production JAR deployments. " +
              "Set CRYPTO_TYPE environment variable to 'vinyldns.core.crypto.JavaCrypto' and " +
              "CRYPTO_SECRET to a 64-character hex string (32-byte AES key). " +
              "NoOpCrypto is suitable only for sbt development/testing, never for production. " +
              "For local JAR-based testing only, set VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING=true"
            )
          }
          logger.warn(
            "SECURITY WARNING: NoOpCrypto is allowed via VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING environment variable. " +
            "This should ONLY be used for local testing. Do NOT use in any production environment."
          )
        }
        if (className == "vinyldns.core.crypto.NoOpCrypto") {
          logger.warn(
            "NoOpCrypto (plaintext storage) is active. This is only acceptable for development/testing. " +
            "Production deployments must use JavaCrypto with CRYPTO_SECRET environment variable."
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

  /**
    * Detect if running as packaged JAR (production) vs sbt (development).
    * Returns true if the application is running from a JAR file, false if running via sbt.
    */
  private def isProductionJar(): Boolean = {
    try {
      val codeSource = getClass.getProtectionDomain().getCodeSource()
      val jarPath = codeSource.getLocation().toURI().getPath()
      jarPath.endsWith(".jar")
    } catch {
      case _: Exception => false // If detection fails, assume development (allow NoOpCrypto)
    }
  }
}
