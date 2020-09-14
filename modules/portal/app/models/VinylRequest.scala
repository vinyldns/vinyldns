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

package models

import java.io.{ByteArrayInputStream, InputStream}
import java.util

import com.amazonaws.{ReadLimitInfo, SignableRequest}
import com.amazonaws.http.HttpMethodName

object VinylDNSRequest {
  val APPLICATION_JSON = "application/json"
}

case class VinylDNSRequest(
    method: String,
    url: String,
    path: String = "",
    payload: Option[String] = None,
    parameters: util.HashMap[String, java.util.List[String]] =
      new util.HashMap[String, java.util.List[String]]()
)

class SignableVinylDNSRequest(origReq: VinylDNSRequest) extends SignableRequest[VinylDNSRequest] {

  import VinylDNSRequest._

  val contentType: String = APPLICATION_JSON

  private val headers = new util.HashMap[String, String]()
  private val parameters = origReq.parameters
  private val uri = new java.net.URI(origReq.url)
  // I hate to do this, but need to be able to set the content after creation to
  // implement the interface properly
  private var contentStream: InputStream = new ByteArrayInputStream(
    origReq.payload.getOrElse("").getBytes("UTF-8")
  )

  override def addHeader(name: String, value: String): Unit = headers.put(name, value)
  override def getHeaders: java.util.Map[String, String] = headers
  override def getResourcePath: String = origReq.path
  override def addParameter(name: String, value: String): Unit = {
    if (!parameters.containsKey(name)) parameters.put(name, new util.ArrayList[String]())
    parameters.get(name).add(value)
  }
  override def getParameters: java.util.Map[String, java.util.List[String]] = parameters
  override def getEndpoint: java.net.URI = uri
  override def getHttpMethod: HttpMethodName = HttpMethodName.valueOf(origReq.method)
  override def getTimeOffset: Int = 0
  override def getContent: InputStream = contentStream
  override def getContentUnwrapped: InputStream = getContent
  override def getReadLimitInfo: ReadLimitInfo = new ReadLimitInfo {
    override def getReadLimit: Int = -1
  }
  override def getOriginalRequestObject: Object = origReq
  override def setContent(content: InputStream): Unit = contentStream = content
}
