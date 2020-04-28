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
  }
}
