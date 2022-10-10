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

package vinyldns.api.config

import pureconfig.ConfigReader
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType

import scala.util.matching.Regex

final case class DottedLabelConfig(typ: RecordType, pattern: Regex)

object DottedLabelConfig {
  implicit val configReader: ConfigReader[DottedLabelConfig] =
    ConfigReader.forProduct2[DottedLabelConfig, String, Regex](
      "type",
      "pattern"
    ) {
      case (typStr, pattern) => {
        val typ: RecordType = RecordType.find(typStr) match {
          case Some(t) => t
          case None => throw new Error(s"Invalid record type: $typStr")
        }
        new DottedLabelConfig(typ, pattern)
      }
    }
}
