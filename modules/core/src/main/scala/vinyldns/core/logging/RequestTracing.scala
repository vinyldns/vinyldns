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

package vinyldns.core.logging

import java.util.UUID

/** Helper methods for request tracing */
object RequestTracing {
  val requestIdHeaderName = "X-VinylDNS-TraceId"

  /** Extracts the trace identifier from a map of headers */
  def extractTraceId(headerMap: Map[String, String]): Option[String] =
    headerMap.get(requestIdHeaderName)

  /** Generates a trace identifier
    *
    * NOTE: this is impure and lacks referential transparency */
  def generateTraceId: String =
    UUID.randomUUID().toString

  /** Creates a trace header name/value pair
    *
    * NOTE: this is impure and lacks referential transparency */
  def createTraceHeader: (String, String) = (requestIdHeaderName, generateTraceId)

  /** Retrieves the trace header name/value pair from the given headerMap.
    * If the header does not exist, creates a new value using [[generateTraceId]].
    *
    * NOTE: this is impure and lacks referential transparency */
  def extractTraceHeader(headerMap: Map[String, String]): (String, String) =
    requestIdHeaderName -> headerMap.getOrElse(requestIdHeaderName, generateTraceId)

}
