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

package vinyldns.api

import cats.effect.{ContextShift, IO}
import com.comcast.ip4s.IpAddress
import fs2.concurrent.SignallingRef
import java.time.{Instant, LocalDateTime, Month, ZoneOffset}
import vinyldns.api.config.{BatchChangeConfig, HighValueDomainConfig, LimitsConfig, ManualReviewConfig, ScheduledChangesConfig}
import vinyldns.api.config.{ZoneAuthConfigs, DottedHostsConfig, BatchChangeConfig, HighValueDomainConfig, LimitsConfig, ManualReviewConfig, ScheduledChangesConfig, ServerConfig}
import vinyldns.api.domain.batch.V6DiscoveryNibbleBoundaries
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._

import scala.util.matching.Regex

trait VinylDNSTestHelpers {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  val processingDisabled: SignallingRef[IO, Boolean] =
    fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()

  val highValueDomainRegexList: List[Regex] = List(new Regex("high-value-domain.*"))
  val highValueDomainIpList: List[IpAddress] =
    (IpAddress.fromString("192.0.2.252") ++ IpAddress.fromString("192.0.2.253") ++ IpAddress.fromString(
      "fd69:27cc:fe91:0:0:0:0:ffff"
    ) ++ IpAddress.fromString(
      "fd69:27cc:fe91:0:0:0:ffff:0"
    )).toList

  val highValueDomainConfig: HighValueDomainConfig =
    HighValueDomainConfig(highValueDomainRegexList, highValueDomainIpList)

  val approvedNameServers: List[Regex] = List(new Regex("some.test.ns."))

  val dottedHostsConfig: DottedHostsConfig = DottedHostsConfig(List(ZoneAuthConfigs("dotted.xyz.",List("xyz"),List("dummy"),List("CNAME"), 3), ZoneAuthConfigs("abc.zone.recordsets.",List("locked"),List("dummy"),List("CNAME"), 3), ZoneAuthConfigs("xyz.",List("super"),List("xyz"),List("CNAME"), 3), ZoneAuthConfigs("dot.xyz.",List("super"),List("xyz"),List("CNAME"), 0)))

  val emptyDottedHostsConfig: DottedHostsConfig = DottedHostsConfig(List.empty)

  val defaultTtl: Long = 7200

  val manualReviewDomainList: List[Regex] = List(new Regex("needs-review.*"))

  val manualReviewIpList: List[IpAddress] =
    (IpAddress.fromString("192.0.2.254") ++ IpAddress.fromString("192.0.2.255") ++ IpAddress.fromString(
      "fd69:27cc:fe91:0:0:0:ffff:1"
    ) ++ IpAddress.fromString("fd69:27cc:fe91:0:0:0:ffff:2")).toList

  val manualReviewZoneNameList: Set[String] = Set("zone.needs.review.")

  val manualReviewConfig: ManualReviewConfig = ManualReviewConfig(
    enabled = true,
    domainList = manualReviewDomainList,
    ipList = manualReviewIpList,
    zoneList = manualReviewZoneNameList
  )

  val scheduledChangesConfig: ScheduledChangesConfig = ScheduledChangesConfig(enabled = true)

  val sharedApprovedTypes: List[RecordType.Value] =
    List(RecordType.A, RecordType.AAAA, RecordType.CNAME, RecordType.PTR, RecordType.TXT)

  val v6DiscoveryNibbleBoundaries: V6DiscoveryNibbleBoundaries =
    V6DiscoveryNibbleBoundaries(min = 5, max = 20)

  val batchChangeLimit = 1000

  val testLimitConfig: LimitsConfig =
    LimitsConfig(100,100,1000,1500,100,100,100)

  val testServerConfig: ServerConfig =
    ServerConfig(100, 100, 100, 100, true, approvedNameServers, "blue", "unset", "vinyldns.", false, true, true, true)

  val batchChangeConfig: BatchChangeConfig =
    BatchChangeConfig(batchChangeLimit, sharedApprovedTypes, v6DiscoveryNibbleBoundaries)

  val fakeTime: Instant = LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC)

  def anonymize(recordSet: RecordSet): RecordSet =
    recordSet.copy(id = "a", created = fakeTime, updated = None)

  def anonymize(recordSetChange: RecordSetChange): RecordSetChange =
    recordSetChange.copy(
      id = "a",
      created = fakeTime,
      recordSet = anonymize(recordSetChange.recordSet),
      updates = recordSetChange.updates.map(anonymize),
      zone = anonymizeTimeOnly(recordSetChange.zone)
    )

  def anonymize(changeSet: ChangeSet): ChangeSet =
    changeSet.copy(
      id = "a",
      createdTimestamp = fakeTime.toEpochMilli,
      processingTimestamp = 0,
      changes = changeSet.changes
        .map(anonymize)
        .sortBy(rs => (rs.recordSet.name, rs.recordSet.typ))
    )

  def anonymizeTimeOnly(zone: Zone): Zone = {
    val newUpdate = zone.updated.map(_ => fakeTime)
    val newLatestSync = zone.latestSync.map(_ => fakeTime)
    zone.copy(created = fakeTime, updated = newUpdate, latestSync = newLatestSync)
  }
}
object VinylDNSTestHelpers extends VinylDNSTestHelpers
