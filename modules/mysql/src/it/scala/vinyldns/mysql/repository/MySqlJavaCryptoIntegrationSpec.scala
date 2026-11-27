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

package vinyldns.mysql.repository

import java.nio.charset.StandardCharsets
import java.security.{GeneralSecurityException, SecureRandom}
import java.util.Base64

import com.typesafe.config.ConfigFactory
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.crypto.JavaCrypto
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.{Zone, ZoneConnection}
import vinyldns.mysql.TestMySqlInstance

/**
  * Verifies that secrets encrypted with JavaCrypto survive persistence in MySQL, that values written by earlier
  * (AES-CBC) releases remain readable, and that forged ciphertext is never persisted.
  */
class MySqlJavaCryptoIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val secretHex = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61"
  private val crypto = new JavaCrypto(ConfigFactory.parseString(s"""secret = "$secretHex""""))

  private val idPrefix = "java-crypto-it-"

  // Ensures the shared datasource and migrations are initialized before using a dedicated repository
  private lazy val zoneRepo = TestMySqlInstance.zoneRepository
  private lazy val userRepo = {
    TestMySqlInstance.userRepository
    new MySqlUserRepository(crypto)
  }

  private def legacyEncrypt(value: String): String = {
    val iv = new Array[Byte](16)
    new SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
      Cipher.ENCRYPT_MODE,
      new SecretKeySpec(javax.xml.bind.DatatypeConverter.parseHexBinary(secretHex), "AES"),
      new IvParameterSpec(iv)
    )
    "ENC:" + Base64.getEncoder.encodeToString(iv ++ cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)))
  }

  private def cleanUp(): Unit =
    DB.localTx { s =>
      s.executeUpdate(s"DELETE FROM user WHERE id LIKE '$idPrefix%'")
      s.executeUpdate(s"DELETE FROM zone WHERE id LIKE '$idPrefix%'")
    }

  override protected def beforeAll(): Unit = {
    userRepo
    cleanUp()
  }

  override protected def afterAll(): Unit = {
    cleanUp()
    super.afterAll()
  }

  "MySqlUserRepository with JavaCrypto" should {
    "persist new secret keys in the authenticated format and read them back" in {
      val user = User("user", "access", Encrypted("plain-secret"), id = idPrefix + "v2")
      userRepo.save(user).unsafeRunSync()

      val stored = userRepo.getUser(user.id).unsafeRunSync().get.secretKey.value
      stored should startWith(JavaCrypto.GcmPrefix)
      crypto.decrypt(stored) shouldBe "plain-secret"
    }

    "keep secret keys written by earlier releases readable" in {
      val legacy = legacyEncrypt("legacy-secret")
      val user = User("legacy", "legacy-access", Encrypted(legacy), id = idPrefix + "legacy")
      userRepo.save(user).unsafeRunSync()

      val stored = userRepo.getUser(user.id).unsafeRunSync().get.secretKey.value
      stored shouldBe legacy
      crypto.decrypt(stored) shouldBe "legacy-secret"
    }

    "refuse to persist a forged encrypted secret key" in {
      val forged = JavaCrypto.GcmPrefix + Base64.getEncoder.encodeToString(Array.fill[Byte](40)(1))
      val user = User("forged", "forged-access", Encrypted(forged), id = idPrefix + "forged")

      a[GeneralSecurityException] should be thrownBy userRepo.save(user).unsafeRunSync()
      userRepo.getUser(user.id).unsafeRunSync() shouldBe empty
    }
  }

  "MySqlZoneRepository" should {
    "store an encrypted connection key unchanged so it can be decrypted later" in {
      val connection =
        ZoneConnection("conn", "keyName", Encrypted("tsig-secret"), "10.1.1.1").encrypted(crypto)
      val zone = Zone(
        "java-crypto-it.",
        "test@test.com",
        id = idPrefix + "zone",
        connection = Some(connection),
        transferConnection = Some(connection)
      )
      zoneRepo.save(zone).unsafeRunSync()

      val loaded = zoneRepo.getZone(zone.id).unsafeRunSync().get
      loaded.connection.get.key shouldBe connection.key
      loaded.connection.get.decrypted(crypto).key.value shouldBe "tsig-secret"
      loaded.transferConnection.get.decrypted(crypto).key.value shouldBe "tsig-secret"
    }
  }
}
