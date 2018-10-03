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

package vinyldns.api.backend

import cats.effect.IO
import fs2._
import fs2.async.mutable.Signal
import org.slf4j.LoggerFactory
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.engine.{RecordSetChangeHandler, ZoneChangeHandler, ZoneSyncHandler}
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.record.{RecordChangeRepository, RecordSetChange, RecordSetRepository}
import vinyldns.core.domain.zone._
import vinyldns.core.queue.{CommandMessage, MessageCount, MessageQueue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object CommandHandler {

  private val logger = LoggerFactory.getLogger("vinyldns.api.backend.CommandHandler")

  /* The outcome of handling the message */
  sealed trait MessageOutcome {
    def message: CommandMessage
  }
  final case class DeleteMessage(message: CommandMessage) extends MessageOutcome
  final case class RetryMessage(message: CommandMessage) extends MessageOutcome

  def mainFlow(
      zoneChangeHandler: ZoneChange => IO[ZoneChange],
      recordChangeHandler: (DnsConnection, RecordSetChange) => IO[RecordSetChange],
      zoneSyncHandler: ZoneChange => IO[ZoneChange],
      mq: MessageQueue,
      count: MessageCount,
      pollingInterval: FiniteDuration,
      pauseSignal: Signal[IO, Boolean],
      defaultConn: ZoneConnection)(implicit scheduler: Scheduler): Stream[IO, Unit] = {

    // Polls queue for message batches, connected to the signal which is toggled in the status endpoint
    val messageSource = startPolling(mq, count, pollingInterval).pauseWhen(pauseSignal)

    // Increase timeouts for zone syncs and creates as they can take 10s of minutes
    val increaseTimeoutWhenSyncing = changeVisibilityTimeoutWhenSyncing(mq)

    val changeRequestProcessor =
      processChangeRequests(zoneChangeHandler, recordChangeHandler, zoneSyncHandler, defaultConn)

    // Delete messages from message queue when complete
    val updateQueue = messageSink(mq)

    // concurrently run 4 message batches, so we can have 40 messages max running concurrently
    def flow(): Stream[IO, Unit] =
      messageSource
        .join(4)
        .observe(increaseTimeoutWhenSyncing)
        .through(changeRequestProcessor)
        .to(updateQueue)
        .handleErrorWith { error =>
          logger.error("Encountered unexpected error in main flow", error)

          // just continue, the flow should never stop unless explicitly told to do so
          flow()
        }

    flow()
  }

  /* Polls Message Queue for messages */
  def startPolling(mq: MessageQueue, count: MessageCount, pollingInterval: FiniteDuration)(
      implicit scheduler: Scheduler): Stream[IO, Stream[IO, CommandMessage]] = {

    def pollingStream(): Stream[IO, Stream[IO, CommandMessage]] =
      // every delay duration, we poll
      scheduler
        .fixedDelay[IO](pollingInterval)
        .evalMap[Chunk[CommandMessage]] { _ =>
          // get the messages from the queue, transform them to a Chunk of messages
          mq.receive(count).map(msgs => Chunk(msgs: _*))
        }
        .map {
          // Need to produce a stream of streams so we can run "batches" in parallel
          Stream.chunk(_).covary[IO]
        }
        .handleErrorWith { error =>
          // on error, we make sure we still continue; should only stop when the app stops
          // or processing is disabled
          logger.error("Encountered error polling message queue", error)

          // just keep going on the stream
          pollingStream()
        }

    pollingStream()
  }

  /* We should only change visibility timeout for zone syncs and creates, which could take minutes */
  def changeVisibilityTimeoutWhenSyncing(mq: MessageQueue): Sink[IO, CommandMessage] =
    _.evalMap { message =>
      message.command match {
        case sync: ZoneChange
            if sync.changeType == ZoneChangeType.Sync || sync.changeType == ZoneChangeType.Create =>
          logger.info(s"Updating visibility timeout for zone sync; changeId=${sync.id}")
          mq.changeMessageTimeout(message, 1200.seconds)

        case _ =>
          // do not change visibility for all other change types
          IO.unit
      }
    }.as(())

  /* Actually processes a change request */
  def processChangeRequests(
      zoneChangeProcessor: ZoneChange => IO[ZoneChange],
      recordChangeProcessor: (DnsConnection, RecordSetChange) => IO[RecordSetChange],
      zoneSyncProcessor: ZoneChange => IO[ZoneChange],
      defaultConn: ZoneConnection): Pipe[IO, CommandMessage, MessageOutcome] =
    _.evalMap[MessageOutcome] { message =>
      message.command match {
        case sync: ZoneChange
            if sync.changeType == ZoneChangeType.Sync || sync.changeType == ZoneChangeType.Create =>
          val doSync =
            for {
              _ <- zoneChangeProcessor(sync) // make sure zone is updated to a syncing status
              syncChange <- zoneSyncProcessor(sync)
              _ <- zoneChangeProcessor(syncChange) // update zone to Active
            } yield syncChange

          outcomeOf(message)(doSync)

        case zoneChange: ZoneChange =>
          outcomeOf(message)(zoneChangeProcessor(zoneChange))

        case rcr: RecordSetChange =>
          val dnsConn =
            DnsConnection(rcr.zone.connection.getOrElse(defaultConn))
          outcomeOf(message)(recordChangeProcessor(dnsConn, rcr))
      }
    }

  def outcomeOf[A](message: CommandMessage)(p: => IO[A]): IO[MessageOutcome] =
    IO.pure(logger.info(s"Running change request $message"))
      .flatMap(_ => p)
      .map { _ =>
        logger.info(s"Successfully completed processing of message $message")
        DeleteMessage(message)
      }
      .attempt
      .map {
        case Left(e) =>
          logger.warn(s"Failed processing message need to retry; $message", e)
          RetryMessage(message)
        case Right(ok) => ok
      }

  /* On success, delete the message; on failure retry */
  def messageSink(mq: MessageQueue): Sink[IO, MessageOutcome] =
    _.evalMap[Any] {
      case DeleteMessage(msg) =>
        mq.remove(msg)
      case RetryMessage(msg) =>
        // Nothing to do here, the message will be retried after visibility timeout
        logger.error(s"Message failed, retrying; $msg")
        mq.requeue(msg)
    }.as(())

  def run(
      mq: MessageQueue,
      msgsPerPoll: MessageCount,
      processingSignal: Signal[IO, Boolean],
      pollingInterval: FiniteDuration,
      zoneRepo: ZoneRepository,
      zoneChangeRepo: ZoneChangeRepository,
      recordSetRepo: RecordSetRepository,
      recordChangeRepo: RecordChangeRepository,
      batchChangeRepo: BatchChangeRepository,
      defaultConn: ZoneConnection)(implicit scheduler: Scheduler): IO[Unit] = {
    // Handlers for each type of change request
    val zoneChangeHandler =
      ZoneChangeHandler(zoneRepo, zoneChangeRepo)
    val recordChangeHandler =
      RecordSetChangeHandler(recordSetRepo, recordChangeRepo, batchChangeRepo)
    val zoneSyncHandler =
      ZoneSyncHandler(recordSetRepo, recordChangeRepo)

    CommandHandler
      .mainFlow(
        zoneChangeHandler,
        recordChangeHandler,
        zoneSyncHandler,
        mq,
        msgsPerPoll,
        pollingInterval,
        processingSignal,
        defaultConn
      )
      .compile
      .drain
  }
}
