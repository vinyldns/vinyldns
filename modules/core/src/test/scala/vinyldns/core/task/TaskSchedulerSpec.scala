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
  
  override def beforeEach() = Mockito.reset(mockRepo)

  "TaskScheduler" should {
    "run a scheduled task" in {
      val mockTask = mock[Task]
      doReturn("test").when(mockTask).name
      doReturn(IO.unit).when(mockTask).run()
      doReturn(IO.pure(true)).when(mockRepo).claimTask("test", 1.seconds)
      doReturn(IO.unit).when(mockRepo).releaseTask("test")

      TaskScheduler.schedule(mockTask, 1.seconds, mockRepo).take(1).compile.drain.unsafeRunSync()

      verify(mockTask).run()
      verify(mockRepo).claimTask("test", 1.seconds)
      verify(mockRepo).releaseTask("test")
    }

    "release the task even on error" in {
      val mockTask = mock[Task]
      doReturn("test").when(mockTask).name
      doReturn(IO.raiseError(new RuntimeException("fail"))).when(mockTask).run()
      doReturn(IO.pure(true)).when(mockRepo).claimTask("test", 1.seconds)
      doReturn(IO.unit).when(mockRepo).releaseTask("test")

      TaskScheduler.schedule(mockTask, 1.seconds, mockRepo).take(1).compile.drain.unsafeRunSync()
      verify(mockRepo).releaseTask("test")
    }
  }
}
