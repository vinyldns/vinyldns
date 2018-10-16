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

package vinyldns.benchmark
import java.util.UUID

import cats.effect.IO
import fs2.Stream
import org.joda.time.DateTime
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone

final case class ZoneGenerator(zone: Zone, records: fs2.Stream[IO, RecordSetChange])

sealed abstract class ZoneSize(val value: String, val recordCount: Int)
object ZoneSize {
  case object XS extends ZoneSize("xs", 10)
  case object S extends ZoneSize("s", 100)
  case object M extends ZoneSize("m", 1000)
  case object L extends ZoneSize("l", 10000)
  case object XL extends ZoneSize("xl", 100000)
  case object XXL extends ZoneSize("xxl", 1000000)
}

object ZoneGeneration {
  // a zone generator has a zone, but also an FS2 stream of records that you can pull from lazily
  // to manage memory
  val ZoneEmail = "test@test.com"
  val BenchMarkGroupId = "benchmark-00000000000000000000000000"

  val aTemplate: RecordSet = RecordSet(
    "change-me",
    "ok",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("10.1.1.1")))

  val aaaaTemplate: RecordSet = RecordSet(
    "change-me",
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  val cnameTemplate: RecordSet = RecordSet(
    "change-me",
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(CNAMEData("cname")))

  val userId = "some-user"

  def generateRecordSet(zoneId: String, idx: Int): RecordSet = {
    // most records are going to be A, AAAA, CNAME with a single RData, pick from the 3
    val rs = idx % 3 match {
      case 0 => aTemplate
      case 1 => aaaaTemplate
      case 2 => cnameTemplate
    }
    rs.copy(
      zoneId = zoneId,
      name = NameGenerators.generateRecordName(idx),
      id = UUID.randomUUID().toString)
  }

  def generateZone(zoneSize: ZoneSize, idx: Int): Zone =
    Zone(
      NameGenerators.generateZoneName(zoneSize, idx),
      ZoneEmail,
      adminGroupId = BenchMarkGroupId
    )

  /**
    * Continues to pull new records lazily until we have released the `count` number of record changes
    */
  def recordSetStream(zone: Zone, recordCount: Int): Stream[IO, RecordSetChange] =
    Stream
      .unfold(1) {
        // if we have hit our record count, by the laws of unfold return a none
        case exhausted if exhausted > recordCount => None
        case offset =>
          // we have not reached our record count, generate another my good man
          val record =
            RecordSetChange(
              zone,
              generateRecordSet(zone.id, offset),
              userId,
              RecordSetChangeType.Create,
              RecordSetChangeStatus.Applied
            )
          Some((record, offset + 1))
      }
      .covary[IO]

  /**
    * Lazily creates zone generators for zones of a given record size.  Follows pattern of recordSetStream
    */
  def zoneStream(zoneSize: ZoneSize, zoneCount: Int): Stream[IO, ZoneGenerator] =
    Stream
      .unfold(1) {
        case exhausted if exhausted > zoneCount => None
        case offset =>
          val z = generateZone(zoneSize, offset)
          val generator = ZoneGenerator(z, recordSetStream(z, zoneSize.recordCount))
          Some(generator, offset + 1) // be sure to advance
      }

  /**
    * We will create a stream for each zone size (s, m, l, xl, xxl)
    * So this is essentially a stream of streams.  We will concurrent join them through
    * to the database
    */
  def zoneStreams(config: BenchmarkConfig): Stream[IO, ZoneGenerator] =
    zoneStream(ZoneSize.XS, config.xs) ++
      zoneStream(ZoneSize.S, config.s) ++
      zoneStream(ZoneSize.M, config.m) ++
      zoneStream(ZoneSize.L, config.l) ++
      zoneStream(ZoneSize.XL, config.xl) ++
      zoneStream(ZoneSize.XXL, config.xxl)
}
