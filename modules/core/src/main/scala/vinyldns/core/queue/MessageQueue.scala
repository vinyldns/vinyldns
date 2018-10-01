package vinyldns.core.queue
import cats.effect.IO
import vinyldns.core.domain.zone.ZoneCommand

import scala.concurrent.duration.FiniteDuration

// for ultimately acknowledging or re-queue of the message, this is queue implementation independent
trait MessageHandle
abstract class Message[A <: ZoneCommand] {
  def handle: MessageHandle

  // A vinyldns deserialized zone command
  def command: A
}

// main consumer
trait MessageQueue {

  // receives a single message, the command should already be deserialized
  def receive(): IO[Message[ZoneCommand]]

  // receives a batch of messages
  def receiveBatch(): IO[List[Message[ZoneCommand]]]

  // puts the message back on the queue with the intention of having it re-processed again
  def requeue(message: Message[ZoneCommand]): IO[Unit]

  // removes a message from the queue, indicating completion or the message should never be processed
  def remove(message: Message[ZoneCommand]): IO[Unit]

  // alters the visibility timeout for a message on the queue.
  def changeMessageTimeout(message: Message[ZoneCommand], duration: FiniteDuration): IO[Unit]

  // sends a zone command to the queue, the queue will need to serialize it (if necessary) first
  def send(command: ZoneCommand): IO[Unit]

  // sends a batch of messages to the queue, the queue should serialize those and return List[IO[Message]]
  // where each IO could individually fail.  Note: retry semantics is not a requirement of the queue implementation
  def sendBatch(messages: List[ZoneCommand]): List[IO[Message[ZoneCommand]]]
}
