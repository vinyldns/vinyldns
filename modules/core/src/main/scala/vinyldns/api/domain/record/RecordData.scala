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

package vinyldns.api.domain.record

import vinyldns.api.domain.DomainHelpers.ensureTrailingDot

sealed trait RecordData

final case class AData(address: String) extends RecordData

final case class AAAAData(address: String) extends RecordData

final case class CNAMEData(cname: String) extends RecordData

object CNAMEData {
  def apply(cname: String): CNAMEData =
    new CNAMEData(ensureTrailingDot(cname))
}

final case class MXData(preference: Integer, exchange: String) extends RecordData

object MXData {
  def apply(preference: Integer, exchange: String): MXData =
    new MXData(preference, ensureTrailingDot(exchange))
}

final case class NSData(nsdname: String) extends RecordData

object NSData {
  def apply(nsdname: String): NSData =
    new NSData(ensureTrailingDot(nsdname))
}

final case class PTRData(ptrdname: String) extends RecordData

object PTRData {
  def apply(ptrdname: String): PTRData =
    new PTRData(ensureTrailingDot(ptrdname))
}

final case class SOAData(
    mname: String,
    rname: String,
    serial: Long,
    refresh: Long,
    retry: Long,
    expire: Long,
    minimum: Long)
    extends RecordData

final case class SPFData(text: String) extends RecordData

final case class SRVData(priority: Integer, weight: Integer, port: Integer, target: String)
    extends RecordData

object SRVData {
  def apply(priority: Integer, weight: Integer, port: Integer, target: String): SRVData =
    new SRVData(priority, weight, port, ensureTrailingDot(target))
}

final case class SSHFPData(algorithm: Integer, typ: Integer, fingerprint: String) extends RecordData

final case class TXTData(text: String) extends RecordData
