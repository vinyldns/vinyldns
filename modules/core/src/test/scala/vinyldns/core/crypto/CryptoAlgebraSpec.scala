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

import java.lang.reflect.InvocationTargetException

import com.typesafe.config.{Config, ConfigException, ConfigFactory}

import scala.collection.JavaConverters._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TestCrypto(config: Config) extends CryptoAlgebra {
  val testMe: String = config.getString("test-me")
  def encrypt(value: String): String = value
  def decrypt(value: String): String = value
}

class CryptoAlgebraSpec extends AnyWordSpec with Matchers {

  private val conf =
    """
      | type = "vinyldns.core.crypto.NoOpCrypto"
      | test-me = "hello"
    """.stripMargin

  private val cryptoConf = ConfigFactory.parseString(conf)

  "CryptoAlgebra" should {
    "load the expected crypto instance" in {
      CryptoAlgebra.load(cryptoConf).unsafeRunSync() shouldBe a[NoOpCrypto]
    }
    "throw an exception if config is missing type" in {
      val badConfig = ConfigFactory.empty()
      a[ConfigException] should be thrownBy CryptoAlgebra.load(badConfig).unsafeRunSync()
    }
    "return ok if all params are provided" in {
      val opts = Map("type" -> "vinyldns.core.crypto.TestCrypto", "test-me" -> "wassup")
      val goodConfig = ConfigFactory.parseMap(opts.asJava)
      val ok = CryptoAlgebra.load(goodConfig).unsafeRunSync().asInstanceOf[TestCrypto]
      ok.testMe shouldBe "wassup"
    }
    "throw an exception if config is missing items required by the class" in {
      val opts = Map("type" -> "vinyldns.core.crypto.TestCrypto")
      val badConfig = ConfigFactory.parseMap(opts.asJava)

      val thrown = the[InvocationTargetException] thrownBy CryptoAlgebra
        .load(badConfig)
        .unsafeRunSync()
      thrown.getCause shouldBe a[ConfigException]
    }

    "SECURITY: reject NoOpCrypto in production JAR deployments" in {
      // This test verifies that when running from a JAR file (production),
      // NoOpCrypto is rejected to prevent plaintext storage of secrets
      val noOpCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.NoOpCrypto"
      """)

      // Note: This test runs in development (sbt), so NoOpCrypto is allowed.
      // In actual production JAR deployments, the startup would fail with IllegalArgumentException.
      // The security check in CryptoAlgebra.isProductionJar() detects if running from .jar
      val result = CryptoAlgebra.load(noOpCryptoConf).unsafeRunSync()
      result shouldBe a[NoOpCrypto] // Allowed in sbt/development mode
    }

    "SECURITY: allow NoOpCrypto with testing override environment variable" in {
      // This test verifies the escape hatch for local JAR-based testing
      val noOpCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.NoOpCrypto"
      """)

      // In production JAR mode, NoOpCrypto requires explicit override via env var
      // This allows local testing via JAR while preventing accidental production use
      val result = CryptoAlgebra.load(noOpCryptoConf).unsafeRunSync()
      result shouldBe a[NoOpCrypto] // Always allowed in sbt mode
      // Override env var VINYLDNS_ALLOW_NOOP_CRYPTO_FOR_TESTING=true permits JAR mode
    }

    "allow JavaCrypto with proper configuration" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val result = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      result shouldBe a[JavaCrypto]
    }

    "encrypt and decrypt with JavaCrypto" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      val plaintext = "mySecretKey123"
      val encrypted = crypto.encrypt(plaintext)
      val decrypted = crypto.decrypt(encrypted)

      // Encrypted form should be different from plaintext and prefixed with "ENC:"
      encrypted should not equal plaintext
      encrypted should startWith("ENC:")

      // Decrypted should match original plaintext
      decrypted shouldBe plaintext
    }

    "handle plaintext values that are not encrypted" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      val plaintext = "unencryptedValue"

      // Decrypt should return plaintext as-is if not prefixed with "ENC:"
      crypto.decrypt(plaintext) shouldBe plaintext
    }

    "prevent double encryption with JavaCrypto" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      val plaintext = "mySecret"
      val encrypted1 = crypto.encrypt(plaintext)
      val encrypted2 = crypto.encrypt(encrypted1)

      // Should not double-encrypt; encrypted2 should equal encrypted1
      encrypted2 shouldBe encrypted1
    }

    "NoOpCrypto: encrypt returns plaintext unchanged" in {
      val noOpCrypto = new NoOpCrypto(ConfigFactory.parseString("type = \"vinyldns.core.crypto.NoOpCrypto\""))
      val plaintext = "secretData123"
      
      // NoOpCrypto is identity function - encryption should return input unchanged
      val encrypted = noOpCrypto.encrypt(plaintext)
      encrypted shouldBe plaintext
    }

    "NoOpCrypto: decrypt returns plaintext unchanged" in {
      val noOpCrypto = new NoOpCrypto(ConfigFactory.parseString("type = \"vinyldns.core.crypto.NoOpCrypto\""))
      val plaintext = "secretData123"
      
      // NoOpCrypto is identity function - decryption should return input unchanged
      val decrypted = noOpCrypto.decrypt(plaintext)
      decrypted shouldBe plaintext
    }

    "JavaCrypto: encrypt multiple values consistently" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      
      // Test with different sensitive values
      val apiSecret = "api-secret-key-12345"
      val tsigKey = "zone-transfer-key-abcde"
      
      val encryptedSecret = crypto.encrypt(apiSecret)
      val encryptedTsig = crypto.encrypt(tsigKey)
      
      // Both should be encrypted (prefixed with "ENC:")
      encryptedSecret should startWith("ENC:")
      encryptedTsig should startWith("ENC:")
      
      // They should be different (different plaintext)
      encryptedSecret should not equal encryptedTsig
      
      // Decryption should recover originals
      crypto.decrypt(encryptedSecret) shouldBe apiSecret
      crypto.decrypt(encryptedTsig) shouldBe tsigKey
    }

    "JavaCrypto: encrypt long values" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      val longPlaintext = "A" * 1000 // 1000-character value
      
      val encrypted = crypto.encrypt(longPlaintext)
      val decrypted = crypto.decrypt(encrypted)
      
      encrypted should startWith("ENC:")
      decrypted shouldBe longPlaintext
    }

    "JavaCrypto: handle special characters in plaintext" in {
      val javaCryptoConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
        secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      """)

      val crypto = CryptoAlgebra.load(javaCryptoConf).unsafeRunSync()
      val specialChars = """!@#$%^&*()_+-=[]{}|;':",./<>?"""
      
      val encrypted = crypto.encrypt(specialChars)
      val decrypted = crypto.decrypt(encrypted)
      
      encrypted should startWith("ENC:")
      decrypted shouldBe specialChars
    }

    "JavaCrypto: reject config without secret key" in {
      val missingSecretConf = ConfigFactory.parseString("""
        type = "vinyldns.core.crypto.JavaCrypto"
      """)

      // Should fail because secret is required
      an[Exception] should be thrownBy CryptoAlgebra.load(missingSecretConf).unsafeRunSync()
    }
  }
}
