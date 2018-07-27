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

import org.scalatest.{Matchers, WordSpec}
import org.typelevel.scalatest.ValidationMatchers._
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.record._

class ZoneRecordValidationsSpec extends WordSpec with Matchers with VinylDNSTestData {

  import vinyldns.api.domain.zone.ZoneRecordValidations._

  val approvedNameServers = List("ns1.test.com.".r, "ns2.test.com.".r)

  val fullNameServers = List(
    "172.17.42.1.",
    "dns101.comcast.net.",
    "dns102.comcast.net.",
    "dns103.comcast.net.",
    "dns104.comcast.net.",
    "dns105.comcast.net.",
    "ns1.comcastbusiness.net.",
    "ns2.comcastbusiness.net.",
    "ns3.comcastbusiness.net.",
    "ns4.comcastbusiness.net.",
    "ns5.comcastbusiness.net.",
    "ns1.parent.com.",
    "odol-ccr.*",
    "cdn-tr.*"
  )

  val fullNameServerRx = fullNameServers.map(_.r)

  "Approved Name Server check" should {
    "return successfully if the name server is in the list of approved name servers" in {
      val result = isApprovedNameServer(approvedNameServers, NSData("ns1.test.com"))
      result should beSuccess(NSData("ns1.test.com"))
    }

    "return an error if the name server is not in the list of approved name servers" in {
      val result = isApprovedNameServer(approvedNameServers, NSData("blah."))
      result should haveFailure("Name Server blah. is not an approved name server.")
    }
  }

  "Contains approved name servers" should {
    "return success if all name servers are in the list of approved name servers" in {
      val test = ns
      containsApprovedNameServers(approvedNameServers, test) should beSuccess(test)
    }

    "return failure if none of the name servers are in the list of approved name servers" in {
      val test = ns.copy(records = List(NSData("blah1."), NSData("blah2.")))
      containsApprovedNameServers(approvedNameServers, test) should (haveFailure(
        "Name Server blah1. is not an approved name server.").and(
        haveFailure("Name Server blah2. is not an approved name server.")))
    }

    "return a failure if any of the name servers are not in the list of approved name servers" in {
      val test = ns.copy(records = List(NSData("blah1."), NSData("ns1.test.com")))
      containsApprovedNameServers(approvedNameServers, test) should haveFailure(
        "Name Server blah1. is not an approved name server.")
    }

    "return success if the name server matches a regular expression" in {
      for (nameServer <- fullNameServers) {
        val test = ns.copy(records = List(NSData(nameServer)))
        containsApprovedNameServers(fullNameServerRx, test) should beSuccess(test)
      }
    }

    "return success even if part of the ns record matches an approved name server" in {
      val test = ns.copy(records = List(NSData("cdn-tr-001-456")))
      containsApprovedNameServers(fullNameServerRx, test) should beSuccess(test)
    }

    "return a failure if the name server does not match one of the regular expressions" in {
      val test = ns.copy(records = List(NSData("test-foo-ns.")))
      val approved = List(".*bar.*".r, "www.*".r)
      containsApprovedNameServers(approved, test) should haveFailure(
        "Name Server test-foo-ns. is not an approved name server.")
    }
  }

  "Valid name server" should {
    "return failure if the name server is not in the approved list" in {
      val test = ns.copy(name = "this-is-ok", records = List(NSData("un-approved.server.")))
      validNameServer(approvedNameServers, test) should haveFailure(
        s"Name Server un-approved.server. is not an approved name server.")
    }
  }

  "Validate Dns Zone" should {
    "return success if all records are valid" in {
      val test = List(
        ns.copy(name = "this-is-valid"),
        aaaa.copy(name = "this-is-valid-too")
      )
      validateDnsZone(approvedNameServers, test) should beSuccess(test)
    }

    "allow dotted hosts" in {
      val test = List(
        ns.copy(name = "this.is.valid"),
        aaaa.copy(name = "this.is.valid.too")
      )

      validateDnsZone(approvedNameServers, test) should beSuccess(test)
    }
  }
}
