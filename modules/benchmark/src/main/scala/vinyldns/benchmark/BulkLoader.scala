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

import java.util.concurrent.atomic.AtomicLong

import cats.effect._
import cats.implicits._
import fs2.{Chunk, Sink, Stream}
import org.slf4j.LoggerFactory
import vinyldns.core.domain.record.{
  ChangeSet,
  RecordChangeRepository,
  RecordSetChange,
  RecordSetRepository
}
import vinyldns.core.domain.zone.ZoneRepository
import vinyldns.core.route.Monitor

object BulkLoader {
  import ZoneGeneration._
  implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val logger = LoggerFactory.getLogger("BENCHMARK")

  def saveChunkOfChanges(
      recordRepo: RecordSetRepository,
      changeRepo: RecordChangeRepository): Sink[IO, Chunk[RecordSetChange]] =
    _.evalMap { changes =>
      if (changes.isEmpty) IO.unit
      else {
        for {
          cs <- IO(ChangeSet(changes.toList))
          _ <- recordRepo.apply(cs)
          _ <- changeRepo.save(cs)
        } yield ()
      }
    }

  def saveZone(zoneRepository: ZoneRepository): Sink[IO, ZoneGenerator] = _.evalMap { zg =>
    zoneRepository.save(zg.zone).as(())
  }

  def benchmarkFlow(
      config: BenchmarkConfig,
      zoneRepo: ZoneRepository,
      recordRepo: RecordSetRepository,
      changeRepo: RecordChangeRepository): Stream[IO, Unit] = {
    val recordSink = saveChunkOfChanges(recordRepo, changeRepo)
    val saveThatZone = saveZone(zoneRepo)

    // Zone streams is a stream of zone generators, each one having 10 - 1MM records
    zoneStreams(config)
      .observe(saveThatZone)
      .map { zg =>
        val startTime = new AtomicLong()
        Stream
          .eval(IO { startTime.set(System.currentTimeMillis) })
          .flatMap { _ =>
            zg.records.chunkN(10000).map(Stream.emit(_).covary[IO].to(recordSink)).parJoinUnbounded
          }
          .evalMap { _ =>
            IO {
              val runTime = System.currentTimeMillis - startTime.get
              // add the latency to zone duration for this size
              Monitor(s"bulkLoad.zone").latency += runTime
              Monitor(s"bulkLoad.zone.${zg.size.value}").latency += runTime
            }
          }
      }
      .parJoinUnbounded
  }

  def run(
      config: BenchmarkConfig,
      zoneRepo: ZoneRepository,
      recordRepo: RecordSetRepository,
      changeRepo: RecordChangeRepository): Unit = {
    val program = benchmarkFlow(config, zoneRepo, recordRepo, changeRepo).compile.drain

    // run our program
    logger.info("STARTING LOAD!!!")
    val startTime = System.currentTimeMillis()
    program.unsafeRunSync()
    val duration = System.currentTimeMillis() - startTime
    logger.info(s"FINISHED LOADING, took ${duration.toDouble / 1000.0} SECONDS!!!")
  }
}
