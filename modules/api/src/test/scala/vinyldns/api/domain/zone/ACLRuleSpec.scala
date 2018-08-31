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

import cats.scalatest.{ValidatedMatchers, ValidatedValues}
import org.scalacheck.Gen._
import org.scalacheck._
import org.scalatest.{Matchers, _}
import org.scalatest.prop._
import vinyldns.api.domain.record.RecordType._

class ACLSpec
    extends PropSpec
    with Matchers
    with ValidatedMatchers
    with ValidatedValues
    with GeneratorDrivenPropertyChecks {
  object AclGenerator {
    import AccessLevel._

    val accessLevelGen: Gen[AccessLevel.Value] = Gen.oneOf(NoAccess, Read, Write, Delete)

    val validRecordTypeGen: Gen[Seq[RecordType]] =
      Gen.someOf(A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF)
    val invalidRecordTypeGen: Gen[Seq[RecordType]] =
      Gen.someOf(A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF, UNKNOWN)
  }

  import ACLRule._
  import AclGenerator._
  import vinyldns.api.domain.DomainValidations._

  val validDescGen: Gen[String] = for {
    numberWithinRange <- choose(0, DESCRIPTION_MAX)
    variableLengthString <- listOfN(numberWithinRange, alphaNumChar).map(_.mkString)
  } yield variableLengthString

  property("AccessLevel should define a default ordering from most to least restrictive") {
    AccessLevel.NoAccess shouldBe <(AccessLevel.Read)
    AccessLevel.Read shouldBe <(AccessLevel.Write)
    AccessLevel.Write shouldBe <(AccessLevel.Delete)
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
