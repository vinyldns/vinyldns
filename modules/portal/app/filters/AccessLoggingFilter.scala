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
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory
import play.api.mvc.{Filter, RequestHeader, Result}
import play.mvc.Http

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class AccessLoggingFilter @Inject()(
    implicit val mat: Materializer,
    executionContext: ExecutionContext)
    extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[AccessLoggingFilter])

  def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    val resultFuture = next(request)

    resultFuture.foreach(result => {
      if (!request.uri.contains("/public") && !request.uri.contains("/assets")) {
        val accessInfo = Map(
          "http" ->
            Map("url" -> Map("path" -> request.path, "query" -> request.rawQueryString).asJava).asJava,
          "request" -> Map("method" -> request.method).asJava,
          "response" -> Map("status_code" -> result.header.status).asJava,
          "client" -> Map("address" -> request.remoteAddress).asJava,
          "user_agent" ->
            Map(
              "original" -> request.headers
                .get(Http.HeaderNames.USER_AGENT)
                .getOrElse("unknown")).asJava
        ).asJava

        logger.info("", StructuredArguments.entries(accessInfo))
      }
    })

    resultFuture
  }
}
