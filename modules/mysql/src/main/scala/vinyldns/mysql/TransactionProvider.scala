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

import cats.effect.IO
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.{ConnectionPool, DB}

import java.util.UUID

/**
  * Provides access to database transaction helper methods.
  */
trait TransactionProvider {
  private val logger: Logger = LoggerFactory.getLogger("vinyldns.mysql.TransactionProvider")

  /**
    * Synchronously executes the given `execution` function within a database transaction. Handles commit and rollback.
    *
    * @param execution The function to execute that takes a DB instance as a parameter
    * @return The result of the execution
    */
  def executeWithinTransaction[A](execution: DB => IO[A]): IO[A] = {
    IO {
      // Create a correlation ID for the database transaction
      val txId = UUID.randomUUID()
      val db = DB(ConnectionPool.borrow())
      try {
        db.autoClose(false)
        logger.debug(s"Beginning a database transaction: $txId")
        db.beginIfNotYet()
        val result = execution(db).unsafeRunSync()
        logger.debug(s"Committing database transaction: $txId")
        db.commit()
        result
      } catch {
        case e: Throwable =>
          logger.error(s"Encountered error executing function within a database transaction ($txId). Rolling back transaction.", e)
          db.rollbackIfActive()
          throw e
      } finally {
        db.close()
      }
    }
  }
}