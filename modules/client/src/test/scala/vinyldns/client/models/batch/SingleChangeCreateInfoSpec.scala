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

package vinyldns.client.models.batch

import org.scalatest._
import vinyldns.client.SharedTestData
import vinyldns.client.models.record.RecordData

class SingleChangeCreateInfoSpec extends WordSpec with Matchers with SharedTestData {
  "SingleChangeCreateInfo.parseChangeType" should {
    "return Add type as right" in {
      SingleChangeCreateInfo.parseChangeType("Add", 1) shouldBe Right("Add")
      SingleChangeCreateInfo.parseChangeType("add", 1) shouldBe Right("Add")
    }

    "return DeleteRecordSet type as right" in {
      SingleChangeCreateInfo.parseChangeType("delete", 1) shouldBe Right("DeleteRecordSet")
      SingleChangeCreateInfo.parseChangeType("Delete", 1) shouldBe Right("DeleteRecordSet")
      SingleChangeCreateInfo.parseChangeType("DeleteRecordSet", 1) shouldBe
        Right("DeleteRecordSet")
      SingleChangeCreateInfo.parseChangeType("deleterecordset", 1) shouldBe
        Right("DeleteRecordSet")
    }

    "return a left throwable when changeType is invalid" in {
      SingleChangeCreateInfo.parseChangeType("bad", 1).left.toOption.get shouldBe a[Throwable]
    }
  }

  "SingleChangeCreateInfo.parseRecordType" should {
    "return record type as right if valid" in {
      SingleChangeCreateInfo.parseRecordType("A", 1) shouldBe Right("A")
      SingleChangeCreateInfo.parseRecordType("A+PTR", 1) shouldBe Right("A+PTR")
      SingleChangeCreateInfo.parseRecordType("cname", 1) shouldBe Right("CNAME")
    }

    "return left throwable when record type is MX" in {
      SingleChangeCreateInfo.parseRecordType("MX", 1).left.toOption.get shouldBe a[Throwable]

    }

    "return left throwable when record type is not supported" in {
      SingleChangeCreateInfo.parseRecordType("DS", 1).left.toOption.get shouldBe a[Throwable]

      SingleChangeCreateInfo.parseRecordType("noexisto", 1).left.toOption.get shouldBe a[Throwable]
    }
  }

  "SingleChangeCreateInfo.parseInputName" should {
    "return the input as right" in {
      SingleChangeCreateInfo.parseInputName("") shouldBe Right("")
      SingleChangeCreateInfo.parseInputName("foo.bar.") shouldBe Right("foo.bar.")
    }
  }

  "SingleChangeCreateInfo.parseTTL" should {
    "return a right None if empty" in {
      SingleChangeCreateInfo.parseTTL("", 1) shouldBe Right(None)
      SingleChangeCreateInfo.parseTTL(" ", 1) shouldBe Right(None)
    }

    "return a right option int if a number" in {
      SingleChangeCreateInfo.parseTTL("10", 1) shouldBe Right(Some(10))
    }

    "return a left throwable if not a number" in {
      SingleChangeCreateInfo.parseTTL("bad", 1).left.toOption.get shouldBe a[Throwable]
    }
  }

  "SingleChangeCreateInfo.parseRecordData" should {
    "return address record data for joint PTR type" in {
      SingleChangeCreateInfo.parseRecordData("1.1.1.1", 1, "A+PTR") shouldBe
        Right(Some(RecordData(address = Some("1.1.1.1"))))

      SingleChangeCreateInfo.parseRecordData("1::1", 1, "AAAA+PTR") shouldBe
        Right(Some(RecordData(address = Some("1::1"))))
    }

    "return correct record data based off type" in {
      SingleChangeCreateInfo.parseRecordData("1.1.1.1", 1, "A") shouldBe
        Right(Some(RecordData(address = Some("1.1.1.1"))))

      SingleChangeCreateInfo.parseRecordData("1::1", 1, "AAAA") shouldBe
        Right(Some(RecordData(address = Some("1::1"))))

      SingleChangeCreateInfo.parseRecordData("foo.", 1, "CNAME") shouldBe
        Right(Some(RecordData(cname = Some("foo."))))

      SingleChangeCreateInfo.parseRecordData("foo.", 1, "PTR") shouldBe
        Right(Some(RecordData(ptrdname = Some("foo."))))

      SingleChangeCreateInfo.parseRecordData("foo.", 1, "TXT") shouldBe
        Right(Some(RecordData(text = Some("foo."))))
    }

    "return a left throwable for unsupported record types" in {
      SingleChangeCreateInfo
        .parseRecordData("foo.", 1, "DS")
        .left
        .toOption
        .get shouldBe a[Throwable]
    }

    "return a left throwable for invalid record types" in {
      SingleChangeCreateInfo
        .parseRecordData("foo.", 1, "bad")
        .left
        .toOption
        .get shouldBe a[Throwable]
    }
  }

