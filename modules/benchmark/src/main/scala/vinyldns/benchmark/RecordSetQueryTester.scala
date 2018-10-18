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

import cats.data.{NonEmptyList, OptionT}
import cats.effect._
import cats.implicits._
import fs2._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{FQDN, RecordSet, RecordSetRepository, RecordType}
import vinyldns.core.domain.zone.{Zone, ZoneRepository}
import vinyldns.core.route.{Monitor, Monitored}

/* Let's run different queries across each zone size */
object RecordSetQueryTester extends Monitored {
  final case class KnownRecord(name: String, typ: RecordType)
  final case class TestZone(size: ZoneSize, zone: Zone, rs: RecordSet)
  final case class TestZones(
      xs: Option[TestZone],
      s: Option[TestZone],
      m: Option[TestZone],
      l: Option[TestZone],
      xl: Option[TestZone],
      xxl: Option[TestZone]) {
    def toList: List[TestZone] = (xs ++ s ++ m ++ l ++ xl ++ xxl).toList
  }

  // we know the records because the generation is deterministic
  val knownRecords: NonEmptyList[KnownRecord] = NonEmptyList.of(
    KnownRecord(NameGenerators.generateRecordName(1), RecordType.AAAA),
    KnownRecord(NameGenerators.generateRecordName(2), RecordType.CNAME),
    KnownRecord(NameGenerators.generateRecordName(3), RecordType.A)
  )

  def loadTestZone(
      zoneRepo: ZoneRepository,
      recordRepo: RecordSetRepository,
      size: ZoneSize): IO[Option[TestZone]] = {
    for {
      zone <- OptionT(zoneRepo.getZonesByFilters(Set(s"z.${size.value}.1.")).map(_.headOption)) // ONLY 1 ZONE!!!
      kr <- OptionT.liftF(IO.pure(knownRecords.head))
      rs <- OptionT(recordRepo.getRecordSets(zone.id, kr.name, kr.typ).map(_.headOption))
    } yield TestZone(size, zone, rs)
  }.value

  /* Load one zone of each size, there maybe none of a certain size keep that in mind */
  def loadTestZones(zoneRepo: ZoneRepository, recordRepo: RecordSetRepository): IO[TestZones] =
    for {
      xs <- loadTestZone(zoneRepo, recordRepo, ZoneSize.XS)
      s <- loadTestZone(zoneRepo, recordRepo, ZoneSize.S)
      m <- loadTestZone(zoneRepo, recordRepo, ZoneSize.M)
      l <- loadTestZone(zoneRepo, recordRepo, ZoneSize.L)
      xl <- loadTestZone(zoneRepo, recordRepo, ZoneSize.XL)
      xxl <- loadTestZone(zoneRepo, recordRepo, ZoneSize.XXL)
    } yield TestZones(xs, s, m, l, xl, xxl)

  def measure[A](name: String, size: ZoneSize)(f: => IO[A]): IO[Unit] =
    for {
      startTime <- IO(System.currentTimeMillis)
      _ <- f
      latency <- IO(System.currentTimeMillis - startTime)
    } yield {
      Monitor(s"query.$name.${size.value}").latency += latency
      Monitor(s"query.$name").latency += latency
    }

  def measure[A](name: String, testZones: TestZones)(f: TestZone => IO[A]): IO[Unit] =
    testZones.toList
      .map(z => measure(name, z.size)(f(z)))
      .sequence
      .as(())

  def getRecordSetsByNameAndType(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("getRecordSetsByNameAndType", testZones) { z =>
      recordRepo.getRecordSets(z.zone.id, z.rs.name, z.rs.typ)
    }

  def getRecordSetById(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("getRecordSetById", testZones) { z =>
      recordRepo.getRecordSet(z.zone.id, z.rs.id)
    }

  def listRecordSets(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("listRecordSets", testZones) { z =>
      recordRepo.listRecordSets(z.zone.id, None, Some(100), None)
    }

  def getRecordSetsByName(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("getRecordSetsByName", testZones) { z =>
      recordRepo.getRecordSetsByName(z.zone.id, z.rs.name)
    }

  def getRecordSetCount(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("getRecordSetCount", testZones) { z =>
      recordRepo.getRecordSetCount(z.zone.id)
    }

  def getRecordSetByFQDN(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    measure("getRecordSetByFQDN", testZones) { z =>
      recordRepo.getRecordSetsByFQDN(List(FQDN(z.rs.name, z.zone.name)))
    }

  // These following functions break the pattern for this special test...
  def getFQDNs(recordRepo: RecordSetRepository, testZone: TestZone): List[FQDN] = {
    // first load 500 records from the repo
    val records = recordRepo.listRecordSets(testZone.zone.id, None, Some(500), None).unsafeRunSync()

    // generate the list of FQDNs
    records.recordSets.map(r => FQDN(r.name, testZone.zone.name))
  }

  def getRecordSetsByFQDN500(recordRepo: RecordSetRepository, fqdns: List[FQDN]): IO[Unit] =
    recordRepo.getRecordSetsByFQDN(fqdns).as(())

  def getRecordSetByFQDN500(recordRepo: RecordSetRepository, testZones: TestZones): IO[Unit] =
    testZones.toList
      .map { z =>
        val fqdns = getFQDNs(recordRepo, z) // done first so we do not time it!! Important!!
        measure("getRecordSetsByFQDN500", z.size)(getRecordSetsByFQDN500(recordRepo, fqdns))
      }
      .sequence
      .as(())

  def runSingle(testZones: TestZones, recordRepo: RecordSetRepository): IO[Unit] =
    for {
      _ <- getRecordSetsByNameAndType(recordRepo, testZones)
      _ <- getRecordSetById(recordRepo, testZones)
      _ <- listRecordSets(recordRepo, testZones)
      _ <- getRecordSetsByName(recordRepo, testZones)
      _ <- getRecordSetCount(recordRepo, testZones)
      _ <- getRecordSetByFQDN(recordRepo, testZones)
      _ <- getRecordSetByFQDN500(recordRepo, testZones)
    } yield ()

  // Runs the test count number of times
  def iterations(count: Int)(singleTest: IO[Unit]): IO[Unit] =
    Stream.repeatEval(singleTest).take(count).compile.drain

  def run(zoneRepo: ZoneRepository, recordRepo: RecordSetRepository): Unit = {
    // NOTE!  Assumes the BulkLoader has been run!!
    val program = for {
      _ <- IO(println("STARTING QUERY TESTS!!!"))
      testZones <- loadTestZones(zoneRepo, recordRepo)
      _ <- IO(println(s"WE HAVE ${testZones.toList.size} TEST ZONES!!!"))
      _ <- iterations(100)(runSingle(testZones, recordRepo))
      _ <- IO(println("FINISHED QUERY TESTS!!!"))
    } yield ()
    program.unsafeRunSync()
  }
}
