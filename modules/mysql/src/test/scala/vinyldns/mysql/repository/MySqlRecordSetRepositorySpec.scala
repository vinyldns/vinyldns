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

package vinyldns.mysql.repository
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.record.RecordType

class MySqlRecordSetRepositorySpec extends WordSpec with Matchers {
  import MySqlRecordSetRepository._
  "fromRecordType" should {
    "support all record types" in {
      fromRecordType(RecordType.A) shouldBe 1
      fromRecordType(RecordType.AAAA) shouldBe 2
      fromRecordType(RecordType.CNAME) shouldBe 3
      fromRecordType(RecordType.MX) shouldBe 4
      fromRecordType(RecordType.NS) shouldBe 5
      fromRecordType(RecordType.PTR) shouldBe 6
      fromRecordType(RecordType.SPF) shouldBe 7
      fromRecordType(RecordType.SRV) shouldBe 8
      fromRecordType(RecordType.SSHFP) shouldBe 9
      fromRecordType(RecordType.TXT) shouldBe 10
      fromRecordType(RecordType.SOA) shouldBe 11
      fromRecordType(RecordType.UNKNOWN) shouldBe unknownRecordType
    }
  }
}
