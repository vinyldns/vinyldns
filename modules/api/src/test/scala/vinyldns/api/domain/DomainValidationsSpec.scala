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
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import vinyldns.api.ValidationTestImprovements._
import vinyldns.core.domain.{InvalidDomainName, InvalidCname, InvalidLength}

class DomainValidationsSpec
    extends AnyPropSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
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
  }

  property("Domain names with underscores should pass property-based testing") {
    validateHostName("_underscore.domain.name.").isValid
    validateHostName("under_score.domain.name.").isValid
    validateHostName("underscore._domain.name.").isValid
  }

  // For wildcard records. '*' can only be in the beginning followed by '.' and domain name
  property("Domain names beginning with asterisk should pass property-based testing") {
    validateHostName("*.domain.name.") shouldBe valid
    validateHostName("aste*risk.domain.name.") shouldBe invalid
    validateHostName("*asterisk.domain.name.") shouldBe invalid
    validateHostName("asterisk*.domain.name.") shouldBe invalid
    validateHostName("asterisk.*domain.name.") shouldBe invalid
    validateHostName("asterisk.domain*.name.") shouldBe invalid
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

  property("Shortest cname should be valid") {
    validateCname("a.",true) shouldBe valid
    validateCname("a.",false) shouldBe valid

  }

  property("Longest cname should be valid") {
    val name = ("a" * 50 + ".") * 5
    validateCname(name,true) shouldBe valid
    validateCname(name,false) shouldBe valid

  }

  property("Cnames with underscores should pass property-based testing") {
    validateCname("_underscore.domain.name.",true).isValid
    validateCname("under_score.domain.name.",true).isValid
    validateCname("underscore._domain.name.",true).isValid
    validateCname("_underscore.domain.name.",false).isValid
    validateCname("under_score.domain.name.",false).isValid
    validateCname("underscore._domain.name.",false).isValid
  }

  // For wildcard records. '*' can only be in the beginning followed by '.' and domain name
  property("Cnames beginning with asterisk should pass property-based testing") {
    validateCname("*.domain.name.",true) shouldBe valid
    validateCname("aste*risk.domain.name.",true) shouldBe invalid
    validateCname("*asterisk.domain.name.",true) shouldBe invalid
    validateCname("asterisk*.domain.name.",true) shouldBe invalid
    validateCname("asterisk.*domain.name.",true) shouldBe invalid
    validateCname("asterisk.domain*.name.",true) shouldBe invalid
    validateCname("*.domain.name.",false) shouldBe valid
    validateCname("aste*risk.domain.name.",false) shouldBe invalid
    validateCname("*asterisk.domain.name.",false) shouldBe invalid
    validateCname("asterisk*.domain.name.",false) shouldBe invalid
    validateCname("asterisk.*domain.name.",false) shouldBe invalid
    validateCname("asterisk.domain*.name.",false) shouldBe invalid
  }
  property("Cname names with forward slash should pass with reverse zone") {
    validateCname("/slash.cname.name.",true).isValid
    validateCname("slash./cname.name.",true).isValid
    validateCname("slash.cname./name.",true).isValid
  }
  property("Cname names with forward slash should fail with forward zone") {
    validateCname("/slash.cname.name.",false).failWith[InvalidCname]
    validateCname("slash./cname.name.",false).failWith[InvalidCname]
    validateCname("slash.cname./name.",false).failWith[InvalidCname]
  }
}
