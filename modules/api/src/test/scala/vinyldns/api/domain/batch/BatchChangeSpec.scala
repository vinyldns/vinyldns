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

package vinyldns.api.domain.batch

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.domain.batch.BatchTransformations.ExistingZones
import vinyldns.core.domain.record._
import vinyldns.api.domain.zone._

class BatchChangeSpec extends WordSpec with Matchers {
  private val pendingChange = SingleAddChange(
    "zoneid",
    "zonename",
    "rname",
    "inputname",
    RecordType.A,
    123,
    AData("2.2.2.2"),
    SingleChangeStatus.Pending,
    None,
    None,
    None)
  private val failedChange = pendingChange.copy(status = SingleChangeStatus.Failed)
  private val completeChange = pendingChange.copy(status = SingleChangeStatus.Complete)

  private val batchChangeBase = BatchChange(
    "userId",
    "userName",
    None,
    DateTime.now,
    List(pendingChange, failedChange, completeChange))

  "BatchChange" should {
    "calculate Pending status based on SingleChanges" in {
      batchChangeBase.status shouldBe BatchChangeStatus.Pending
    }
    "calculate PartialFailure status based on SingleChanges" in {
      batchChangeBase
        .copy(changes = List(completeChange, failedChange))
        .status shouldBe BatchChangeStatus.PartialFailure
    }
    "calculate Failed status based on SingleChanges" in {
      batchChangeBase.copy(changes = List(failedChange)).status shouldBe BatchChangeStatus.Failed
    }
    "calculate Complete status based on SingleChanges" in {
      batchChangeBase
        .copy(changes = List(completeChange))
        .status shouldBe BatchChangeStatus.Complete
    }
  }
  "ExistingZones" should {
    val ip4base1 = Zone("1.2.3.in-addr.arpa.", "test")
    val ip4del1 = Zone("0/30.1.2.3.in-addr.arpa.", "test")
    val ip4base2 = Zone("10.2.3.in-addr.arpa.", "test")
    val ip4del2 = Zone("0/30.10.2.3.in-addr.arpa.", "test")
    val ipv6match1 = Zone("0.1.0.0.2.ip6.arpa.", "test")
    val ipv6match2 = Zone("0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "test")
    val ipv6match3 = Zone("0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "test")
    val ipv6nonMatch1 = Zone("5.1.0.0.2.ip6.arpa.", "test")
    val ipv6nonMatch2 = Zone("5.0.8.b.d.0.1.0.0.2.ip6.arpa.", "test")
    val ipv6nonMatch3 = Zone("5.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "test")

    val existingZones = ExistingZones(
      Set(
        ip4base1,
        ip4del1,
        ip4base2,
        ip4del2,
        ipv6match1,
        ipv6match2,
        ipv6match3,
        ipv6nonMatch1,
        ipv6nonMatch2,
        ipv6nonMatch3))

    "getipv4PTRMatches" should {
      "return all possible matches" in {
        existingZones.getipv4PTRMatches("3.2.1.55") should contain theSameElementsAs List(
          ip4base1,
          ip4del1)
      }
      "return empty if there are no matches" in {
        existingZones.getipv4PTRMatches("55.55.55.55") shouldBe List()
      }
    }
    "getipv6PTRMatches" should {
      "return all possible matches" in {
        val expected = List(ipv6match1, ipv6match2, ipv6match3)
        // these are all different forms of the same IP
        existingZones
          .getipv6PTRMatches("2001:0db8:0000:0000:0000:ff00:0042:8329") should contain theSameElementsAs expected
        existingZones.getipv6PTRMatches("2001:db8:0:0:0:ff00:42:8329") should contain theSameElementsAs expected
        existingZones.getipv6PTRMatches("2001:db8::ff00:42:8329") should contain theSameElementsAs expected

        // only matches 1st option
        existingZones.getipv6PTRMatches("2001:0db0:0000:0000:0000:0000:0000:0000") shouldBe List(
          ipv6match1)
        existingZones.getipv6PTRMatches("2001:db0::ff00:42:8329") shouldBe List(ipv6match1)
      }
      "return empty if there are no matches" in {
        existingZones.getipv6PTRMatches("2002:0db0:0000:0000:0000:0000:0000:0000") shouldBe List()
        existingZones.getipv6PTRMatches("2002:db0::ff00:42:8329") shouldBe List()
      }
    }
  }
}
