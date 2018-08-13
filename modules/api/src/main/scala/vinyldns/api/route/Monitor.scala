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

package vinyldns.api.route

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import nl.grons.metrics.scala.{Histogram, Meter, MetricName}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.Instrumented

import scala.collection._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait Monitored {

  def monitor[T](name: String)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val startTime = System.currentTimeMillis()

    // invoke the function yielding a new future
    f.andThen {
      case Success(ok) =>
        getMonitor(name).capture(System.currentTimeMillis() - startTime, success = true)
        ok
      case Failure(error) =>
        getMonitor(name).capture(System.currentTimeMillis() - startTime, success = false)
        throw error
    }
  }

  def time[T](id: String)(
      f: => Future[T])(implicit ec: ExecutionContext, logger: Logger): Future[T] = {
    val startTime = System.currentTimeMillis
    def duration: Double = (System.currentTimeMillis - startTime) / 1000.0

    Future
      .successful(logger.info(s"Starting $id"))
      .flatMap(_ => f)
      .andThen {
        case Success(t) =>
          logger.info(s"Finished $id; success=true; duration=$duration seconds")
          t

        case Failure(e) =>
          logger.error(s"Finished $id; success=false; duration=$duration seconds", e)
          e
      }
  }

  /* Separate function makes it easier to test */
  private[route] def getMonitor(name: String) = Monitor(name)
}

/**
  * Holds a set of monitors that are used to monitor the traffic into the API
  */
object Monitor {

  lazy val monitors: mutable.Map[String, Monitor] = concurrent.TrieMap.empty

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
  override lazy val metricBaseName = MetricName("vinyldns.api")

  val latency: Histogram = metrics.histogram(name, "latency")
  val errors: Meter = metrics.meter(name, "errorRate")
  val logger: Logger = LoggerFactory.getLogger(classOf[Monitor])

  def duration(startTimeInMillis: Long): Long = System.currentTimeMillis() - startTimeInMillis

  // used to record stats about an http request / response
  def record(startTime: Long): Any => Any = {
    case res: Complete =>
      capture(duration(startTime), res.response.status.intValue < 500)
      res

    case rej: Rejected =>
      capture(duration(startTime), success = true)
      rej

    case resp: HttpResponse =>
      capture(duration(startTime), resp.status.intValue < 500)
      resp

    case Failure(t) =>
      fail(duration(startTime))
      throw t

    case f: akka.actor.Status.Failure =>
      fail(duration(startTime))
      f

    case e: Throwable =>
      fail(duration(startTime))
      throw e

    case x =>
      x
  }

  def capture(duration: Long, success: Boolean): Unit = {
    logger.info(Monitor.logEntry(name, duration, success))
    latency += duration
    if (!success) errors.mark()
  }

  def fail(duration: Long): Unit = {
    logger.info(Monitor.logEntry(name, duration, success = false))
    latency += duration
    errors.mark()
  }
}
