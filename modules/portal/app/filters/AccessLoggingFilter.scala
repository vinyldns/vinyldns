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

package filters

import akka.stream.Materializer
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.mvc.{Filter, RequestHeader, Result}
import play.mvc.Http
import vinyldns.core.logging.RequestTracing

import scala.concurrent.{ExecutionContext, Future}

class AccessLoggingFilter @Inject() (
    implicit val mat: Materializer,
    executionContext: ExecutionContext
) extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[AccessLoggingFilter])

  def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    val (trackingHeaderName, trackingHeaderValue) = RequestTracing.createTraceHeader
    val newRequest =
      request.withHeaders(
        request.headers.add(trackingHeaderName -> trackingHeaderValue)
      )
    val resultFuture = next(newRequest)

    resultFuture.foreach(result => {
      if (!request.uri.contains("/public") && !request.uri.contains("/assets")) {
        val traceId =
          RequestTracing.extractTraceId(newRequest.headers.toSimpleMap).getOrElse("(unknown)")
        val userAgent = newRequest.headers.get(Http.HeaderNames.USER_AGENT).getOrElse("unknown")

        // This will get logged at the end of the request.  We eventually need to correlate all events during the request
        // with this request identifier.
        val msg = s"Request: method=${newRequest.method}, path=${newRequest.uri}, remote_address=${newRequest.remoteAddress}, " +
          s"user_agent=$userAgent | " +
          s"trace.id=$traceId |" +
          s"Response: status_code=${result.header.status} "
        logger.info(msg)
      }
    })

    resultFuture
  }
}
