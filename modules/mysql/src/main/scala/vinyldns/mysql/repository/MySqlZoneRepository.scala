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

import cats.effect._
import cats.implicits._
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone.{Zone, ZoneRepository, ZoneStatus}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MySqlZoneRepository extends ZoneRepository with ProtobufConversions with Monitored {

  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneRepository])
  private final val MAX_ACCESSORS = 30
  private final val INITIAL_RETRY_DELAY = 1.millis
  final val MAX_RETRIES = 10
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the zone, which will update the values if the zone already exists
    * similar to a PUT in a KV store
    */
  private final val PUT_ZONE =
    sql"""
       |INSERT INTO zone(id, name, admin_group_id, data)
       |     VALUES ({id}, {name}, {adminGroupId}, {data}) ON DUPLICATE KEY
       |     UPDATE name=VALUES(name),
       |            admin_group_id=VALUES(admin_group_id),
       |            data=VALUES(data);
        """.stripMargin

  private final val DELETE_ZONE =
    sql"""
       |DELETE
       |  FROM zone
       | WHERE id = (?)
       |
      """.stripMargin

  /**
    * Note: It seems as though H2 does not support INSERT IGNORE INTO syntax for MYSQL
    * Given that zone updates are very infrequent, and the number of accessors for a zone is relatively small,
    * I think it is ok to use the REPLACE INTO syntax
    */
  private final val PUT_ZONE_ACCESS =
    sql"""
       |REPLACE INTO zone_access(accessor_id, zone_id)
       |      VALUES ({accessorId}, {zoneId})
        """.stripMargin

  private final val DELETE_ZONE_ACCESS =
    sql"""
       |DELETE
       |  FROM zone_access
       | WHERE zone_id = ?
      """.stripMargin

  // Get the data, which is the protocol buffer
  private final val GET_ZONE =
    sql"""
       |SELECT data
       |  FROM zone
       | WHERE id = ?
        """.stripMargin

  // Get the data, which is the protocol buffer
  private final val GET_ZONE_BY_NAME =
    sql"""
       |SELECT data
       |  FROM zone
       | WHERE name = ?
        """.stripMargin

  private final val GET_ZONES_BY_ADMIN_GROUP_ID =
    sql"""
       |SELECT DISTINCT id, data
       |  FROM zone z
       | WHERE z.admin_group_id = (?)
        """.stripMargin

  private final val BASE_ZONE_SEARCH_SQL =
    """
      |SELECT DISTINCT z.id, z.data, z.name
      |  FROM zone z
       """.stripMargin

  private final val BASE_GET_ZONES_SQL =
    """
      |SELECT data
      |  FROM zone
       """.stripMargin

  /**
    * When we save a zone, if it is deleted we actually delete it from the repo.  This will force a cascade
    * delete on all linked records in the zone_access table.
    *
    * If the zone is not deleted, we have to save both the zone itself, as well as the zone access entries.
    */
  def save(zone: Zone): IO[Either[DuplicateZoneError, Zone]] =
    zone.status match {
      case ZoneStatus.Deleted =>
        val doDelete: Zone => IO[Either[DuplicateZoneError, Zone]] = z => deleteTx(z).map(Right(_))
        retryWithBackoff(doDelete, zone, INITIAL_RETRY_DELAY, MAX_RETRIES)
      case _ => retryWithBackoff(saveTx, zone, INITIAL_RETRY_DELAY, MAX_RETRIES)
    }

  def getZone(zoneId: String): IO[Option[Zone]] =
    monitor("repo.ZoneJDBC.getZone") {
      IO {
        DB.readOnly { implicit s =>
          GET_ZONE.bind(zoneId).map(extractZone(1)).first().apply()
        }
      }
    }

  def getZoneByName(zoneName: String): IO[Option[Zone]] =
    monitor("repo.ZoneJDBC.getZoneByName") {
      IO {
        DB.readOnly { implicit s =>
          getZoneByNameInSession(zoneName)
        }
      }
    }

  private def getZoneByNameInSession(zoneName: String)(implicit session: DBSession): Option[Zone] =
    GET_ZONE_BY_NAME.bind(zoneName).map(extractZone(1)).first().apply()

  private def getZonesByNamesGroup(zoneNames: List[String]): IO[List[Zone]] =
    monitor("repo.ZoneJDBC.getZonesByNames") {
      IO {
        DB.readOnly { implicit s =>
          val questionMarks = List.fill(zoneNames.size)("?").mkString(",")
          SQL(BASE_GET_ZONES_SQL +
            s"""WHERE name in ($questionMarks)""".stripMargin)
            .bind(zoneNames: _*)
            .map(extractZone(1))
            .list()
            .apply()
        }
      }
    }

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] = {
    val zoneNamesList = zoneNames.toList
    if (zoneNames.isEmpty) {
      IO.pure(Set())
    } else {
      // TODO Getting 500 at a time max here - should test this at scale
      val zoneGroups = zoneNamesList.grouped(500).toList
      val zoneGroupsSeq = zoneGroups.map(getZonesByNamesGroup).parSequence
      zoneGroupsSeq.map(_.flatten.toSet)
    }
  }

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]] =
    if (zoneNames.isEmpty) {
      IO.pure(Set())
    } else {
      monitor("repo.ZoneJDBC.getZonesByFilters") {
        val zoneNameList = zoneNames.toList

        IO {
          DB.readOnly { implicit s =>
            val clause = for (_ <- zoneNameList) yield s"""name like (?)""".stripMargin
            val whereClause = clause.mkString(" OR ")
            val zn = zoneNameList.map(name => s"%$name%")

            SQL(BASE_GET_ZONES_SQL +
              " WHERE " + whereClause).bind(zn: _*).map(extractZone(1)).list().apply()
          }.toSet
        }
      }
    }

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      offset: Option[Int] = None,
      pageSize: Int = 100): IO[List[Zone]] =
    monitor("repo.ZoneJDBC.listZones") {
      IO {
        DB.readOnly { implicit s =>
          buildZoneSearch(
            authPrincipal.signedInUser,
            authPrincipal.memberGroupIds,
            zoneNameFilter,
            offset,
            pageSize)
            .map(extractZone(2))
            .list()
            .apply()
        }
      }
    }

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]] =
    monitor("repo.ZoneJDBC.getZonesByAdminGroupId") {
      IO {
        DB.readOnly { implicit s =>
          GET_ZONES_BY_ADMIN_GROUP_ID.bind(adminGroupId).map(extractZone(2)).list().apply()
        }
      }
    }

  /**
    * This is somewhat complicated due to how we need to build the SQL.
    *
    * - Dynamically build the accessor list combining the user id and group ids
    * - Do not include a zone name filter if there is no filter applied
    * - Dynamically build the LIMIT clause.  We cannot specify an offset if this is the first page (offset == 0)
    *
    * @return A fully bound scalike SQL that can be run
    */
  private def buildZoneSearch(
      user: User,
      groupIds: Seq[String],
      zoneNameFilter: Option[String],
      offset: Option[Int],
      pageSize: Int) = {

    val (withAccessorCheck, accessors) = withAccessors(user, groupIds)
    val (withZoneNameSQL, zoneNameParams) = withZoneNameFilter(withAccessorCheck, zoneNameFilter)
    val sortedSQL = withZoneNameSQL + " ORDER BY z.name ASC"
    val (finalSQL, pagingParams) = withPaging(sortedSQL, offset, pageSize)

    SQL(finalSQL).bind(accessors ++ zoneNameParams ++ pagingParams: _*)
  }

  private def withAccessors(user: User, groupIds: Seq[String]): (String, Seq[Any]) =
    // Super users do not need to join across to check zone access as they have access to all of the zones
    if (user.isSuper || user.isSupport) {
      (BASE_ZONE_SEARCH_SQL, Seq.empty)
    } else {
      // User is not super or support,
      // let's join across to the zone access table so we return only zones a user has access to
      val accessors = buildZoneSearchAccessorList(user, groupIds)
      val questionMarks = List.fill(accessors.size)("?").mkString(",")
      val withAccessorCheck = BASE_ZONE_SEARCH_SQL +
        s"""
           |    JOIN zone_access za ON z.id = za.zone_id
           |      AND za.accessor_id IN ($questionMarks)
    """.stripMargin
      (withAccessorCheck, accessors)
    }

  /* Append a WHERE clause for the zone name if it is provided */
  private def withZoneNameFilter(sql: String, zoneNameFilter: Option[String]): (String, Seq[Any]) =
    zoneNameFilter
      .map(filter => s"%$filter%")
      .map(znf => (sql + " WHERE z.name LIKE (?)", Seq(znf)))
      .getOrElse(sql, Seq.empty)

  /* For the first page (offset not specified), we do not specify an offset / skip */
  private def withPaging(sql: String, offset: Option[Int], pageSize: Int): (String, Seq[Any]) =
    offset
      .map(os => (sql + " LIMIT ?, ?", Seq(os, pageSize)))
      .getOrElse(sql + " LIMIT ?", Seq(pageSize))

  /* Limit the accessors so that we don't have boundless parameterized queries */
  private def buildZoneSearchAccessorList(user: User, groupIds: Seq[String]): Seq[String] = {
    val allAccessors = user.id +: groupIds

    if (allAccessors.length > MAX_ACCESSORS) {
      logger.warn(
        s"User ${user.userName} with id ${user.id} is in more than $MAX_ACCESSORS groups, no all zones maybe returned!")
    }

    // Take the top 30 accessors, but add "EVERYONE" to the list so that we include zones that have everyone access
    allAccessors.take(MAX_ACCESSORS) :+ "EVERYONE"
  }

  private def putZone(zone: Zone)(implicit session: DBSession): Zone = {
    PUT_ZONE
      .bindByName(
        Seq(
          'id -> zone.id,
          'name -> zone.name,
          'adminGroupId -> zone.adminGroupId,
          'data -> toPB(zone).toByteArray
        ): _*
      )
      .update()
      .apply()

    zone
  }

  /**
    * The zone_access table holds a pair of accessor_id -> zone_id
    *
    * The accessor_id is either a user id or a group id.
    *
    * Here, we want to store the following:
    * - All user ids for any ACL rules on the zone
    * - All group ids for any ACL rules on the zone
    * - The adminGroupId on the zone - this is important!  We have to do this as a separate step when we do our insert
    */
  private def putZoneAccess(zone: Zone)(implicit session: DBSession): Zone = {

    // generates the batch parameters, we create an entry per user and group mentioned in the acl rules
    val sqlParameters: Seq[Seq[(Symbol, Any)]] =
      zone.acl.rules.toSeq
        .map(r => r.userId.orElse(r.groupId).getOrElse("EVERYONE")) // if the user and group are empty, assert everyone
        .map(userOrGroupId => Seq('accessorId -> userOrGroupId, 'zoneId -> zone.id))

    // we MUST make sure that we put the admin group id as an accessor to this zone
    val allAccessors = sqlParameters :+ Seq('accessorId -> zone.adminGroupId, 'zoneId -> zone.id)

    // make sure that we do a distinct, so that we don't generate unnecessary inserts
    PUT_ZONE_ACCESS.batchByName(allAccessors.distinct: _*).apply()
    zone
  }

  private def deleteZone(zone: Zone)(implicit session: DBSession): Zone = {
    DELETE_ZONE.bind(zone.id).update().apply()
    zone
  }

  private def deleteZoneAccess(zone: Zone)(implicit session: DBSession): Zone = {
    DELETE_ZONE_ACCESS.bind(zone.id).update().apply()
    zone
  }

  private def extractZone(columnIndex: Int): WrappedResultSet => Zone = res => {
    fromPB(VinylDNSProto.Zone.parseFrom(res.bytes(columnIndex)))
  }

  def deleteTx(zone: Zone): IO[Zone] =
    monitor("repo.ZoneJDBC.delete") {
      IO {
        DB.localTx { implicit s =>
          deleteZone(zone)
        }
      }
    }

  def saveTx(zone: Zone): IO[Either[DuplicateZoneError, Zone]] =
    monitor("repo.ZoneJDBC.save") {
      IO {
        DB.localTx { implicit s =>
          getZoneByNameInSession(zone.name) match {
            case Some(foundZone) if zone.id != foundZone.id => DuplicateZoneError(zone.name).asLeft
            case _ =>
              deleteZoneAccess(zone)
              putZone(zone)
              putZoneAccess(zone)
              zone.asRight
          }
        }
      }
    }

  def retryWithBackoff[E, A](
      f: A => IO[Either[E, A]],
      a: A,
      delay: FiniteDuration,
      maxRetries: Int): IO[Either[E, A]] =
    f(a).handleErrorWith { error =>
      if (maxRetries > 0)
        IO.sleep(delay) *> retryWithBackoff(f, a, delay * 2, maxRetries - 1)
      else
        IO.raiseError(error)
    }
}
