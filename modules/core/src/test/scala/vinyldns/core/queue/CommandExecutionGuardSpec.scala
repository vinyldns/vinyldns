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

package vinyldns.core.queue

import java.util.concurrent.atomic.AtomicLong

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Deferred
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.queue.CommandExecutionGuard._
import vinyldns.core.queue.CommandExecutionRepository._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CommandExecutionGuardSpec extends AnyWordSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private val identity = CommandIdentity("test-id", "test-key", "test-digest")

  "Command replay protection" should {
    "skip completed commands across guard instances, even if transport acknowledgement was lost" in {
      val repo = new TestCommandExecutionRepository()
      val guard = new CommandExecutionGuard(repo)
      guard
        .execute(identity)(IO.pure(true))(identity => identity)
        .unsafeRunSync() shouldBe Executed(true)
      val restarted = new CommandExecutionGuard(repo)
      restarted
        .execute(identity)(IO.raiseError[Boolean](new RuntimeException("must not execute")))(
          identity => identity
        )
        .unsafeRunSync() shouldBe AlreadyCompleted
    }

    "allow retryable outcomes and exceptions to be retried" in {
      val guard = new CommandExecutionGuard(new TestCommandExecutionRepository())
      guard
        .execute(identity)(IO.pure(false))(identity => identity)
        .unsafeRunSync() shouldBe Executed(false)
      val failure = new RuntimeException("retry")
      guard
        .execute(identity)(IO.raiseError[Boolean](failure))(identity => identity)
        .attempt
        .unsafeRunSync() shouldBe Left(failure)
      guard
        .execute(identity)(IO.pure(true))(identity => identity)
        .unsafeRunSync() shouldBe Executed(true)
    }

    "reject reuse of a signed command ID with different content" in {
      val guard = new CommandExecutionGuard(new TestCommandExecutionRepository())
      guard.execute(identity)(IO.pure(true))(identity => identity).unsafeRunSync()
      guard
        .execute(identity.copy(digest = "different"))(IO.pure(true))(identity => identity)
        .unsafeRunSync() shouldBe IdentityConflict
    }

    "exclude concurrent duplicate workers and release a cancelled attempt" in {
      val guard = new CommandExecutionGuard(new TestCommandExecutionRepository())
      val run = for {
        started <- Deferred[IO, Unit]
        finish <- Deferred[IO, Boolean]
        first <- guard
          .execute(identity)(started.complete(()) *> finish.get)(identity => identity)
          .start
        _ <- started.get
        duplicate <- guard.execute(identity)(IO.pure(true))(identity => identity)
        _ <- first.cancel
        retry <- guard.execute(identity)(IO.pure(true))(identity => identity)
      } yield (duplicate, retry)
      run.timeout(5.seconds).unsafeRunSync() shouldBe (InProgress, Executed(true))
    }

    "recover an expired claim without allowing its former owner to complete it" in {
      val clock = new AtomicLong(1000)
      val repo = new TestCommandExecutionRepository(() => clock.get())
      repo.claim(identity, "first", 1.minute).unsafeRunSync() shouldBe Acquired
      clock.addAndGet(61000)
      repo.claim(identity, "second", 1.minute).unsafeRunSync() shouldBe Acquired
      repo.complete(identity, "first").attempt.unsafeRunSync() shouldBe Left(
        LostLease(identity.commandId)
      )
      repo.complete(identity, "second").unsafeRunSync()
      repo.claim(identity, "third", 1.minute).unsafeRunSync() shouldBe Completed
    }

    "renew the execution lease while a handler is running" in {
      val renewed = Deferred[IO, Unit].unsafeRunSync()
      val repo = new TestCommandExecutionRepository() {
        override def renew(
            identity: CommandIdentity,
            owner: String,
            lease: FiniteDuration
        ): IO[Unit] =
          super.renew(identity, owner, lease) *> renewed.complete(())
      }
      val guard = new CommandExecutionGuard(repo, 3.seconds)
      guard
        .execute(identity)(renewed.get.as(true))(identity => identity)
        .timeout(10.seconds)
        .unsafeRunSync() shouldBe Executed(true)
    }

    "cancel processing and release the claim when lease renewal fails" in {
      val repo = new TestCommandExecutionRepository() {
        override def renew(
            identity: CommandIdentity,
            owner: String,
            lease: FiniteDuration
        ): IO[Unit] =
          IO.raiseError(LostLease(identity.commandId))
      }
      val guard = new CommandExecutionGuard(repo, 3.seconds)
      guard
        .execute(identity)(IO.never.as(true))(identity => identity)
        .attempt
        .timeout(10.seconds)
        .unsafeRunSync() shouldBe Left(LostLease(identity.commandId))
      repo.claim(identity, "retry", 1.minute).unsafeRunSync() shouldBe Acquired
    }
  }
}
