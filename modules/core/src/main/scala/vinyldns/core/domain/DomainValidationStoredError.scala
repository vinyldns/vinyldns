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

package vinyldns.core.domain

import vinyldns.core.domain.DomainValidationErrorType.DomainValidationErrorType

case class DomainValidationStoredError(errorType: DomainValidationErrorType, message: String)

object DomainValidationStoredError {
  def apply(error: DomainValidationError): DomainValidationStoredError =
    new DomainValidationStoredError(DomainValidationErrorType.from(error), error.message)
}

object DomainValidationErrorType extends Enumeration {
  type DomainValidationErrorType = Value
  // NOTE: once defined, an error code cannot be changed!
  val ZDE, Unknown = Value

  def from(error: DomainValidationError): DomainValidationErrorType = error match {
    case _: ZoneDiscoveryError => ZDE
    case _ => Unknown
  }
}
