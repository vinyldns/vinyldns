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

package controllers

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class PortalEventServiceSpec extends Specification {
  private val service = new PortalEventService()

  "PortalEventService" should {
    "validate a generic event and generate a correlation ID" in {
      val result = service.validate(Json.obj(
        "feature" -> "record_set_search",
        "action" -> "csv_export",
        "status" -> "success",
        "entityType" -> "record_set"
      ))

      result must beRight.like { case event =>
        event.feature must beSome("record_set_search")
        event.correlationId must beMatching("[0-9a-f-]{36}")
      }
    }

    "reject invalid status, duration, and unsupported fields" in {
      val invalidEvents = Seq(
        Json.obj("feature" -> "records", "action" -> "export", "status" -> "pending"),
        Json.obj("feature" -> "records", "action" -> "export", "status" -> "success", "durationMs" -> -1),
        Json.obj("feature" -> "records", "action" -> "export", "status" -> "success", "username" -> "spoofed"),
        Json.obj("feature" -> "records", "action" -> "export", "status" -> "success", "metadata" -> Json.obj())
      )

      invalidEvents.forall(event => service.validate(event).isLeft) must beTrue
    }

    "accept a bounded API error message only for failed events" in {
      val message = "RecordSet with name already-exists and type A already exists in zone ok."
      val failure = Json.obj(
        "feature" -> "record_create",
        "action" -> "create",
        "status" -> "failure",
        "errorCode" -> "OPERATION_FAILED",
        "errorMessage" -> message
      )
      val successWithError = failure ++ Json.obj("status" -> "success")
      val oversizedMessage = failure ++ Json.obj("errorMessage" -> ("x" * 513))

      service.validate(failure) must beRight.like { case event =>
        event.errorCode must beSome("OPERATION_FAILED")
        event.errorMessage must beSome(message)
      }
      service.validate(successWithError) must beLeft
      service.validate(oversizedMessage) must beLeft
    }

    "validate export events and strip control characters from string fields" in {
      val payload = Json.obj(
        "type" -> "zone",
        "recordTypes" -> "A\rAAAA",
        "query" -> "host\u0000.example",
        "rows" -> 12
      )

      service.validate(payload) must beRight.like { case event =>
        event.exportType must beSome("zone")
        event.recordTypes must beSome("AAAAA")
        event.query must beSome("host.example")
        event.rows must beSome(12L)
      }
    }

    "reject export events with unknown fields, invalid types, or invalid row counts" in {
      val valid = Json.obj("type" -> "search", "rows" -> 0)
      val invalid = Seq(
        valid ++ Json.obj("username" -> "spoofed"),
        valid ++ Json.obj("type" -> "other"),
        valid ++ Json.obj("rows" -> -1),
        valid ++ Json.obj("rows" -> 1.5)
      )

      invalid.forall(event => service.validate(event).isLeft) must beTrue
      service.validate(valid ++ Json.obj("query" -> ("x" * 513))) must beRight.like { case event =>
        event.query must beSome("x" * 512)
      }
    }

    "record export payloads through the same recorder as generic events" in {
      val payload = Json.obj("type" -> "search", "rows" -> 0)
      service.record("test-user", payload) must beRight
    }
  }
}
