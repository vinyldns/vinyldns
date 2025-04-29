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

package vinyldns.core.domain.zone

import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.core.TestZoneData._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.TestMembershipData.okGroup

class ZoneSpec extends AnyWordSpec with Matchers {

  "Zone" should {
    "toString should output a zone properly" in {
      val result = zoneActive.toString

      result should include("id=\"" + zoneActive.id + "\"")
      result should include("name=\"" + zoneActive.name + "\"")
      result should include("connection=\"" + zoneActive.connection + "\"")
      result should include("transferConnection=\"" + zoneActive.transferConnection + "\"")
      result should include("account=\"" + zoneActive.account + "\"")
      result should include("status=\"" + zoneActive.status + "\"")
      result should include("shared=\"" + zoneActive.shared + "\"")
      result should include("reverse=\"" + zoneActive.isReverse + "\"")
      result should include("isTest=\"" + zoneActive.isReverse + "\"")

      result shouldNot include("updated=")
      result shouldNot include("latestSync=")
    }
    "toString should output a zone properly with updated and latestSync" in {
      val time = Instant.now.truncatedTo(ChronoUnit.MILLIS)
      val result = zoneActive.copy(updated = Some(time), latestSync = Some(time)).toString

      result should include("id=\"" + zoneActive.id + "\"")
      result should include("name=\"" + zoneActive.name + "\"")
      result should include("connection=\"" + zoneActive.connection + "\"")
      result should include("transferConnection=\"" + zoneActive.transferConnection + "\"")
      result should include("account=\"" + zoneActive.account + "\"")
      result should include("status=\"" + zoneActive.status + "\"")
      result should include("shared=\"" + zoneActive.shared + "\"")
      result should include("reverse=\"" + zoneActive.isReverse + "\"")
      result should include("isTest=\"" + zoneActive.isReverse + "\"")
      result should include("updated=\"" + time + "\"")
      result should include("latestSync=\"" + time + "\"")
    }
    "add an ACL rule" in {
      val result = zoneActive.addACLRule(userAclRule)

      result.acl.rules should contain(userAclRule)
    }
    "remove an ACL rule" in {
      val zone1 = zoneActive.addACLRule(userAclRule)
      val zone2 = zone1.addACLRule(groupAclRule)
      val result = zone2.deleteACLRule(groupAclRule)

      (result.acl.rules should contain).only(userAclRule)
    }
  }

  "Generate Zone" should {
    "toString should output a generate zone properly" in {
      val result = generateBindZoneAuthorized.toString

      result should include("id=\"" + generateBindZoneAuthorized.id + "\"")
      result should include("groupId=\"" + generateBindZoneAuthorized.groupId + "\"")
      result should include("provider=\"" + generateBindZoneAuthorized.provider + "\"")
      result should include("zoneName=\"" + generateBindZoneAuthorized.zoneName + "\"")
      result should include("serverId=\"" + generateBindZoneAuthorized.serverId + "\"")
      result should include("masters=\"" + generateBindZoneAuthorized.masters + "\"")
      result should include("nameservers=\"" + generateBindZoneAuthorized.nameservers + "\"")
      result should include("description=\"" + generateBindZoneAuthorized.description + "\"")
      result should include("visibility=\"" + generateBindZoneAuthorized.visibility + "\"")
      result should include("accountId=\"" + generateBindZoneAuthorized.accountId + "\"")
      result should include("projectId=\"" + generateBindZoneAuthorized.projectId + "\"")
      result should include("ttl=\"" + generateBindZoneAuthorized.ttl + "\"")
      result should include("refresh=\"" + generateBindZoneAuthorized.refresh + "\"")
      result should include("retry=\"" + generateBindZoneAuthorized.retry + "\"")
      result should include("expire=\"" + generateBindZoneAuthorized.expire + "\"")
      result should include("negative_cache_ttl=\"" + generateBindZoneAuthorized.negative_cache_ttl + "\"")
    }

    "update a GenerateZone instance from UpdateGenerateZoneInput" in {
      val result = GenerateZone(updateBindZone, generateBindZoneAuthorized)

      result.groupId shouldEqual okGroup.id
      result.provider shouldEqual "bind"
      result.zoneName shouldEqual okZone.name
      result.nameservers shouldEqual Some(List("bind_ns"))
      result.admin_email shouldEqual Some("test@test.com")
      result.ttl shouldEqual Some(3600)
      result.refresh shouldEqual Some(6048000)
      result.retry shouldEqual Some(86400)
      result.expire shouldEqual Some(24192000)
      result.negative_cache_ttl shouldEqual Some(6048000)
      result.response shouldEqual Some(bindZoneGenerationResponse)
      result.id shouldEqual "bindZoneId"
    }
    "update a GenerateZone instance from ZoneGenerationInput" in {
      val result = GenerateZone(inputBindZone)

      result.groupId shouldEqual okGroup.id
      result.provider shouldEqual "bind"
      result.zoneName shouldEqual okZone.name
      result.nameservers shouldEqual Some(List("bind_ns"))
      result.admin_email shouldEqual Some("test@test.com")
      result.ttl shouldEqual Some(3600)
      result.refresh shouldEqual Some(6048000)
      result.retry shouldEqual Some(86400)
      result.expire shouldEqual Some(24192000)
      result.negative_cache_ttl shouldEqual Some(6048000)
      result.response shouldEqual Some(bindZoneGenerationResponse)
    }
  }
}
