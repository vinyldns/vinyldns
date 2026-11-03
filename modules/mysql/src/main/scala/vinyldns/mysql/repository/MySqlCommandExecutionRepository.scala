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

package vinyldns.mysql.repository

import cats.effect.IO
import scalikejdbc._
import vinyldns.core.queue.{CommandExecutionRepository, CommandIdentity}
import vinyldns.core.queue.CommandExecutionRepository._

import scala.concurrent.duration.FiniteDuration

class MySqlCommandExecutionRepository(connectionPoolName: Symbol = 'default)
    extends CommandExecutionRepository {
  def claim(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Claim] = IO {
    NamedDB(connectionPoolName).localTx { implicit session =>
      sql"""INSERT INTO queue_command_execution(command_key, payload_digest)
            VALUES (${identity.key}, ${identity.digest})
            ON DUPLICATE KEY UPDATE command_key=command_key""".update().apply()
      val state =
        sql"""SELECT payload_digest, completed,
                     COALESCE(lease_until > CURRENT_TIMESTAMP(3), FALSE) AS leased
              FROM queue_command_execution WHERE command_key=${identity.key} FOR UPDATE"""
          .map(rs => (rs.string("payload_digest"), rs.boolean("completed"), rs.boolean("leased")))
          .single()
          .apply()
          .getOrElse(throw new IllegalStateException("Queue execution state disappeared"))
      state match {
        case (digest, _, _) if digest != identity.digest => Conflict
        case (_, true, _) => Completed
        case (_, _, true) => Busy
        case _ =>
          sql"""UPDATE queue_command_execution
                SET owner=$owner,
                    lease_until=TIMESTAMPADD(SECOND, ${lease.toSeconds}, CURRENT_TIMESTAMP(3))
                WHERE command_key=${identity.key}""".update().apply()
          Acquired
      }
    }
  }

  def renew(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Unit] = IO {
    NamedDB(connectionPoolName).localTx { implicit session =>
      val changed =
        sql"""UPDATE queue_command_execution
              SET lease_until=TIMESTAMPADD(SECOND, ${lease.toSeconds}, CURRENT_TIMESTAMP(3))
              WHERE command_key=${identity.key} AND owner=$owner AND completed=FALSE
                AND lease_until > CURRENT_TIMESTAMP(3)""".update().apply()
      if (changed != 1) throw LostLease(identity.commandId)
    }
  }

  def complete(identity: CommandIdentity, owner: String): IO[Unit] = IO {
    NamedDB(connectionPoolName).localTx { implicit session =>
      val changed =
        sql"""UPDATE queue_command_execution
              SET completed=TRUE, owner=NULL, lease_until=NULL
              WHERE command_key=${identity.key} AND owner=$owner AND completed=FALSE
                AND lease_until > CURRENT_TIMESTAMP(3)""".update().apply()
      if (changed != 1) throw LostLease(identity.commandId)
    }
  }

  def release(identity: CommandIdentity, owner: String): IO[Unit] = IO {
    NamedDB(connectionPoolName).localTx { implicit session =>
      sql"""UPDATE queue_command_execution SET owner=NULL, lease_until=NULL
            WHERE command_key=${identity.key} AND owner=$owner AND completed=FALSE"""
        .update()
        .apply()
      ()
    }
  }
}
