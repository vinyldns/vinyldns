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

package vinyldns.mysql

case class MySqlConnectionSettings(
    name: String,
    driver: String,
    url: String,
    user: String,
    password: String,
    poolMaxSize: Int,
    connectionTimeoutMillis: Long,
    maxLifeTime: Long,
    migrationSettings: Option[MySqlMigrationSettings]
)

case class MySqlMigrationSettings(
    migrationUrl: String,
    migrationSchemaTable: Option[String],
    poolMaxSize: Int = 3
)
