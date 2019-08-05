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

package vinyldns.api.domain

import cats.scalatest.ValidatedMatchers
import org.scalacheck._
import org.scalatest._
import org.scalatest.prop._
import vinyldns.api.ValidationTestImprovements._
import vinyldns.core.domain.{InvalidDomainName, InvalidLength}

class DomainValidationsSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with ValidatedMatchers {

  import Gen._
  import vinyldns.api.domain.DomainValidations._
  import vinyldns.api.DomainGenerator._
  import vinyldns.api.IpAddressGenerator._

  property("Shortest domain name should be valid") {
    validateHostName("a.") shouldBe valid
  }

  property("Longest domain name should be valid") {
    val name = ("a" * 50 + ".") * 5
    validateHostName(name) shouldBe valid
  }

  property("Domain name should pass property-based testing") {
    forAll(domainGenerator) { domain: String =>
      whenever(validateHostName(domain).isValid) {
        domain.length should be > 0
        domain.length should be < 256
        (domain should fullyMatch).regex(validFQDNRegex)

        domain should endWith(".")
      }
    }
  }

  property("Domain names beginning with invalid characters should fail with InvalidDomainName") {
    validateHostName("/slash.domain.name.").failWith[InvalidDomainName]
    validateHostName("-hyphen.domain.name.").failWith[InvalidDomainName]
    validateHostName("_underscore.domain.name.").isValid
  }

  property("Valid Ipv4 addresses should pass property-based testing") {
    forAll(validIpv4Gen) { validIp: String =>
      val res = validateIpv4Address(validIp)
      res shouldBe valid
      (validIp should fullyMatch).regex(validIpv4Regex)
    }
  }

  property("Invalid Ipv4 addresses should fail property-based testing") {
    forAll(invalidIpGen) { invalidIp: String =>
      whenever(validateIpv4Address(invalidIp).isInvalid) {
        (invalidIp shouldNot fullyMatch).regex(validIpv4Regex)
      }
    }
  }

  property("Valid string lengths should pass property-based testing") {
    val validDescGen: Gen[String] = listOfN(255, alphaNumChar).map(_.mkString)

    forAll(option(validDescGen)) { desc: Option[String] =>
      validateStringLength(desc, None, 255) shouldBe valid
    }
  }

  property("Description of None and Some(\"\") should succeed") {
    validateStringLength(Some(""), None, 255) shouldBe valid
    validateStringLength(None, None, 255) shouldBe valid
  }

  property("String exceeding maximum description length should fail with InvalidLength") {
    val invalidDesc = "a" * 256
    validateStringLength(Some(invalidDesc), None, 255).failWith[InvalidLength]
  }
}
