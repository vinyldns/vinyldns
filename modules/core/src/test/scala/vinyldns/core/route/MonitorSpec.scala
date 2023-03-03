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

package vinyldns.core.route

import cats.effect._
import nl.grons.metrics.scala.{Histogram, Meter}
import org.scalatest.concurrent.ScalaFutures
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.OneInstancePerTest
import org.slf4j.Logger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MonitorSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with OneInstancePerTest
    with ScalaFutures {

  private val mockLatency = mock[Histogram]
  private val mockErrors = mock[Meter]

  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  class TestMonitor extends Monitor("test") {
    override val latency: Histogram = mockLatency
    override val errors: Meter = mockErrors
  }

  trait TestMonitored extends Monitored {
    implicit val logger: Logger = mock[Logger]
    override private[route] def getMonitor(name: String): Monitor = new TestMonitor
  }

  class TestMonitoring extends TestMonitored {

    def doSomethingGood(): IO[String] = monitor("doSomethingGood") {
      IO { "good" }
    }

    def doSomethingBad(): IO[String] = monitor("doSomethingBad") {
      IO { throw new RuntimeException("bad") }
    }

    def timeSomethingGood(): IO[String] = time("timeSomethingGood") {
      IO { "good" }
    }

    def timeSomethingBad(): IO[String] = time("timeSomethingBad") {
      IO { throw new RuntimeException("bad") }
    }
  }

  val underTest = new TestMonitor

  "The Monitored trait" should {
    "record successful futures" in {
      val traitTest = new TestMonitoring

      whenReady(traitTest.doSomethingGood().unsafeToFuture()) { _ =>
        verify(mockLatency).+=(anyLong)
        verifyZeroInteractions(mockErrors)
      }
    }

    "record failed futures" in {
      val traitTest = new TestMonitoring

      whenReady(traitTest.doSomethingBad().unsafeToFuture().failed) { result =>
        result shouldBe a[RuntimeException]
        verify(mockLatency).+=(anyLong)
        verify(mockErrors).mark()
      }
    }

    "record start and end when timing a successful future" in {
      val traitTest = new TestMonitoring

      whenReady(traitTest.timeSomethingGood().unsafeToFuture()) { _ =>
        val msgCaptor = ArgumentCaptor.forClass(classOf[String])
        verify(traitTest.logger, times(2)).info(msgCaptor.capture())
        verify(traitTest.logger, never()).error(anyString, any(classOf[Throwable]))

        val firstMessage = msgCaptor.getAllValues.get(0)
        val secondMessage = msgCaptor.getAllValues.get(1)

        firstMessage shouldBe "Starting timeSomethingGood"
        secondMessage should include("Finished timeSomethingGood; success=true; duration=")
      }
    }

    "record start and end when timing a failed future" in {
      val traitTest = new TestMonitoring

      whenReady(traitTest.timeSomethingBad().unsafeToFuture().failed) { _ =>
        val msgCaptor = ArgumentCaptor.forClass(classOf[String])
        val errorCaptor = ArgumentCaptor.forClass(classOf[String])
        verify(traitTest.logger, times(1)).info(msgCaptor.capture())
        verify(traitTest.logger, times(1)).error(errorCaptor.capture())

        msgCaptor.getValue shouldBe "Starting timeSomethingBad"
        errorCaptor.getValue should include("Finished timeSomethingBad; success=false; duration=")
      }
    }
  }

  "A Monitor" should {
    "have an overridden base name" in {
      new Monitor("foo").metricBaseName.name shouldBe "vinyldns.core"
    }
    "increment the latency and not increment the errors on success" in {
      underTest.capture(1000L, true)

      verify(mockLatency).+=(1000L)
      verifyZeroInteractions(mockErrors)
    }
    "increment the latency and the errors when capture false" in {
      underTest.capture(1000L, false)

      verify(mockLatency).+=(1000L)
      verify(mockErrors).mark()
    }
    "increment the latency and errors when fail" in {
      underTest.fail(1000L)

      verify(mockLatency).+=(1000L)
      verify(mockErrors).mark()
    }
  }

  "Monitor logEntry" should {
    "return a log entry for success" in {
      val result = Monitor.logEntry("foo", 100, success = true)
      result should include("monitor='foo'")
      result should include("millis=100")
      result should include("fail=0")
    }
    "return a log entry for failure" in {
      val result = Monitor.logEntry("foo", 100, success = false)
      result should include("monitor='foo'")
      result should include("millis=100")
      result should include("fail=1")
    }
  }
}
