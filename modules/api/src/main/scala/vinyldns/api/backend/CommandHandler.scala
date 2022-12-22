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

import cats.effect.{ContextShift, IO, Timer}
import fs2._
import fs2.concurrent.SignallingRef
import org.slf4j.LoggerFactory
import vinyldns.api.engine.{
  BatchChangeHandler,
  RecordSetChangeHandler,
  ZoneChangeHandler,
  ZoneSyncHandler
}
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.batch.{BatchChange, BatchChangeCommand, BatchChangeRepository}
import vinyldns.core.domain.record.{
  RecordChangeRepository,
  RecordSetChange,
  RecordSetCacheRepository,
  RecordSetRepository
}
import vinyldns.core.domain.zone._
import vinyldns.core.queue.{CommandMessage, MessageCount, MessageQueue}

import scala.concurrent.duration._
import vinyldns.core.notifier.AllNotifiers
import java.io.{PrintWriter, StringWriter}

object CommandHandler {

  private val logger = LoggerFactory.getLogger("vinyldns.api.backend.CommandHandler")
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /* The outcome of handling the message */
  sealed trait MessageOutcome {
    def message: CommandMessage
  }
  final case class DeleteMessage(message: CommandMessage) extends MessageOutcome
  final case class RetryMessage(message: CommandMessage) extends MessageOutcome

  def mainFlow(
      zoneChangeHandler: ZoneChange => IO[ZoneChange],
      recordChangeHandler: (Backend, RecordSetChange) => IO[RecordSetChange],
      zoneSyncHandler: ZoneChange => IO[ZoneChange],
      batchChangeHandler: BatchChangeCommand => IO[Option[BatchChange]],
      mq: MessageQueue,
      count: MessageCount,
      pollingInterval: FiniteDuration,
      pauseSignal: SignallingRef[IO, Boolean],
      backendResolver: BackendResolver,
      maxOpen: Int = 4
  )(implicit timer: Timer[IO]): Stream[IO, Unit] = {

    // Polls queue for message batches, connected to the signal which is toggled in the status endpoint
    val messageSource = startPolling(mq, count, pollingInterval).pauseWhen(pauseSignal)

    // Increase timeouts for zone syncs and creates as they can take 10s of minutes
    val increaseTimeoutWhenSyncing = changeVisibilityTimeoutWhenSyncing(mq)

    val changeRequestProcessor =
      processChangeRequests(
        zoneChangeHandler,
        recordChangeHandler,
        zoneSyncHandler,
        batchChangeHandler,
        backendResolver
      )

    // Delete messages from message queue when complete
    val updateQueue = messageSink(mq)

    // concurrently run 4 message batches, so we can have 40 messages max running concurrently
    def flow(): Stream[IO, Unit] =
      messageSource
        .map(
          _.observe(increaseTimeoutWhenSyncing)
            .through(changeRequestProcessor)
            .through(updateQueue)
        )
        .parJoin(maxOpen)
        .handleErrorWith { error =>
          val errorMessage = new StringWriter
          error.printStackTrace(new PrintWriter(errorMessage))
          logger.error(s"Encountered unexpected error in main flow. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")

          // just continue, the flow should never stop unless explicitly told to do so
          flow()
        }

    flow()
  }

  /* Polls Message Queue for messages */
  def startPolling(mq: MessageQueue, count: MessageCount, pollingInterval: FiniteDuration)(
      implicit timer: Timer[IO]
  ): Stream[IO, Stream[IO, CommandMessage]] = {

    def pollingStream(): Stream[IO, Stream[IO, CommandMessage]] =
      // every delay duration, we poll
      Stream
        .fixedDelay[IO](pollingInterval)
        .evalMap[IO, Chunk[CommandMessage]] { _ =>
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
          val errorMessage = new StringWriter
          error.printStackTrace(new PrintWriter(errorMessage))
          logger.error(s"Encountered error polling message queue. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")

          // just keep going on the stream
          pollingStream()
        }

    pollingStream()
  }

