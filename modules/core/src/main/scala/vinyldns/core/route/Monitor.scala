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
import nl.grons.metrics.scala.{Histogram, Meter, MetricName}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.Instrumented
import java.io.{PrintWriter, StringWriter}
import scala.collection._

trait Monitored {

  def monitor[T](name: String)(f: => IO[T]): IO[T] = {
    val startTime = System.currentTimeMillis()

    // invoke the function yielding a new future
    f.attempt.flatMap {
      case Right(ok) =>
        getMonitor(name).capture(System.currentTimeMillis() - startTime, success = true)
        IO(ok)
      case Left(error) =>
        getMonitor(name).capture(System.currentTimeMillis() - startTime, success = false)
        IO.raiseError(error)
    }
  }

  def time[T](id: String)(f: => IO[T])(implicit logger: Logger): IO[T] = {
    val startTime = System.currentTimeMillis
    def duration: Double = (System.currentTimeMillis - startTime) / 1000.0

    IO.pure(logger.info(s"Starting $id"))
      .flatMap(_ => f)
      .attempt
      .flatMap {
        case Right(t) =>
          logger.info(s"Finished $id; success=true; duration=$duration seconds")
          IO(t)

        case Left(e) =>
          val errorMessage = new StringWriter
          e.printStackTrace(new PrintWriter(errorMessage))
          logger.error(s"Finished $id; success=false; duration=$duration seconds. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
          IO.raiseError(e)
      }
  }

  /* Separate function makes it easier to test */
  private[route] def getMonitor(name: String) = Monitor(name)
}

/**
  * Holds a set of monitors that are used to monitor the traffic into the API
  */
object Monitor {

  lazy val monitors: mutable.Map[String, Monitor] = scala.collection.concurrent.TrieMap.empty

  def apply(name: String): Monitor = monitors.getOrElseUpdate(name, new Monitor(name))

  def logEntry(monitorName: String, durationMillis: Long, success: Boolean): String = {
    val sb = new StringBuilder
    sb.append("monitor='").append(monitorName).append("'")
    sb.append(" millis=").append(durationMillis)
    sb.append(" fail=").append(if (success) 0 else 1)
    sb.toString
  }
}

/**
  * Monitor for code that you want to capture metrics for
  *
  * @param name The name given to the thing we are monitoring, should be unique in the system
  */
class Monitor(val name: String) extends Instrumented {
  override lazy val metricBaseName = MetricName("vinyldns.core")

  val latency: Histogram = metrics.histogram(name, "latency")
  val errors: Meter = metrics.meter(name, "errorRate")
  val logger: Logger = LoggerFactory.getLogger(classOf[Monitor])

  def duration(startTimeInMillis: Long): Long = System.currentTimeMillis() - startTimeInMillis

  def capture(duration: Long, success: Boolean): Unit = {
    if (logger.isDebugEnabled) {
      logger.debug(Monitor.logEntry(name, duration, success))
    }
    // Testing
    latency += duration
    if (!success) errors.mark()
  }

  def fail(duration: Long): Unit = {
    logger.warn(Monitor.logEntry(name, duration, success = false))
    latency += duration
    errors.mark()
  }
}
