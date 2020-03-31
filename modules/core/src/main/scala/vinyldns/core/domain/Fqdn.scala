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

import DomainHelpers.{ensureTrailingDot, removeWhitespace}

case class Fqdn(fqdn: String) {
  override def equals(obj: Any): Boolean =
    obj match {
      case Fqdn(otherFqdn) => otherFqdn.toLowerCase == fqdn.toLowerCase
      case _ => false
    }

  override def hashCode(): Int = fqdn.hashCode
}

case object Fqdn {
  def apply(fqdn: String): Fqdn =
    new Fqdn(ensureTrailingDot(removeWhitespace(fqdn)))
}
