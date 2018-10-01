package vinyldns.core.queue
import cats.effect.IO

// follow data store loading on how to load the message queue settings using pure config, and how the
// implementation loads and initializes itself.
trait MessageQueueProvider[A] {
  def load(settings: MessageQueueConfig): IO[MessageQueue[A]]
}
