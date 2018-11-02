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

package vinyldns.api.engine

import java.util.concurrent.TimeUnit

import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.sqs.model._
import com.typesafe.config.Config
import fs2._
import fs2.concurrent.SignallingRef
import org.slf4j.LoggerFactory
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.domain.zone.ZoneAlreadyExistsError
import vinyldns.api.engine.sqs.SqsConnection
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneChangeStatus, ZoneChangeType}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ZoneCommandHandler {

  import vinyldns.api.engine.sqs.SqsConverters._

  private val logger = LoggerFactory.getLogger("vinyldns.api.sqs.ZoneCommandHandler")
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /* The outcome of handling the message */
  sealed trait MessageOutcome {
    def message: Message
  }

  /* Parsed from a message, the request to be applied */
  sealed trait ChangeRequest {
    def message: Message
  }

  final case class DeleteMessage(message: Message) extends MessageOutcome

  final case class RetryMessage(message: Message) extends MessageOutcome

  final case class ZoneSyncRequest(zoneChange: ZoneChange, message: Message) extends ChangeRequest

  final case class ZoneChangeRequest(zoneChange: ZoneChange, message: Message) extends ChangeRequest

  final case class RecordChangeRequest(recordSetChange: RecordSetChange, message: Message)
      extends ChangeRequest

  def mainFlow(
      dataAccessor: ApiDataAccessor,
      sqsConnection: SqsConnection,
      pollingInterval: FiniteDuration,
      pauseSignal: SignallingRef[IO, Boolean])(implicit timer: Timer[IO]): Stream[IO, Unit] = {

    // Polls SQS for message batches, connected to the signal which is toggled in the status endpoint
    val sqsMessageSource = startPolling(sqsConnection, pollingInterval).pauseWhen(pauseSignal)

    // Increase timeouts for zone syncs as they can take 10s of minutes
    val increaseTimeoutForZoneSyncs = changeVisibilityTimeoutForZoneSyncs(sqsConnection)

    // Handlers for each type of change request
    val zoneChangeHandler =
      ZoneChangeHandler(dataAccessor.zoneRepository, dataAccessor.zoneChangeRepository)
    val recordChangeHandler =
      RecordSetChangeHandler(
        dataAccessor.recordSetRepository,
        dataAccessor.recordChangeRepository,
        dataAccessor.batchChangeRepository)
    val zoneSyncHandler =
      ZoneSyncHandler(dataAccessor.recordSetRepository, dataAccessor.recordChangeRepository)

    val changeRequestProcessor =
      processChangeRequests(zoneChangeHandler, recordChangeHandler, zoneSyncHandler)

    // Delete messages from Sqs when complete
    val updateSqs = messageSink(sqsConnection)

    // concurrently run as many message batches as possible
    def flow(): Stream[IO, Unit] =
      sqsMessageSource
        .through(genMessageStreams)
        .parJoin(4)
        .through(genChangeRequests)
        .observe(increaseTimeoutForZoneSyncs)
        .through(changeRequestProcessor)
        .to(updateSqs)
        .handleErrorWith { error =>
          logger.error("Encountered unexpected error in main flow", error)

          // just continue, the flow should never stop unless explicitly told to do so
          flow()
        }

    flow()
  }

  /* Polls SQS for messages */
  def startPolling(sqsConnection: SqsConnection, pollingInterval: FiniteDuration)(
      implicit timer: Timer[IO]): Stream[IO, ReceiveMessageResult] = {

    def pollingStream(): Stream[IO, ReceiveMessageResult] =
      Stream
        .fixedDelay[IO](pollingInterval)
        .evalMap[IO, ReceiveMessageResult] { _ =>
          sqsConnection.receiveMessageBatch(
            new ReceiveMessageRequest()
              .withMaxNumberOfMessages(10)
              .withMessageAttributeNames(".*")
              .withWaitTimeSeconds(1)
          )
        }
        .handleErrorWith { error =>
          logger.error("Encountered error polling sqs", error)
          pollingStream()
        }

    pollingStream()
  }

  /* We should only change visibility timeout for zone syncs, which could take minutes */
  def changeVisibilityTimeoutForZoneSyncs(sqsConnection: SqsConnection): Sink[IO, ChangeRequest] =
    _.evalMap[IO, Any] {
      case ZoneSyncRequest(sync, msg) =>
        logger.info(
          s"Updating visibility timeout for zone sync; changeId=${sync.id} messageId=${msg.getMessageId}")
        sqsConnection.changeMessageVisibility(
          new ChangeMessageVisibilityRequest()
            .withReceiptHandle(msg.getReceiptHandle)
            .withVisibilityTimeout(1600) // 1200 seconds == 30 minutes
        )

      case _ =>
        IO.unit
    }.map(_ => ())

  /* Converts a Stream of RMR to a Stream of Streams, each containing a finite list of Messages */
  def genMessageStreams: Pipe[IO, ReceiveMessageResult, Stream[IO, Message]] = _.map { rmr =>
    if (rmr.getMessages.size > 0) {
      logger.info(
        s"Generating message stream for message batch messageCount=${rmr.getMessages.size}")
    }
    Stream.emits(rmr.getMessages.asScala)
  }

  /* Converts an SQS Message into a ChangeRequest */
  def genChangeRequests: Pipe[IO, Message, ChangeRequest] = _.map { msg =>
    fromMessage(msg) match {
      case zc: ZoneChange
          if zc.changeType == ZoneChangeType.Sync || zc.changeType == ZoneChangeType.Create =>
        ZoneSyncRequest(zc, msg)

      case zc: ZoneChange =>
        ZoneChangeRequest(zc, msg)

      case rc: RecordSetChange =>
        RecordChangeRequest(rc, msg)
    }
  }

  /* Actually processes a change request */
  def processChangeRequests(
      zoneChangeProcessor: ZoneChange => IO[ZoneChange],
      recordChangeProcessor: (DnsConnection, RecordSetChange) => IO[RecordSetChange],
      zoneSyncProcessor: ZoneChange => IO[ZoneChange]): Pipe[IO, ChangeRequest, MessageOutcome] =
    _.evalMap[IO, MessageOutcome] {
      case zsr @ ZoneSyncRequest(_, _) =>
        val doSync =
          for {
            updatedZoneChange <- zoneChangeProcessor(zsr.zoneChange) // make sure zone is updated to a syncing status
            _ <- checkZoneChangeStatus(updatedZoneChange) // raise error if ZoneChange status is Failed
            syncChange <- zoneSyncProcessor(zsr.zoneChange)
            _ <- zoneChangeProcessor(syncChange) // update zone to Active
          } yield syncChange

        outcomeOf(zsr)(doSync)

      case zcr @ ZoneChangeRequest(_, _) =>
        outcomeOf(zcr)(zoneChangeProcessor(zcr.zoneChange))

      case rcr @ RecordChangeRequest(_, _) =>
        val dnsConn = DnsConnection(
          rcr.recordSetChange.zone.connection.getOrElse(VinylDNSConfig.defaultZoneConnection))
        outcomeOf(rcr)(recordChangeProcessor(dnsConn, rcr.recordSetChange))
    }

  private def checkZoneChangeStatus(zoneChange: ZoneChange): IO[ZoneChange] =
    zoneChange.status match {
      case ZoneChangeStatus.Failed =>
        IO.raiseError(
          ZoneAlreadyExistsError(s"Zone with name ${zoneChange.zone.name} already exists"))
      case _ => IO.pure(zoneChange)
    }

  private def outcomeOf[A](changeRequest: ChangeRequest)(p: => IO[A]): IO[MessageOutcome] =
    IO.pure(logger.info(
        s"Running change request $changeRequest; messageId=${changeRequest.message.getMessageId}"))
      .flatMap(_ => p)
      .map { _ =>
        logger.info(
          s"Successfully completed processing of message; messageId=${changeRequest.message.getMessageId}")
        DeleteMessage(changeRequest.message)
      }
      .attempt
      .map {
        case Left(e) => {
          e match {
            case ZoneAlreadyExistsError(_) => IO.raiseError(e).unsafeRunSync()
            case _ =>
              logger.warn(
                s"Failed processing message need to retry; ; messageId=${changeRequest.message.getMessageId}",
                e)
              RetryMessage(changeRequest.message)
          }
        }
        case Right(ok) => ok
      }

  /* On success, delete the message; on failure, do nothing and allow it to retry */
  def messageSink(sqsConnection: SqsConnection): Sink[IO, MessageOutcome] =
    _.evalMap[IO, Any] {
      case DeleteMessage(msg) =>
        sqsConnection.deleteMessage(
          new DeleteMessageRequest().withReceiptHandle(msg.getReceiptHandle))
      case RetryMessage(msg) =>
        // Nothing to do here, the message will be retried after visibility timeout
        // sqs queues are setup to retry 100 times before dead-lettering the change
        logger.error(
          s"Message failed, not deleting so it can be retried; messageId=${msg.getReceiptHandle}")
        IO.unit
    }.map(_ => ())

}

object ProductionZoneCommandHandler {

  def run(
      sqsConnection: SqsConnection,
      processingSignal: SignallingRef[IO, Boolean],
      dataAccessor: ApiDataAccessor,
      config: Config): IO[Unit] = {
    implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

    for {
      pollingInterval <- IO.pure(
        config.getDuration("polling-interval", TimeUnit.MILLISECONDS).milliseconds)
      flow <- ZoneCommandHandler
        .mainFlow(dataAccessor, sqsConnection, pollingInterval, processingSignal)
        .compile
        .drain
    } yield flow
  }
}
