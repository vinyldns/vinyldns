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

package vinyldns.client.models.record

import upickle.default._
import vinyldns.client.models.OptionRW

case class RecordData(
    address: Option[String] = None,
    cname: Option[String] = None,
    preference: Option[Int] = None,
    exchange: Option[String] = None,
    nsdname: Option[String] = None,
    ptrdname: Option[String] = None,
    mname: Option[String] = None,
    rname: Option[String] = None,
    serial: Option[Long] = None,
    refresh: Option[Long] = None,
    retry: Option[Long] = None,
    expire: Option[Long] = None,
    minimum: Option[Long] = None,
    text: Option[String] = None,
    priority: Option[Int] = None,
    weight: Option[Int] = None,
    port: Option[Int] = None,
    target: Option[String] = None,
    algorithm: Option[Int] = None,
    `type`: Option[Int] = None,
    fingerprint: Option[String] = None,
    keytag: Option[Int] = None,
    digesttype: Option[Int] = None,
    digest: Option[String] = None
)

object RecordData extends OptionRW {
  implicit val rw: ReadWriter[RecordData] = macroRW
}
