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

import cats.effect.{IO, Timer}
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.generate.{DuplicateGenerateZoneNameError, GenerateZone, GenerateZoneRepository, ListGeneratedZonesResults}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

import java.sql.SQLException

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class MySqlGenerateZoneRepository extends GenerateZoneRepository with ProtobufConversions with Monitored {

  private val logger = LoggerFactory.getLogger(classOf[MySqlGenerateZoneRepository])

  final val MAX_RETRIES = 10

  // Cap the number of group accessors interpolated into the IN (...) clause so we don't build
  // an unbounded parameterized query (mirrors MySqlZoneRepository.buildZoneSearchAccessorList).
  final val MAX_ACCESSORS = 30

  private final val INITIAL_RETRY_DELAY = 1.millis

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  // MySQL ER_DUP_ENTRY
  private final val DUPLICATE_KEY_ERROR_CODE = 1062

  // ER_LOCK_DEADLOCK and ER_LOCK_WAIT_TIMEOUT
  private final val TRANSIENT_ERROR_CODES = Set(1213, 1205)

  // Not an upsert: ON DUPLICATE KEY UPDATE would also fire on the unique name index and
  // silently overwrite another zone's row. save() updates by id, then inserts if new.
  private final val INSERT_GENERATE_ZONE: SQL[Nothing, NoExtractor] =
    sql"""
         |INSERT INTO generate_zone(id, name, provider, admin_group_id, response, data)
         |     VALUES ({id}, {name}, {provider}, {adminGroupId}, {response}, {data})
        """.stripMargin

  private final val UPDATE_GENERATE_ZONE: SQL[Nothing, NoExtractor] =
    sql"""
         |UPDATE generate_zone
         |   SET name = {name},
         |       provider = {provider},
         |       admin_group_id = {adminGroupId},
         |       response = {response},
         |       data = {data}
         | WHERE id = {id}
        """.stripMargin

  private final val DELETE_GENERATED_ZONE: SQLSyntax =
    sqls"DELETE FROM generate_zone WHERE id = ?"

  private final val GET_GENERATED_ZONE_BY_NAME: SQLSyntax =
    sqls"SELECT data FROM generate_zone WHERE name = ?"

  private final val GET_GENERATED_ZONE_BY_ID: SQLSyntax =
    sqls"SELECT data FROM generate_zone WHERE id = ?"

  private final val BASE_GENERATE_ZONE_SEARCH_SQL: SQLSyntax =
    sqls"SELECT gz.data FROM generate_zone gz"

  /**
    * Generated zones are only accessible to members of their admin group (there is no per-zone
    * ACL table as there is for regular zones). Only super/support admins may see all zones; 
    * everyone else is restricted to zones owned by a group they belong to, and a user who 
    * belongs to no groups sees nothing.
    */
  private def accessFilter(authPrincipal: AuthPrincipal): Option[SQLSyntax] =
    if (authPrincipal.isSystemAdmin) {
      None
    } else {
      val memberGroupIds = authPrincipal.memberGroupIds
      if (memberGroupIds.nonEmpty) {
        if (memberGroupIds.length > MAX_ACCESSORS) {
          logger.warn(
            s"User ${authPrincipal.signedInUser.userName} with id ${authPrincipal.signedInUser.id} " +
              s"is in more than $MAX_ACCESSORS groups, not all generated zones may be returned!"
          )
        }
        Some(sqls"gz.admin_group_id IN (${memberGroupIds.take(MAX_ACCESSORS)})")
      } else {
        Some(sqls"gz.admin_group_id IN ('')")
      }
    }

  def save(generateZone: GenerateZone): IO[GenerateZone] =
    monitor("repo.generateZone.save") {
      retryOnTransient(saveTx(generateZone), INITIAL_RETRY_DELAY, MAX_RETRIES)
    }

  private def saveTx(generateZone: GenerateZone): IO[GenerateZone] =
    IO {
      DB.localTx { implicit s =>
        val params = Seq(
          'id -> generateZone.id,
          'name -> generateZone.zoneName,
          'provider -> generateZone.provider,
          'adminGroupId -> generateZone.groupId,
          'response -> generateZone.response.map(r => toPB(r).toByteArray).orNull,
          'data -> toPB(generateZone).toByteArray
        )
        val updated = UPDATE_GENERATE_ZONE.bindByName(params: _*).update().apply()
        if (updated == 0) INSERT_GENERATE_ZONE.bindByName(params: _*).update().apply()
      }
      generateZone
    }.handleErrorWith {
      case e: SQLException if e.getErrorCode == DUPLICATE_KEY_ERROR_CODE =>
        IO.raiseError(DuplicateGenerateZoneNameError(generateZone.zoneName))
      case e => IO.raiseError(e)
    }

  // The unique name index means concurrent saves contend on it, so a transaction can come back a
  // deadlock victim or hit a lock wait timeout. Both are transient, so retry with backoff as
  // MySqlZoneRepository does for the zone table's unique name. A duplicate name is not retried:
  // it's a real conflict and will fail the same way every time.
  private def retryOnTransient(
      save: IO[GenerateZone],
      delay: FiniteDuration,
      maxRetries: Int
  ): IO[GenerateZone] =
    save.handleErrorWith {
      case e: SQLException if TRANSIENT_ERROR_CODES.contains(e.getErrorCode) && maxRetries > 0 =>
        logger.warn(s"Transient error saving generated zone, retrying in $delay: ${e.getMessage}")
        IO.sleep(delay) *> retryOnTransient(save, delay * 2, maxRetries - 1)
      case e => IO.raiseError(e)
    }


  private def deleteGeneratedZone(generateZone: GenerateZone)(implicit session: DBSession): GenerateZone = {
    sql"$DELETE_GENERATED_ZONE".bind(generateZone.id).update().apply()
    generateZone
  }

  private def extractGenerateZone(columnIndex: Int): WrappedResultSet => GenerateZone = res => {
    fromPB(VinylDNSProto.GenerateZone.parseFrom(res.bytes(columnIndex)))
  }

  def delete(generateZone: GenerateZone): IO[GenerateZone] =
    monitor("repo.ZoneJDBC.generateZoneDelete") {
      IO {
        DB.localTx { implicit s =>
          deleteGeneratedZone(generateZone)

          generateZone
        }
      }
    }

  private def getGenerateZoneByNameInSession(zoneName: String)(implicit session: DBSession): Option[GenerateZone] =
    sql"$GET_GENERATED_ZONE_BY_NAME".bind(zoneName).map(extractGenerateZone(1)).first().apply()

  private def getGenerateZoneByIdInSession(zoneId: String)(implicit session: DBSession): Option[GenerateZone] =
    sql"$GET_GENERATED_ZONE_BY_ID".bind(zoneId).map(extractGenerateZone(1)).first().apply()

  def getGenerateZoneByName(zoneName: String): IO[Option[GenerateZone]] =
    monitor("repo.ZoneJDBC.getGenerateZoneByName") {
      IO {
        DB.readOnly { implicit s =>
          getGenerateZoneByNameInSession(zoneName)
        }
      }
    }

  def getGenerateZoneById(id: String): IO[Option[GenerateZone]] =
    monitor("repo.ZoneJDBC.getGenerateZoneById") {
      IO {
        DB.readOnly { implicit s =>
          getGenerateZoneByIdInSession(id)
        }
      }
    }

  def listGenerateZones(
                         authPrincipal: AuthPrincipal,
                         zoneNameFilter: Option[String] = None,
                         startFrom: Option[String] = None,
                         maxItems: Int = 100
                       ): IO[ListGeneratedZonesResults] =
    monitor("repo.ZoneJDBC.listGeneratedZones") {
      IO {
        DB.readOnly { implicit s =>
          val nameFilters = if (zoneNameFilter.isDefined && (zoneNameFilter.get.takeRight(1) == "." || zoneNameFilter.get.contains("*"))) {
            List(
              zoneNameFilter.map(flt => sqls"gz.name LIKE ${ensureTrailingDot(flt.replace('*', '%'))}"),
              startFrom.map(os => sqls"gz.name > $os")
            ).flatten
          } else {
            List(
              zoneNameFilter.map(flt => sqls"gz.name LIKE ${flt.concat("%")}"),
              startFrom.map(os => sqls"gz.name > $os")
            ).flatten
          }

          val filters = nameFilters ++ accessFilter(authPrincipal).toList

          val baseQuery = BASE_GENERATE_ZONE_SEARCH_SQL

          val withWhere = if (filters.nonEmpty) {
            baseQuery.append(sqls" WHERE ").append(SQLSyntax.join(filters, sqls" AND "))
          } else baseQuery

          // ORDER BY name so cursor pagination (gz.name > startFrom + nextId) is stable across
          // pages instead of depending on MySQL's incidental row order.
          val fullQuery = withWhere.append(sqls" GROUP BY gz.name ORDER BY gz.name ASC LIMIT ${maxItems + 1}")

          val results: List[GenerateZone] = sql"$fullQuery"
            .map(extractGenerateZone(1))
            .list()
            .apply()

          val (newResults, nextId) =
            if (results.size > maxItems)
              (results.dropRight(1), results.dropRight(1).lastOption.map(_.zoneName))
            else (results, None)

          ListGeneratedZonesResults(
            generatedZones = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            zonesFilter = zoneNameFilter
          )
        }
      }
    }

  def listGeneratedZonesByAdminGroupIds(
                                         authPrincipal: AuthPrincipal,
                                         startFrom: Option[String] = None,
                                         maxItems: Int = 100,
                                         adminGroupIds: Set[String]
                                       ): IO[ListGeneratedZonesResults] =
    monitor("repo.ZoneJDBC.listZonesByAdminGroupIds") {
      IO {
        DB.readOnly { implicit s =>
          val groupIdList = adminGroupIds.toSeq
          val groupIdCondition = if (adminGroupIds.nonEmpty) {
            sqls"gz.admin_group_id IN ($groupIdList)"
          } else {
            sqls"gz.admin_group_id IN ('')"
          }

          val startFromCondition = startFrom.map(os => sqls"gz.name > $os")

          val conditions =
            List(Some(groupIdCondition), startFromCondition).flatten ++
              accessFilter(authPrincipal).toList

          val baseQuery = BASE_GENERATE_ZONE_SEARCH_SQL.append(sqls" WHERE ")
          val withConditions = baseQuery.append(SQLSyntax.join(conditions, sqls" AND "))
          // ORDER BY name so cursor pagination (gz.name > startFrom + nextId) is stable across
          // pages instead of depending on MySQL's incidental row order.
          val fullQuery = withConditions.append(sqls" GROUP BY gz.name ORDER BY gz.name ASC LIMIT ${maxItems + 1}")

          val results: List[GenerateZone] = sql"$fullQuery"
            .map(extractGenerateZone(1))
            .list()
            .apply()

          val (newResults, nextId) =
            if (results.size > maxItems)
              (results.dropRight(1), results.dropRight(1).lastOption.map(_.zoneName))
            else (results, None)


          ListGeneratedZonesResults(
            generatedZones = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            zonesFilter = None
          )
        }
      }
    }



}

