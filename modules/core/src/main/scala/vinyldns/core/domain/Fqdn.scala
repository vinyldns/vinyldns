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

  // Everything up to the first dot / period
  def firstLabel: String = fqdn.substring(0, fqdn.indexOf('.'))

  // Everything up to the first dot, includes the dot to make it absolute
  def firstLabelAbsolute: String = fqdn.substring(0, fqdn.indexOf('.') + 1)

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

  // Combines record name and zone name to create a valid fqdn
  def merge(recordName: String, zoneName: String): Fqdn = {
    def dropTrailingDot(value: String): String =
      if (value.endsWith(".")) value.dropRight(1) else value

    val rname = dropTrailingDot(recordName)
    val zname = dropTrailingDot(zoneName)

    val zIndex = rname.lastIndexOf(zname)
    if (zIndex > 0) {
      // zone name already there, or record name = zone name, so just return
      Fqdn(rname + ".")
    } else {
      // zone name not in record name so combine
      Fqdn(s"$rname.$zname.")
    }
  }
}
