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
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.DomainValidations.HOST_MAX_LENGTH

class ZoneACLSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with VinylDNSTestData {

  object ZoneAclGenerator {
    import AccessLevel._
    import vinyldns.api.domain.record.RecordType._

    val accessLevelGen: Gen[AccessLevel.Value] = Gen.oneOf(NoAccess, Read, Write, Delete)
    val validRecordTypeGen: Gen[Seq[RecordType]] =
      Gen.someOf(A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF)
    val validDescGen: Gen[String] = for {
      numberWithinRange <- choose(0, HOST_MAX_LENGTH)
      variableLengthString <- listOfN(numberWithinRange, alphaNumChar).map(_.mkString)
    } yield variableLengthString

    def validAclRule: Gen[ACLRule] =
      for {
        access <- accessLevelGen
        desc <- option(validDescGen)
        uId <- option(alphaNumStr)
        gId <- option(alphaNumStr)
        mask <- option(alphaNumStr)
        rType <- validRecordTypeGen
      } yield ACLRule(ACLRuleInfo(access, desc, uId, gId, mask, rType.toSet))

    def validAclRuleSet: Gen[List[ACLRule]] =
      for {
        numberWithinRange <- choose(0, 10)
        aclList <- listOfN(numberWithinRange, validAclRule)
      } yield aclList
  }

  import ZoneAclGenerator._

  property("ZoneACL should add a new ACL rule") {
    val acl = ZoneACL()
    val result = acl.addRule(userAclRule)
    result.rules should contain(userAclRule)
  }

  property("ZoneACL should delete an existing ACL rule") {
    val acl = ZoneACL(Set(userAclRule, groupAclRule))
    val result = acl.deleteRule(userAclRule)
    (result.rules should contain).only(groupAclRule)
  }

  property("ZoneACL should delete a non-existing ACL rule") {
    val acl = ZoneACL(Set(groupAclRule))
    val result = acl.deleteRule(userAclRule)
    (result.rules should contain).only(groupAclRule)
    result shouldBe acl
  }

  property("Build returns ZoneACL when valid values are passed in") {
    forAll(validAclRuleSet) { aclRule: List[ACLRule] =>
      ZoneACL.build(aclRule.toSet).isSuccess shouldEqual true
    }
    import AccessLevel._

    ZoneACL.build(Set(ACLRule(Read))).isSuccess shouldEqual true
    ZoneACL.build(Set.empty).isSuccess shouldEqual true
  }
}
