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

import cats.scalatest.ValidatedMatchers
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, PropSpec}
import vinyldns.api.ValidationTestImprovements._
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.{InvalidDomainName, InvalidEmail}

class ZoneSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with ValidatedMatchers
    with VinylDNSTestData {
  import AccessLevel._

  val validName = "test."
  val validEmail = "test@email.com"
  val adminGroupId = "adminGroupId"
  val validConnection =
    ZoneConnection("connectionName", "connectionKeyName", "connectionKey", "127.0.0.1")
  val validTransfer =
    ZoneConnection("transferConnectionName", "transferKeyName", "transferKey", "test.com.:900")
  val validZoneACL = ZoneACL(Set(ACLRule(Read)))

  property("toString should output a zone properly") {
    val result = zoneActive.toString

    result should include("id=\"" + zoneActive.id + "\"")
    result should include("name=\"" + zoneActive.name + "\"")
    result should include("connection=\"" + zoneActive.connection + "\"")
    result should include("account=\"" + zoneActive.account + "\"")
    result should include("status=\"" + zoneActive.status + "\"")
    result should include("shared=\"" + zoneActive.shared + "\"")
    result should include("reverse=\"" + zoneActive.isReverse + "\"")
  }

  property("Zone should add an ACL rule") {
    val result = zoneActive.addACLRule(userAclRule)

    result.acl.rules should contain(userAclRule)
  }

  property("Zone should remove an ACL rule") {
    val zone1 = zoneActive.addACLRule(userAclRule)
    val zone2 = zone1.addACLRule(groupAclRule)
    val result = zone2.deleteACLRule(groupAclRule)

    (result.acl.rules should contain).only(userAclRule)
  }

  property("Build should succeed when valid parameters are passed in") {
    Zone.build(validName, validEmail, adminGroupId, None, None, None) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, Some(validConnection), None, None) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, None, Some(validTransfer), None) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, None, None, Some(zoneAcl)) shouldBe valid
    Zone.build(
      validName,
      validEmail,
      adminGroupId,
      Some(validConnection),
      Some(validTransfer),
      None) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, Some(validConnection), None, Some(validZoneACL)) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, None, Some(validTransfer), Some(validZoneACL)) shouldBe valid
    Zone.build(validName, validEmail, adminGroupId, Some(validConnection), None, Some(validZoneACL)) shouldBe valid
    Zone.build(
      validName,
      validEmail,
      adminGroupId,
      Some(validConnection),
      Some(validTransfer),
      Some(validZoneACL)) shouldBe valid
  }

  property("Build should fail when invalid parameters are passed in") {
    val invalidName = "foo.bar"
    val invalidEmail = "@."

    Zone.build(invalidName, validEmail, adminGroupId, None, None, None).failWith[InvalidDomainName]
    Zone.build(validName, invalidEmail, adminGroupId, None, None, None).failWith[InvalidEmail]
  }
}
