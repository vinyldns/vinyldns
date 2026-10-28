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

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.PortalEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.StringMapMessage
import play.api.libs.json._

@Singleton
class PortalEventService @Inject() () {
  private val logger = LogManager.getLogger(classOf[PortalEventService])
  private val exportEventFields = Set("type", "zoneId", "recordTypes", "query", "rows")

  def record(username: String, payload: JsValue, exportEvent: Boolean = false): Either[String, Unit] =
    if (exportEvent) {
      validateExport(payload).map { case (exportType, zoneId, recordTypes, query, rows) =>
        val message = new StringMapMessage()
          .`with`("event", "export_event")
          .`with`("user", username)
          .`with`("type", exportType)
          .`with`("rows", rows.toString)
        zoneId.foreach(value => message.`with`("zoneId", value))
        recordTypes.foreach(value => message.`with`("recordTypes", value))
        query.foreach(value => message.`with`("query", value))
        logger.info(message)
      }
    } else {
      validate(payload).map { event =>
        val message = new StringMapMessage()
          .`with`("event", "portal_event")
          .`with`("schemaVersion", "1")
          .`with`("timestamp", Instant.now.toString)
          .`with`("user", username)
          .`with`("feature", event.feature)
          .`with`("action", event.action)
          .`with`("status", event.status)
          .`with`("durationMs", event.durationMs.fold("")(_.toString))
          .`with`("correlationId", event.correlationId)
        event.entityType.foreach(value => message.`with`("entityType", value))
        event.errorCode.foreach(value => message.`with`("errorCode", value))
        event.errorMessage.foreach(value => message.`with`("errorMessage", value))
        // ECS layout writes MapMessage entries as searchable structured fields.
        logger.info(message)
      }
    }

  private[controllers] def validateExport(
      payload: JsValue
  ): Either[String, (String, Option[String], Option[String], Option[String], Long)] = payload match {
    case event: JsObject if (event.keys -- exportEventFields).nonEmpty => Left("Unsupported export event fields")
    case event: JsObject =>
      for {
        exportType <- event.value.get("type") match {
          case Some(JsString(value)) if value == "zone" || value == "search" => Right(value)
          case _ => Left("Invalid type")
        }
        zoneId <- optionalString(event, "zoneId", 128, None, stripControlCharacters = true)
        recordTypes <- optionalString(event, "recordTypes", 256, None, stripControlCharacters = true)
        query <- optionalString(event, "query", 512, None, stripControlCharacters = true)
        rows <- event.value.get("rows") match {
          case Some(JsNumber(value)) if value.isWhole && value >= 0 && value <= BigDecimal(Long.MaxValue) =>
            Right(value.toLong)
          case _ => Left("Invalid rows")
        }
      } yield (exportType, zoneId, recordTypes, query, rows)
    case _ => Left("Export event payload must be a JSON object")
  }

  private[controllers] def validate(payload: JsValue): Either[String, PortalEvent] = payload match {
    case event: JsObject =>
      val allowedFields = Set(
        "feature",
        "action",
        "status",
        "entityType",
        "durationMs",
        "errorCode",
        "errorMessage"
      )
      val unknownFields = event.keys -- allowedFields
      if (unknownFields.nonEmpty) {
        Left("Unsupported event fields")
      } else {
        for {
          feature <- requiredString(event, "feature", 64, identifierPattern)
          action <- requiredString(event, "action", 64, identifierPattern)
          status <- requiredString(event, "status", 16, "^(success|failure)$".r)
          entityType <- optionalString(event, "entityType", 32, Some(identifierPattern))
          durationMs <- optionalDuration(event)
          errorCode <- optionalString(event, "errorCode", 64, Some(identifierPattern))
          errorMessage <- optionalErrorMessage(event)
          _ <- if (status == "failure" || (errorCode.isEmpty && errorMessage.isEmpty)) Right(())
            else Left("Error details are only valid for failed events")
        } yield PortalEvent(
          feature,
          action,
          status,
          entityType,
          durationMs,
          errorCode,
          errorMessage,
          UUID.randomUUID().toString
        )
      }
    case _ => Left("Event payload must be a JSON object")
  }

  private def requiredString(
      event: JsObject,
      field: String,
      maxLength: Int,
      pattern: scala.util.matching.Regex
  ): Either[String, String] =
    event.value.get(field) match {
      case Some(JsString(value)) if value.length <= maxLength && pattern.pattern.matcher(value).matches() =>
        Right(value)
      case _ => Left(s"Invalid $field")
    }

  private def optionalString(
      event: JsObject,
      field: String,
      maxLength: Int,
      pattern: Option[scala.util.matching.Regex],
      stripControlCharacters: Boolean = false
  ): Either[String, Option[String]] =
    event.value.get(field) match {
      case None | Some(JsNull) => Right(None)
      case Some(JsString(rawValue)) =>
        val value = if (stripControlCharacters) rawValue.filterNot(ch => Character.isISOControl(ch)).take(maxLength)
        else rawValue
        val matchesPattern = pattern.forall(_.pattern.matcher(value).matches())
        if (value.length <= maxLength && matchesPattern) Right(Option(value).filter(_.nonEmpty))
        else Left(s"Invalid $field")
      case _ => Left(s"Invalid $field")
    }

  private def optionalDuration(event: JsObject): Either[String, Option[Long]] =
    event.value.get("durationMs") match {
      case None | Some(JsNull) => Right(None)
      case Some(JsNumber(value)) if value.isWhole && value >= 0 && value <= 3600000 =>
        Right(Some(value.toLong))
      case _ => Left("Invalid durationMs")
    }

  private def optionalErrorMessage(event: JsObject): Either[String, Option[String]] =
    event.value.get("errorMessage") match {
      case None | Some(JsNull) => Right(None)
      case Some(JsString(value))
          if value.length <= 512 && !value.exists(ch => Character.isISOControl(ch)) =>
        Right(Option(value.trim).filter(_.nonEmpty))
      case _ => Left("Invalid errorMessage")
    }

  private val identifierPattern = "^[A-Za-z][A-Za-z0-9_.-]*$".r
}
