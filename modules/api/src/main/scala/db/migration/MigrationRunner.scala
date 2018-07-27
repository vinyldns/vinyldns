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

package db.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.slf4j.LoggerFactory
import vinyldns.api.repository.mysql.VinylDNSJDBC
import scala.collection.JavaConverters._

object MigrationRunner {

  private val logger = LoggerFactory.getLogger("MigrationRunner")

  def main(args: Array[String]): Unit = {

    logger.info("Running migrations...")

    val migration = new Flyway()
    val dbName = VinylDNSJDBC.config.getString("name")

    // Must use the classpath to pull in both scala and sql migrations
    migration.setLocations("classpath:db/migration")
    migration.setDataSource(VinylDNSJDBC.instance.migrationDataSource)
    migration.setSchemas(dbName)
    val placeholders = Map("dbName" -> dbName)
    migration.setPlaceholders(placeholders.asJava)

    // Runs ALL flyway migrations including SQL and scala
    try {
      migration.migrate()
      logger.info("migrations complete")
      System.exit(0)
    } catch {
      case fe: FlywayException =>
        logger.error("migrations failed!", fe)

        // Repair will fix meta data issues (if any) in the flyway database table.  Recommended when
        // a catastrophic failure occurs
        migration.repair()
        System.exit(1)
    }
  }
}
