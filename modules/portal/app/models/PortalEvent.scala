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

package models

final case class PortalEvent(
    feature: Option[String],
    action: Option[String],
    status: Option[String],
    entityType: Option[String],
    durationMs: Option[Long],
    errorCode: Option[String],
    errorMessage: Option[String],
    exportType: Option[String],
    recordTypes: Option[String],
    query: Option[String],
    rows: Option[Long],
    username: Option[String],
    zoneId: Option[String],
    recordId: Option[String],
    groupId: Option[String],
    correlationId: String
)
