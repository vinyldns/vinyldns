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

package vinyldns.api.domain.access

import cats.scalatest.EitherMatchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.ResultHelpers
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record.{PTRData, RecordType}

class GlobalAclSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  import vinyldns.core.TestMembershipData._
  import vinyldns.core.TestZoneData._

  private val globalAcls = GlobalAcls(
    List(GlobalAcl(List(okGroup.id, dummyGroup.id), List(".*foo.*", ".*bar.com.")))
  )

  "isAuthorized" should {
    "return false if the acl list is empty" in {
      GlobalAcls(Nil).isAuthorized(okAuth, "foo", RecordType.A, okZone, Nil) shouldBe false
    }
    "return true if the user and record are in the acl" in {
      globalAcls.isAuthorized(okAuth, "foo", RecordType.A, okZone, Nil) shouldBe true
    }
    "return true for a PTR record if the user and record match an acl" in {
      globalAcls.isAuthorized(
        okAuth,
        "foo",
        RecordType.PTR,
        zoneIp4,
        List(PTRData(Fqdn("foo.com")))
      ) shouldBe true
    }
    "normalizes the record name before testing" in {
      globalAcls.isAuthorized(okAuth, "foo.", RecordType.A, okZone, Nil) shouldBe true
    }
    "return true for a PTR record when all PTR records match an acl" in {
      globalAcls.isAuthorized(
        okAuth,
        "foo",
        RecordType.PTR,
        zoneIp4,
        List(PTRData(Fqdn("foo.com")), PTRData(Fqdn("bar.com")))
      ) shouldBe true
    }
    "return false for a PTR record if one of the PTR records does not match an acl" in {
      globalAcls.isAuthorized(
        okAuth,
        "foo",
        RecordType.PTR,
        zoneIp4,
        List(PTRData(Fqdn("foo.com")), PTRData(Fqdn("blah.net")))
      ) shouldBe false
    }
    "return false for a PTR record if the record data is empty" in {
      globalAcls.isAuthorized(okAuth, "foo", RecordType.PTR, zoneIp4, Nil) shouldBe false
    }
    "return false for a PTR record if the ACL is empty" in {
      GlobalAcls(Nil).isAuthorized(
        okAuth,
        "foo",
        RecordType.PTR,
        zoneIp4,
        List(PTRData(Fqdn("foo.com")))
      ) shouldBe false
    }
    "return false for a PTR record if the ACL is empty and the record data is empty" in {
      GlobalAcls(Nil).isAuthorized(okAuth, "foo", RecordType.PTR, zoneIp4, Nil) shouldBe false
    }
  }
}