  /* We should only change visibility timeout for zone syncs and creates, which could take minutes */
  def changeVisibilityTimeoutWhenSyncing(mq: MessageQueue): Pipe[IO, CommandMessage, Unit] =
    _.evalMap[IO, Any] { message =>
      message.command match {
        case sync: ZoneChange
            if sync.changeType == ZoneChangeType.Sync || sync.changeType == ZoneChangeType.Create =>
          logger.info(s"Updating visibility timeout for zone change; changeId=${sync.id}")
          mq.changeMessageTimeout(message, 1.hour)

        case _ =>
          // do not change visibility for all other change types
          IO.unit
      }
    }.as(())

  /* Actually processes a change request */
  def processChangeRequests(
      zoneChangeProcessor: ZoneChange => IO[ZoneChange],
      recordChangeProcessor: (Backend, RecordSetChange) => IO[RecordSetChange],
      zoneSyncProcessor: ZoneChange => IO[ZoneChange],
      batchChangeProcessor: BatchChangeCommand => IO[Option[BatchChange]],
      backendResolver: BackendResolver
  ): Pipe[IO, CommandMessage, MessageOutcome] =
    _.evalMap[IO, MessageOutcome] { message =>
      message.command match {
        case sync: ZoneChange
            if sync.changeType == ZoneChangeType.Sync || sync.changeType == ZoneChangeType.Create =>
          outcomeOf(message)(zoneSyncProcessor(sync))

        case zoneChange: ZoneChange =>
          outcomeOf(message)(zoneChangeProcessor(zoneChange))

        case rcr: RecordSetChange =>
          outcomeOf(message)(recordChangeProcessor(backendResolver.resolve(rcr.zone), rcr))

        case bcc: BatchChangeCommand =>
          outcomeOf(message)(batchChangeProcessor(bcc))
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
          val errorMessage = new StringWriter
          e.printStackTrace(new PrintWriter(errorMessage))
          logger.warn(s"Failed processing message need to retry; $message. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
          RetryMessage(message)
        case Right(ok) => ok
      }

  /* On success, delete the message; on failure retry */
  def messageSink(mq: MessageQueue): Pipe[IO, MessageOutcome, Unit] =
    _.evalMap[IO, Any] {
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
           processingSignal: SignallingRef[IO, Boolean],
           pollingInterval: FiniteDuration,
           zoneRepo: ZoneRepository,
           zoneChangeRepo: ZoneChangeRepository,
           recordSetRepo: RecordSetRepository,
           recordChangeRepo: RecordChangeRepository,
           recordSetCacheRepo: RecordSetCacheRepository,
           batchChangeRepo: BatchChangeRepository,
           notifiers: AllNotifiers,
           backendResolver: BackendResolver,
           maxZoneSize: Int
  )(implicit timer: Timer[IO]): IO[Unit] = {
    // Handlers for each type of change request
    val zoneChangeHandler =
      ZoneChangeHandler(zoneRepo, zoneChangeRepo, recordSetRepo, recordSetCacheRepo)
    val recordChangeHandler =
      RecordSetChangeHandler(recordSetRepo, recordChangeRepo,recordSetCacheRepo, batchChangeRepo )
    val zoneSyncHandler =
      ZoneSyncHandler(
        recordSetRepo,
        recordChangeRepo,
        recordSetCacheRepo,
        zoneChangeRepo,
        zoneRepo,
        backendResolver,
        maxZoneSize
      )
    val batchChangeHandler =
      BatchChangeHandler(batchChangeRepo, notifiers)

    CommandHandler
      .mainFlow(
        zoneChangeHandler,
        recordChangeHandler,
        zoneSyncHandler,
        batchChangeHandler,
        mq,
        msgsPerPoll,
        pollingInterval,
        processingSignal,
        backendResolver
      )
      .compile
      .drain
  }
}
