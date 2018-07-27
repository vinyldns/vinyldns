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

package vinyldns.api.route

import org.json4s.JValue
import scalaz.Scalaz._
import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.domain.zone.{ACLRule, ACLRuleInfo, AccessLevel}

trait ACLJsonProtocol extends JsonValidation {

  val aclSerializers = Seq(
    ACLRuleInfoSerializer,
    JsonEnumV(AccessLevel),
    JsonV[ACLRule]
  )

  /* Used for adding rules to the zone acl */
  case object ACLRuleInfoSerializer extends ValidationSerializer[ACLRuleInfo] {
    override def fromJson(js: JValue): JsonDeserialized[ACLRuleInfo] = {
      val deserialized = (
        (js \ "accessLevel").required(AccessLevel, "Missing ACLRule.accessLevel")
          |@| (js \ "description").optional[String]
          |@| (js \ "userId").optional[String]
          |@| (js \ "groupId").optional[String]
          |@| (js \ "recordMask").optional[String]
          |@| (js \ "recordTypes").default[Set[RecordType]](Set.empty[RecordType])
          |@| (js \ "displayName").optional[String]
      )(ACLRuleInfo.apply)

      deserialized.check(
        ("Cannot specify both a userId and a groupId", { rule =>
          !(rule.userId.isDefined && rule.groupId.isDefined)
        })
      )
    }
  }
}
