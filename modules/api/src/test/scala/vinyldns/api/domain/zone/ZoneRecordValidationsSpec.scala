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
import com.comcast.ip4s._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.{
  DomainValidationError,
  HighValueDomainError,
  RecordRequiresManualReview
}
import vinyldns.core.domain.record._
import vinyldns.core.TestRecordSetData._

class ZoneRecordValidationsSpec extends WordSpec with Matchers with ValidatedMatchers {

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

  val highValueRegexList = List("high-value-domain.*".r)

  val highValueIpList = List(ip"1::f", ip"10.10.10.10")

  val fullNameServerRx = fullNameServers.map(_.r)

  "Approved Name Server check" should {
    "return successfully if the name server is in the list of approved name servers" in {
      val result = isApprovedNameServer(approvedNameServers, NSData("ns1.test.com"))
      result should beValid(NSData("ns1.test.com"))
    }

    "return an error if the name server is not in the list of approved name servers" in {
      val result = isApprovedNameServer(approvedNameServers, NSData("blah."))
      result should haveInvalid("Name Server blah. is not an approved name server.")
    }
  }

  "isNotHighValueFqdn" should {
    "return successfully if fqdn is not in high value list" in {
      val result = isNotHighValueFqdn(highValueRegexList, "not-high-value.foo.")
      result should beValid(())
    }

    "return an error if fqdn is in high value list" in {
      val result = isNotHighValueFqdn(highValueRegexList, "high-value-domain.foo.")
      result should haveInvalid[DomainValidationError](
        HighValueDomainError("high-value-domain.foo."))
    }
  }

  "isNotHighValueIp" should {
    "return successfully if ipv4 is not in high value list" in {
      val result = isNotHighValueIp(highValueIpList, "10.0.001.251")
      result should beValid(())
    }

    "return successfully if ipv6 is not in high value list" in {
      val result = isNotHighValueIp(highValueIpList, "1:2:3:4:5:6:7:8")
      result should beValid(())
    }

    "return an error if ipv6 is in high value list" in {
      val result = isNotHighValueIp(highValueIpList, "1:0:0:0:0:0:0:f")
      result should haveInvalid[DomainValidationError](HighValueDomainError("1:0:0:0:0:0:0:f"))
    }

    "return an error if ipv4 is in high value list" in {
      val result = isNotHighValueIp(highValueIpList, "10.10.10.10")
      result should haveInvalid[DomainValidationError](HighValueDomainError("10.10.10.10"))
    }
  }

  "isIpInIpList" should {
    "return true if ipv4 is in list" in {
      isIpInIpList(highValueIpList, "10.10.10.10") shouldBe true
    }

    "return true if ipv6 is in list" in {
      isIpInIpList(highValueIpList, "1:0:0:0:0:0:0:f") shouldBe true
    }

    "return false if ipv4 is not in list" in {
      isIpInIpList(highValueIpList, "10.0.0.2") shouldBe false
    }

    "return false if ipv6 is not in list" in {
      isIpInIpList(highValueIpList, "a::b") shouldBe false
    }

    "return false for invalid ip" in {
      isIpInIpList(highValueIpList, "not-an-ip") shouldBe false
    }
  }

  "Contains approved name servers" should {
    "return success if all name servers are in the list of approved name servers" in {
      val test = ns
      containsApprovedNameServers(approvedNameServers, test) should beValid(test)
    }

    "return failure if none of the name servers are in the list of approved name servers" in {
      val test = ns.copy(records = List(NSData("blah1."), NSData("blah2.")))
      containsApprovedNameServers(approvedNameServers, test) should haveInvalid(
        "Name Server blah1. is not an approved name server.").and(
        haveInvalid("Name Server blah2. is not an approved name server."))
    }

    "return a failure if any of the name servers are not in the list of approved name servers" in {
      val test = ns.copy(records = List(NSData("blah1."), NSData("ns1.test.com")))
      containsApprovedNameServers(approvedNameServers, test) should haveInvalid(
        "Name Server blah1. is not an approved name server.")
    }

    "return success if the name server matches a regular expression" in {
      for (nameServer <- fullNameServers) {
        val test = ns.copy(records = List(NSData(nameServer)))
        containsApprovedNameServers(fullNameServerRx, test) should beValid(test)
      }
    }

    "return success even if part of the ns record matches an approved name server" in {
      val test = ns.copy(records = List(NSData("cdn-tr-001-456")))
      containsApprovedNameServers(fullNameServerRx, test) should beValid(test)
    }

    "return a failure if the name server does not match one of the regular expressions" in {
      val test = ns.copy(records = List(NSData("test-foo-ns.")))
      val approved = List(".*bar.*".r, "www.*".r)
      containsApprovedNameServers(approved, test) should haveInvalid(
        "Name Server test-foo-ns. is not an approved name server.")
    }
  }

  "isStringInRegexList" should {
    val regexList = List(
      "three3{3}".r,
      "foo(bar)?".r,
      "fIzB?uz".r
    )

    "return true if string is in regex list" in {
      isStringInRegexList(regexList, "three333") shouldBe true
      isStringInRegexList(regexList, "foo") shouldBe true
      isStringInRegexList(regexList, "fIzuz") shouldBe true
    }

    "return false if string is not in regex list" in {
      isStringInRegexList(regexList, "three3") shouldBe false
      isStringInRegexList(regexList, "foob") shouldBe false
      isStringInRegexList(regexList, "fIzBBuz") shouldBe false
    }

    "return false if regex list is empty" in {
      isStringInRegexList(List(), "bran") shouldBe false
    }
  }

  "toCaseIgnoredRegexList" should {
    val rawList = List(
      "three3{3}",
      "foo(bar)?",
      "fIzB?uz",
      ".*high-value.*"
    )

    "make regexes that ignore case" in {
      val regexList = toCaseIgnoredRegexList(rawList)

      isStringInRegexList(regexList, "fizbuz") shouldBe true
      isStringInRegexList(regexList, "fizBuz") shouldBe true
      isStringInRegexList(regexList, "fIzUz") shouldBe true
      isStringInRegexList(regexList, "FIZBUZ") shouldBe true
      isStringInRegexList(regexList, "foo.high-value.test.com") shouldBe true
      isStringInRegexList(regexList, "foo.HIGH-VALUE.test.com") shouldBe true
    }
  }

  "zoneDoesNotRequireManualReview" should {
    val zoneNameList = List(
      "foo.bar.",
      "bizz.bazz."
    )

    "match zone name regardless of casing" in {
      zoneDoesNotRequireManualReview(zoneNameList, "FOO.bar.", "some.FOO.bar.") should
        haveInvalid[DomainValidationError](RecordRequiresManualReview("some.FOO.bar."))
      zoneDoesNotRequireManualReview(zoneNameList, "bizz.bazz.", "some.bizz.bazz.")
      haveInvalid[DomainValidationError](RecordRequiresManualReview("some.bizz.bazz."))
    }

    "match zone regardless of terminating dot" in {
      zoneDoesNotRequireManualReview(zoneNameList, "foo.bar", "no-dot.foo.bar.") should
        haveInvalid[DomainValidationError](RecordRequiresManualReview("no-dot.foo.bar."))
    }
  }
}
