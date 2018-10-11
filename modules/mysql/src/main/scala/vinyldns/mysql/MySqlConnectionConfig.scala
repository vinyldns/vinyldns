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

final case class MySqlConnectionConfig(
    name: String,
    driver: String,
    migrationUrl: String,
    url: String,
    user: String,
    password: String,
    migrationSchemaTable: Option[String],
    // Optional hikari settings
    // see https://github.com/brettwooldridge/HikariCP#frequently-used
    connectionTimeoutMillis: Option[Long],
    idleTimeout: Option[Long],
    minimumIdle: Option[Int],
    maximumPoolSize: Option[Int],
    maxLifeTime: Option[Long],
    registerMbeans: Boolean = false,
    // MySql performance settings, should be stored in a .properties file
    // see https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
    mySqlProperties: Map[String, String] = Map(),
)
