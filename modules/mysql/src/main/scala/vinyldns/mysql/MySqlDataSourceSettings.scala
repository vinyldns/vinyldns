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

final case class MySqlDataSourceSettings(
    poolName: String,
    driver: String,
    url: String,
    user: String,
    password: String,
    connectionTimeoutMillis: Option[Long] = None,
    idleTimeout: Option[Long] = None,
    maxLifetime: Option[Long] = None,
    maximumPoolSize: Option[Int] = None,
    minimumIdle: Option[Int] = None,
    registerMbeans: Boolean = false,
    mySqlProperties: Map[String, String] = Map()
) {

  override def toString: String = {
    val sb = new StringBuilder
    def addNamedField(name: String, field: Any): Unit =
      sb.append(name).append("=\"").append(field).append("\"; ")

    sb.append("MySqlDataSourceSettings: [")
    addNamedField("poolName", poolName)
    addNamedField("driver", driver)
    addNamedField("user", user)
    val maskedUrl = url.split('?').headOption.getOrElse("")
    addNamedField("urlQueryExcluded", maskedUrl)

    connectionTimeoutMillis.foreach(addNamedField("connectionTimeoutMillis", _))
    idleTimeout.foreach(addNamedField("idleTimeout", _))
    maxLifetime.foreach(addNamedField("maxLifetime", _))
    maximumPoolSize.foreach(addNamedField("maximumPoolSize", _))
    minimumIdle.foreach(addNamedField("minimumIdle", _))

    addNamedField("registerMbeans", registerMbeans)
    addNamedField("mySqlProperties", mySqlProperties)
    sb.append("]")

    sb.toString
  }
}

object MySqlDataSourceSettings {
  def apply(config: MySqlConnectionConfig, poolName: String): MySqlDataSourceSettings =
    new MySqlDataSourceSettings(
      poolName,
      config.driver,
      config.url,
      config.user,
      config.password,
      config.connectionTimeoutMillis,
      config.idleTimeout,
      config.maxLifetime,
      config.maximumPoolSize,
      config.minimumIdle,
      config.registerMbeans,
      config.mySqlProperties
    )
}
