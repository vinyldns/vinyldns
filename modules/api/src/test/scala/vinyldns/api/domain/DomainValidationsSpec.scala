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
import vinyldns.core.domain.record.RecordType._

class DomainValidationsSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with ValidatedMatchers {

  import Gen._
  import vinyldns.api.domain.DomainValidations._
  import vinyldns.api.DomainGenerator._
  import vinyldns.api.IpAddressGenerator._

  val validRecordTypeGen: Gen[Seq[RecordType]] =
    Gen.someOf(A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF)
  val invalidRecordTypeGen: Gen[Seq[RecordType]] =
    Gen.someOf(A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF, UNKNOWN)

  property("Email should non-zero length and pass property-based testing") {
    val emailNumAlphaChar: Gen[Char] = frequency((8, alphaNumChar), (1, '-'), (1, '_'))
    val emailGenerator: Gen[String] = for {
      local <- listOf(emailNumAlphaChar).map(_.mkString)
      domain <- listOf(emailNumAlphaChar).map(_.mkString)
      topLevelDomain <- listOfN(5, alphaChar).map(_.mkString)
    } yield local + "@" + domain + "." + topLevelDomain

    forAll(emailGenerator) { email: String =>
      whenever(validateEmail(email).isValid) {
        email.length should be > 0
        (email should fullyMatch).regex(validEmailRegex)

        /* Emails should contain exactly one instance of the '@' symbol, with a period in the sub-string following it */
        email should include("@")

        val emailSplit = email.split("@")
        emailSplit.length shouldEqual 2
        emailSplit(1) should include(".")
      }
    }
  }

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

  property("Smallest and largest port numbers should pass") {
    validatePort("0") shouldBe valid
    validatePort("65535") shouldBe valid
  }

  property("Valid port numbers should pass property-based testing") {
    val validPortNumberGen: Gen[String] = choose(0, 65535).map(_.toString)
    forAll(validPortNumberGen) { validPortNum: String =>
      validatePort(validPortNum) shouldBe valid
      val intValue = validPortNum.toInt
      intValue should be >= 0
      intValue should be < 65535
    }
  }

  property("Invalid port numbers should fail with InvalidPortNumber/InvalidPortParameter") {
    val invalidPortNums = List("-1", "65536")
    invalidPortNums.foreach(validatePort(_).failWith[InvalidPortNumber])

    forAll(alphaStr) { invalidPort: String =>
      validatePort(invalidPort).failWith[InvalidPortNumber]
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

  property("Strings with trailing dots should pass property-based testing") {
    forAll(domainGenerator) { trailingDot: String =>
      validateTrailingDot(trailingDot) shouldBe valid
    }
  }

  property("Strings missing trailing dots should fail with MissingTrailingDot") {
    forAll(alphaNumComponent) { noTrailingDot: String =>
      validateTrailingDot(noTrailingDot).failWith[InvalidDomainName]
    }
  }

  property("Valid record types should pass property-based testing") {
    forAll(validRecordTypeGen) { rt: Seq[RecordType] =>
      validateKnownRecordTypes(rt.toSet) shouldBe valid
    }
  }

  property("Invalid record types should fail property-based testing") {
    forAll(invalidRecordTypeGen) { rt: Seq[RecordType] =>
      whenever(validateKnownRecordTypes(rt.toSet).isInvalid) {
        rt.toSet should contain(UNKNOWN)
      }
    }
  }
}