  "SingleChangeCreateInfo.parseFromCsvRow" should {
    "return a left throwable if row does not contain 5 columns" in {
      SingleChangeCreateInfo.parseFromCsvRow("", 1).left.toOption.get shouldBe a[Throwable]
      SingleChangeCreateInfo.parseFromCsvRow("1,2,3", 1).left.toOption.get shouldBe a[Throwable]
      SingleChangeCreateInfo.parseFromCsvRow("1", 1).left.toOption.get shouldBe a[Throwable]
      SingleChangeCreateInfo
        .parseFromCsvRow("1,2,3,4,5,6", 1)
        .left
        .toOption
        .get shouldBe a[Throwable]
    }

    "convert csv row to SingleChangeCreateInfo" in {
      val aAdd = "Add,A,foo.ok.,300,1.1.1.1"
      val aPTRAdd = "Add, A+PTR,foo.ok.  ,300,1.1.1.1"
      val cnameDelete = "delete,CNAME,bar.ok.,,"
      val textDelete = "DeleteRecordSet,TXT,bar.ok.,,"

      SingleChangeCreateInfo.parseFromCsvRow(aAdd, 1) shouldBe
        Right(
          SingleChangeCreateInfo(
            "foo.ok.",
            "Add",
            "A",
            Some(300),
            Some(RecordData(address = Some("1.1.1.1")))))

      SingleChangeCreateInfo.parseFromCsvRow(aPTRAdd, 1) shouldBe
        Right(
          SingleChangeCreateInfo(
            "foo.ok.",
            "Add",
            "A+PTR",
            Some(300),
            Some(RecordData(address = Some("1.1.1.1")))))

      SingleChangeCreateInfo.parseFromCsvRow(cnameDelete, 1) shouldBe
        Right(SingleChangeCreateInfo("bar.ok.", "DeleteRecordSet", "CNAME"))

      SingleChangeCreateInfo.parseFromCsvRow(textDelete, 1) shouldBe
        Right(SingleChangeCreateInfo("bar.ok.", "DeleteRecordSet", "TXT"))
    }

    "return left throwable if a column has bad input" in {
      SingleChangeCreateInfo
        .parseFromCsvRow("Add,noexist,foo.ok.,300,1.1.1.1", 1)
        .left
        .toOption
        .get
        .getMessage shouldBe s""""row 1: record type 'noexist' cannot be done via csv""""
    }
  }

  "SingleChangeCreateInfo.toCsv" should {
    "convert a list of single changes to a csv" in {
      val expected =
        """Change Type,Record Type,Input Name,TTL,Record Data
          |Add,A,foo.ok.,300,1.1.1.1
          |Add,A+PTR,foo.ok.,300,1.1.1.1
          |DeleteRecordSet,CNAME,bar.ok.,,""".stripMargin + "\n"

      val changes = List(
        SingleChangeCreateInfo(
          "foo.ok.",
          "Add",
          "A",
          Some(300),
          Some(RecordData(address = Some("1.1.1.1")))),
        SingleChangeCreateInfo(
          "foo.ok.",
          "Add",
          "A+PTR",
          Some(300),
          Some(RecordData(address = Some("1.1.1.1")))),
        SingleChangeCreateInfo("bar.ok.", "DeleteRecordSet", "CNAME")
      )

      SingleChangeCreateInfo.toCsv(changes) shouldBe expected
    }

    "filter MX type out of csv" in {
      val expected =
        """Change Type,Record Type,Input Name,TTL,Record Data
          |Add,A,foo.ok.,300,1.1.1.1""".stripMargin + "\n"

      val changes = List(
        SingleChangeCreateInfo(
          "foo.ok.",
          "Add",
          "A",
          Some(300),
          Some(RecordData(address = Some("1.1.1.1")))),
        SingleChangeCreateInfo(
          "foo.ok.",
          "Add",
          "MX",
          Some(300),
          Some(RecordData(preference = Some(1), exchange = Some("ex."))))
      )

      SingleChangeCreateInfo.toCsv(changes) shouldBe expected
    }

    "just return headers if changes are empty" in {
      val expected = "Change Type,Record Type,Input Name,TTL,Record Data\n"
      SingleChangeCreateInfo.toCsv(List()) shouldBe expected
    }
  }
}
