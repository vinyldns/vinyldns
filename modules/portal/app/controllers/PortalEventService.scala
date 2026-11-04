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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import models.PortalEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.StringMapMessage
import play.api.libs.json._

@Singleton
class PortalEventService @Inject() () {
  private val logger = LogManager.getLogger(classOf[PortalEventService])
  private val allowedFields = Set(
    "feature",
    "action",
    "status",
    "entityType",
    "durationMs",
    "errorCode",
    "errorMessage",
    "type",
    "recordTypes",
    "query",
    "rows"
  )

  def record(username: String, payload: JsValue): Either[String, Unit] =
    validate(payload).map { event =>
      val eventName = if (event.exportType.isDefined) "export_event" else "portal_event"
      val message = (Seq("event" -> eventName, "user" -> username) ++ portalEventFields(event))
        .foldLeft(new StringMapMessage()) { case (msg, (key, value)) => msg.`with`(key, value) }
      // ECS layout writes MapMessage entries as searchable structured fields.
      logger.info(message)
    }

  private def portalEventFields(event: PortalEvent): Seq[(String, String)] =
    Seq(
      "feature" -> event.feature,
      "action" -> event.action,
      "status" -> event.status,
      "entityType" -> event.entityType,
      "type" -> event.exportType,
      "recordTypes" -> event.recordTypes,
      "query" -> event.query,
      "rows" -> event.rows.map(_.toString),
      "durationMs" -> event.durationMs.map(_.toString),
      "errorCode" -> event.errorCode,
      "errorMessage" -> event.errorMessage
    ).collect { case (key, Some(value)) => key -> value } :+ ("correlationId" -> event.correlationId)

  // An event carrying a `type` is an export event (requires type and rows); otherwise it needs feature, action and status.
  private[controllers] def validate(payload: JsValue): Either[String, PortalEvent] = payload match {
    case event: JsObject if (event.keys -- allowedFields).nonEmpty => Left("Unsupported event fields")
    case event: JsObject =>
      val isExport = event.keys.contains("type")
      for {
        feature <- requiredWhen(!isExport, "feature", optionalString(event, "feature", 64, Some(identifierPattern)))
        action <- requiredWhen(!isExport, "action", optionalString(event, "action", 64, Some(identifierPattern)))
        status <- requiredWhen(!isExport, "status", optionalString(event, "status", 16, Some("^(success|failure)$".r)))
        entityType <- optionalString(event, "entityType", 32, Some(identifierPattern))
        durationMs <- optionalNumber(event, "durationMs", 3600000)
        errorCode <- optionalString(event, "errorCode", 64, Some(identifierPattern))
        errorMessage <- optionalErrorMessage(event)
        exportType <- requiredWhen(isExport, "type", optionalString(event, "type", 16, Some("^(zone|search)$".r)))
        recordTypes <- optionalString(event, "recordTypes", 256, None, stripControlCharacters = true)
        query <- optionalString(event, "query", 512, None, stripControlCharacters = true)
        rows <- requiredWhen(isExport, "rows", optionalNumber(event, "rows", Long.MaxValue))
        _ <- if (status.contains("failure") || (errorCode.isEmpty && errorMessage.isEmpty)) Right(())
          else Left("Error details are only valid for failed events")
      } yield PortalEvent(
        feature,
        action,
        status,
        entityType,
        durationMs,
        errorCode,
        errorMessage,
        exportType,
        recordTypes,
        query,
        rows,
        UUID.randomUUID().toString
      )
    case _ => Left("Event payload must be a JSON object")
  }

  private def requiredWhen[A](required: Boolean, field: String, value: Either[String, Option[A]]): Either[String, Option[A]] =
    value.flatMap(v => if (required && v.isEmpty) Left(s"Invalid $field") else Right(v))

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

  private def optionalNumber(event: JsObject, field: String, max: Long): Either[String, Option[Long]] =
    event.value.get(field) match {
      case None | Some(JsNull) => Right(None)
      case Some(JsNumber(value)) if value.isWhole && value >= 0 && value <= BigDecimal(max) =>
        Right(Some(value.toLong))
      case _ => Left(s"Invalid $field")
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
