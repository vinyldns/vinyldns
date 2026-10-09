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

package vinyldns.core.domain.zone.generate

import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s.JsonAST.JValue
import vinyldns.core.domain.zone.ZoneStatus
import vinyldns.core.domain.zone.ZoneStatus.ZoneStatus
import vinyldns.core.domain.zone.generate.GenerateZoneChangeType.GenerateZoneChangeType

object GenerateZoneChangeType extends Enumeration {
  type GenerateZoneChangeType = Value
  val Create, Update, Delete = Value
}

final case class GenerateZone(
                               groupId: String,
                               email: String,
                               provider: String,
                               zoneName: String,
                               status:  ZoneStatus = ZoneStatus.Active,
                               providerParams: Map[String, JValue] = Map.empty,
                               response: Option[ZoneGenerationResponse] = None,
                               id: String = UUID.randomUUID().toString,
                               created: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS),
                               updated: Option[Instant] = None
                     ){
    override def toString: String = {
      val sb = new StringBuilder
      sb.append("GenerateZone: [")
      sb.append("id=\"").append(id).append("\"; ")
      sb.append("groupId=\"").append(groupId).append("\"; ")
      sb.append("email=\"").append(email).append("\"; ")
      sb.append("provider=\"").append(provider).append("\"; ")
      sb.append("zoneName=\"").append(zoneName).append("\"; ")
      sb.append("status=\"").append(status).append("\"; ")
      sb.append("created=\"").append(created).append("\"; ")
      updated.map(sb.append("updated=\"").append(_).append("\"; "))
      sb.append("]")
      sb.toString
    }
}

object GenerateZone {
  def apply(zoneGenerationInput: ZoneGenerationInput): GenerateZone = {
    import zoneGenerationInput._

    GenerateZone(
      groupId,
      email,
      provider,
      zoneName,
      providerParams = providerParams
    )
  }
}

case class ZoneGenerationResponse(
                                   responseCode: Option[Int],
                                   status: Option[String],
                                   message: Option[JValue],
                                   changeType: GenerateZoneChangeType
                                 )

// Client-supplied request to generate a zone. Server-owned fields (id, status, response)
// are intentionally not part of this model so clients cannot set them; the server assigns
// them when constructing the GenerateZone.
case class ZoneGenerationInput(
    groupId: String,
    email: String,
    provider: String,
    zoneName: String,
    providerParams: Map[String, JValue] = Map.empty
)
