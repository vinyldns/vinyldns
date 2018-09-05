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

package vinyldns.core

import org.joda.time.DateTime
import vinyldns.core.domain.record._
import TestZoneData._

object TestRecordSetData {
  val aaaa: RecordSet = RecordSet(
    okZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val cname: RecordSet = RecordSet(
    okZone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(CNAMEData("cname")))

  val ptrIp4: RecordSet = RecordSet(
    okZone.id,
    "30",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(PTRData("ptr")))

  val srv: RecordSet = RecordSet(
    okZone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(SRVData(1, 2, 3, "target")))

  val mx: RecordSet = RecordSet(
    okZone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(MXData(3, "mx")))
}
