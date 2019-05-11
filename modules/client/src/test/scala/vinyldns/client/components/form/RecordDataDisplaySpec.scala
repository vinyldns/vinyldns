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

package vinyldns.client.components.form

import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.components.RecordDataDisplay
import vinyldns.client.models.record.RecordData
import vinyldns.core.domain.record.RecordType

import scala.language.existentials

class RecordDataDisplaySpec extends WordSpec with Matchers {
  "RecordDataDisplay" should {
    "show only 4 if there are more than 4 records" in {
      val records = List(
        RecordData(address = Some("1.1.1.1")),
        RecordData(address = Some("2.2.2.2")),
        RecordData(address = Some("3.3.3.3")),
        RecordData(address = Some("4.4.4.4")),
        RecordData(address = Some("5.5.5.5")),
        RecordData(address = Some("6.6.6.6"))
      )

      ReactTestUtils.withRenderedIntoDocument(
        RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.A, ""))) { c =>
        val liElements = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "li")
        liElements.length shouldBe 5
        liElements.last.outerHtmlScrubbed() should include("more...")
      }
    }

    "show all if there are more than 4 records and more is clicked" in {
      val records = List(
        RecordData(address = Some("1.1.1.1")),
        RecordData(address = Some("2.2.2.2")),
        RecordData(address = Some("3.3.3.3")),
        RecordData(address = Some("4.4.4.4")),
        RecordData(address = Some("5.5.5.5")),
        RecordData(address = Some("6.6.6.6"))
      )

      ReactTestUtils.withRenderedIntoDocument(
        RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.A, ""))) { c =>
        val more = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-more")
        Simulate.click(more)
        val liElements = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "li")
        liElements.length shouldBe 7
        liElements.last.outerHtmlScrubbed() should include("less...")
      }
    }

    "hide elements if there are more than 4 records and less is clicked" in {
      val records = List(
        RecordData(address = Some("1.1.1.1")),
        RecordData(address = Some("2.2.2.2")),
        RecordData(address = Some("3.3.3.3")),
        RecordData(address = Some("4.4.4.4")),
        RecordData(address = Some("5.5.5.5")),
        RecordData(address = Some("6.6.6.6"))
      )

      ReactTestUtils.withRenderedIntoDocument(
        RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.A, ""))) { c =>
        val more = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-more")
        Simulate.click(more)
        val liElements = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "li")
        liElements.length shouldBe 7
        liElements.last.outerHtmlScrubbed() should include("less...")

        val less = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-less")
        Simulate.click(less)
        val liElementsAfter = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "li")
        liElementsAfter.length shouldBe 5
        liElementsAfter.last.outerHtmlScrubbed() should include("more...")
      }
    }

    "display an A record" in {
      val records = List(
        RecordData(address = Some("1.1.1.1")),
        RecordData(address = Some("2.2.2.2")),
        RecordData(address = Some("3.3.3.3"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.A, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>1.1.1.1</li><li>2.2.2.2</li><li>3.3.3.3</li></ul>"""
    }

    "display an AAAA record" in {
      val records = List(
        RecordData(address = Some("1::1")),
        RecordData(address = Some("2::2")),
        RecordData(address = Some("3::3"))
      )

      ReactTestUtils
        .renderIntoDocument(
          RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.AAAA, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>1::1</li><li>2::2</li><li>3::3</li></ul>"""
    }

    "display a CNAME record" in {
      val records = List(RecordData(cname = Some("cname.")))

      ReactTestUtils
        .renderIntoDocument(
          RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.CNAME, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>cname.</li></ul>"""
    }

    "display a DS record" in {
      val records = List(
        RecordData(
          keytag = Some(1),
          algorithm = Some(3),
          digesttype = Some(1),
          digest = Some("ds1")),
        RecordData(
          keytag = Some(1),
          algorithm = Some(3),
          digesttype = Some(2),
          digest = Some("ds2"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.DS, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<li>
          |KeyTag: 1 |
          | Algorithm: 3 |
          | DigestType: 1 |
          | Digest: ds1
          |</li>
          |<li>
          |KeyTag: 1 |
          | Algorithm: 3 |
          | DigestType: 2 |
          | Digest: ds2
          |</li>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }

    "display a MX record" in {
      val records = List(
        RecordData(preference = Some(1), exchange = Some("e1")),
        RecordData(preference = Some(2), exchange = Some("e2"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.MX, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<li>
          |Preference: 1 |
          | Exchange: e1
          |</li>
          |<li>
          |Preference: 2 |
          | Exchange: e2
          |</li>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }

    "display a NS record" in {
      val records = List(
        RecordData(nsdname = Some("ns1.")),
        RecordData(nsdname = Some("ns2."))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.NS, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>ns1.</li><li>ns2.</li></ul>"""
    }

    "display a PTR record" in {
      val records = List(
        RecordData(ptrdname = Some("ptr1.")),
        RecordData(ptrdname = Some("ptr2."))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.PTR, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>ptr1.</li><li>ptr2.</li></ul>"""
    }

    "display a SOA record" in {
      val records = List(
        RecordData(
          mname = Some("mname"),
          rname = Some("rname"),
          serial = Some(1),
          refresh = Some(2),
          retry = Some(3),
          expire = Some(4),
          minimum = Some(5))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.SOA, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<table><tbody>
          |<tr><td>Mname:</td><td>mname</td></tr>
          |<tr><td>Rname:</td><td>rname</td></tr>
          |<tr><td>Serial:</td><td>1</td></tr>
          |<tr><td>Refresh:</td><td>2</td></tr>
          |<tr><td>Retry:</td><td>3</td></tr>
          |<tr><td>Expire:</td><td>4</td></tr>
          |<tr><td class="GlobalStyle_Styles-keepWhitespace">Minimum:   </td><td>5</td></tr>
          |</tbody></table>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }

    "display a SPF record" in {
      val records = List(
        RecordData(text = Some("spf1")),
        RecordData(text = Some("spf2"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.SPF, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>spf1</li><li>spf2</li></ul>"""
    }

    "display a TXT record" in {
      val records = List(
        RecordData(text = Some("txt1")),
        RecordData(text = Some("txt2"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.TXT, "")))
        .outerHtmlScrubbed() shouldBe
        """<ul class="table-cell-list"><li>txt1</li><li>txt2</li></ul>"""
    }

    "display a SRV record" in {
      val records = List(
        RecordData(priority = Some(1), weight = Some(2), port = Some(3), target = Some("t1")),
        RecordData(priority = Some(4), weight = Some(5), port = Some(6), target = Some("t2"))
      )

      ReactTestUtils
        .renderIntoDocument(RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.SRV, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<li>
          |Priority: 1 |
          | Weight: 2 |
          | Port: 3 |
          | Target: t1
          |</li>
          |<li>
          |Priority: 4 |
          | Weight: 5 |
          | Port: 6 |
          | Target: t2
          |</li>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }

    "display a SSHFP record" in {
      val records = List(
        RecordData(algorithm = Some(1), `type` = Some(2), fingerprint = Some("f1")),
        RecordData(algorithm = Some(3), `type` = Some(4), fingerprint = Some("f2")),
      )

      ReactTestUtils
        .renderIntoDocument(
          RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.SSHFP, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<li>
          |Algorithm: 1 |
          | Type: 2 |
          | Fingerprint: f1
          |</li>
          |<li>
          |Algorithm: 3 |
          | Type: 4 |
          | Fingerprint: f2
          |</li>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }

    "display a NAPTR record" in {
      val records = List(
        RecordData(
          order = Some(10),
          preference = Some(100),
          flags = Some("S"),
          service = Some("SIP"),
          regexp = Some("foo"),
          replacement = Some("target1.")),
        RecordData(
          order = Some(20),
          preference = Some(101),
          flags = Some(""),
          service = Some(""),
          regexp = Some("bar"),
          replacement = Some("target2."))
      )

      ReactTestUtils
        .renderIntoDocument(
          RecordDataDisplay(RecordDataDisplay.Props(records, RecordType.NAPTR, "")))
        .outerHtmlScrubbed() shouldBe
        """
          |<ul class="table-cell-list">
          |<li>
          |Order: 10 |
          | Preference: 100 |
          | Flags: S |
          | Service: SIP |
          | Regexp: foo |
          | Replacement: target1.
          |</li>
          |<li>
          |Order: 20 |
          | Preference: 101 |
          | Flags:  |
          | Service:  |
          | Regexp: bar |
          | Replacement: target2.
          |</li>
          |</ul>""".stripMargin.replaceAll("\n", "")
    }
  }
}
