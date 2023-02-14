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

package vinyldns.core.health

import cats.effect.IO
import org.slf4j.LoggerFactory
import java.io.{PrintWriter, StringWriter}

object HealthCheck {

  type HealthCheck = IO[Either[HealthCheckError, Unit]]

  case class HealthCheckError(message: String) extends Throwable(message)

  private val logger = LoggerFactory.getLogger("HealthCheck")

  implicit class HealthCheckImprovements(io: IO[Either[Throwable, _]]) {
    def asHealthCheck(caller: Class[_]): HealthCheck =
      io.map {
        case Left(err) =>
          val errorMessage = new StringWriter
          err.printStackTrace(new PrintWriter(errorMessage))
          logger.error(s"HealthCheck for ${caller.getCanonicalName} failed. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
          val msg = Option(err.getMessage).getOrElse("no message from error")
          Left(
            HealthCheckError(s"${caller.getCanonicalName} health check failed with msg='${msg}'")
          )
        case _ => Right(())
      }
  }

}
