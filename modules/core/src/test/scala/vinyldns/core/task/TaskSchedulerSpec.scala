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

package vinyldns.core.task
import cats.effect.{ContextShift, IO, Timer}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

import scala.concurrent.duration._

class TaskSchedulerSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  private val mockRepo = mock[TaskRepository]

  class TestTask(
      val name: String,
      val timeout: FiniteDuration,
      val runEvery: FiniteDuration,
      testResult: IO[Unit] = IO.unit)
      extends Task {
    def run(): IO[Unit] = testResult
  }

  override def beforeEach() = Mockito.reset(mockRepo)

  "TaskScheduler" should {
    "run a scheduled task" in {
      val task = new TestTask("test", 5.seconds, 500.millis)
      val spied = spy(task)
      doReturn(IO.unit).when(mockRepo).saveTask(task.name)
      doReturn(IO.pure(true)).when(mockRepo).claimTask(task.name, task.timeout)
      doReturn(IO.unit).when(mockRepo).releaseTask(task.name)

      TaskScheduler.schedule(spied, mockRepo).take(1).compile.drain.unsafeRunSync()

      verify(spied).run()
      verify(mockRepo).claimTask(task.name, task.timeout)
      verify(mockRepo).releaseTask(task.name)
    }

    "release the task even on error" in {
      val task =
        new TestTask("test", 5.seconds, 500.millis, IO.raiseError(new RuntimeException("fail")))
      doReturn(IO.unit).when(mockRepo).saveTask(task.name)
      doReturn(IO.pure(true)).when(mockRepo).claimTask(task.name, task.timeout)
      doReturn(IO.unit).when(mockRepo).releaseTask(task.name)

      TaskScheduler.schedule(task, mockRepo).take(1).compile.drain.unsafeRunSync()
      verify(mockRepo).releaseTask(task.name)
    }

    "fail to start if the task cannot be saved" in {
      val task = new TestTask("test", 5.seconds, 500.millis)
      val spied = spy(task)
      doReturn(IO.raiseError(new RuntimeException("fail"))).when(mockRepo).saveTask(task.name)

      a[RuntimeException] should be thrownBy TaskScheduler
        .schedule(task, mockRepo)
        .take(1)
        .compile
        .drain
        .unsafeRunSync()
      verify(spied, never()).run()
    }
  }
}
