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

package vinyldns.api.domain.zone

import org.scalacheck.Gen._
import org.scalacheck._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, PropSpec}
import org.typelevel.scalatest.ValidationMatchers
import vinyldns.api.ValidationTestImprovements._
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.zone.ZoneConnection._
import vinyldns.api.domain.{InvalidDomainName, InvalidIpv4Address, InvalidLength, InvalidPortNumber}
import vinyldns.core.crypto.CryptoAlgebra

class ZoneConnectionSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with ValidationMatchers
    with VinylDNSTestData {
  val validName: String = "zoneConnectionName"
  val keyName: String = "zoneConnectionKeyName"
  val keyValue: String = "zoneConnectionKey"

  val validAddress1 = "0.0.0.0"
  val validAddress2 = "255.255.255.255"
  val validDomain1: String = "www.test.domain.name.com."
  val validDomain2: String = "A."
  val validAddrs = List(validAddress1, validAddress2, validDomain1, validDomain2)

  val validPort1: Int = 0
  val validPort2: Int = 65535
  val validPorts: List[String] = List(validPort1.toString, validPort2.toString, "")

  val invalidName: String = "a" * 256

  val invalidDomain = "-hello."
  val invalidAddress = "256.256.256.256"
  val invalidAddrs = List(invalidDomain, invalidAddress)

  val invalidPort1 = "-1"
  val invalidPort2 = "65536"
  val invalidPortNums = List(invalidPort1, invalidPort2)

  val invalidPortParams = List("a", "invalidport")

  val testCrypto = new CryptoAlgebra {
    def encrypt(value: String): String = "encrypted!"
    def decrypt(value: String): String = "decrypted!"
  }

  object ZoneConnectionGenerator {
    val connectionNameGen: Gen[String] = for {
      numberWithinRange <- choose(1, HOST_MAX_LENGTH)
      variableLengthString <- listOfN(numberWithinRange, alphaNumChar).map(_.mkString)
    } yield variableLengthString
  }
  import ZoneConnectionGenerator._

  def buildConnectionCombos(addrs: List[String], ports: List[String]): List[String] =
    for {
      a <- addrs
      p <- ports
    } yield s"$a:$p"

  property("ZoneConnection should encrypt clear connections") {
    val test = ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")

    test.encrypted(testCrypto).key shouldBe "encrypted!"
  }

  property("ZoneConnection should decrypt connections") {
    val test = ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "10.1.1.1")
    val decrypted = test.decrypted(testCrypto)

    decrypted.key shouldBe "decrypted!"
  }

  property("Valid descriptions should pass property-based testing") {
    forAll(connectionNameGen) { name: String =>
      validateStringLength(name, Some(ZONE_CONNECTION_MIN), ZONE_CONNECTION_MAX) shouldBe success
      name.length should be > 0
      name.length should be <= HOST_MAX_LENGTH
    }
  }

  property("ConnectionName of \"\" should fail with InvalidLength") {
    validateStringLength("", Some(ZONE_CONNECTION_MIN), ZONE_CONNECTION_MAX).failWith[InvalidLength]
  }

  property("String exceeding maximum connection name length should fail with InvalidLength") {
    validateStringLength(invalidName, Some(ZONE_CONNECTION_MIN), ZONE_CONNECTION_MAX)
      .failWith[InvalidLength]
  }

  property("Valid connection host servers should pass validations") {
    validAddrs.foreach {
      validateHostServer(_) shouldBe success
    }

    buildConnectionCombos(validAddrs, validPorts).foreach {
      validateHostServer(_) shouldBe success
    }
  }

  property("Invalid connection host servers should fail validations") {
    List(invalidDomain, invalidAddress).foreach { invalid =>
      val error = validateHostServer(invalid)
      atLeast(1, error.failures) shouldBe an[InvalidDomainName]
      atLeast(1, error.failures) shouldBe an[InvalidIpv4Address]
    }

    buildConnectionCombos(validAddrs, invalidPortNums).foreach(
      validateHostServer(_).failWith[InvalidPortNumber])

    buildConnectionCombos(validAddrs, invalidPortParams).foreach(
      validateHostServer(_).failWith[InvalidPortNumber])
  }

  property("Build should succeed when valid parameters are passed in") {
    buildConnectionCombos(validAddrs, validPorts).foreach {
      ZoneConnection.build(validName, keyName, keyValue, _) shouldBe success
    }
  }

  property("Build should fail when invalid parameters are passed in") {
    buildConnectionCombos(validAddrs, validPorts).foreach { validHost =>
      ZoneConnection.build(invalidName, keyName, keyValue, validHost).failWith[InvalidLength]
    }

    buildConnectionCombos(invalidAddrs, validPorts).foreach { invalidHost =>
      val errors = ZoneConnection.build(validName, keyName, keyValue, invalidHost)
      atLeast(1, errors.failures) shouldBe an[InvalidDomainName]
      atLeast(1, errors.failures) shouldBe an[InvalidIpv4Address]
    }

    buildConnectionCombos(validAddrs, invalidPortNums).foreach(
      ZoneConnection.build(validName, keyName, keyValue, _).failWith[InvalidPortNumber])

    buildConnectionCombos(validAddrs, invalidPortParams).foreach(
      ZoneConnection.build(validName, keyName, keyValue, _).failWith[InvalidPortNumber])
  }
}
