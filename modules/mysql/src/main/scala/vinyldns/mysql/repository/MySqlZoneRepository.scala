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

import cats.effect.{IO, _}
import cats.implicits._
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone.{ListZonesResults, Zone, ZoneRepository, ZoneStatus}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto
import vinyldns.core.domain.DomainHelpers._

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
       |SELECT data
       |  FROM zone z
       | WHERE z.admin_group_id = (?)
        """.stripMargin

  private final val BASE_ZONE_SEARCH_SQL =
    """
      |SELECT z.data
      |  FROM zone z
       """.stripMargin

  private final val BASE_GET_ZONES_SQL =
    """
      |SELECT data
      |  FROM zone
       """.stripMargin

  private final val GET_ZONE_ACCESS_BY_ADMIN_GROUP_ID =
    sql"""
         |SELECT zone_id
         |  FROM zone_access z
         | WHERE z.accessor_id = (?)
         | LIMIT 1
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
          SQL(
            BASE_GET_ZONES_SQL +
              s"""WHERE name in ($questionMarks)""".stripMargin
          ).bind(zoneNames: _*)
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

  def getZones(zoneIds: Set[String]): IO[Set[Zone]] =
    if (zoneIds.isEmpty) {
      IO.pure(Set())
    } else {
      monitor("repo.ZoneJDBC.getZones") {
        val zoneIdsList = zoneIds.toList
        IO {
          DB.readOnly { implicit s =>
            val questionMarks = List.fill(zoneIds.size)("?").mkString(",")
            SQL(
              BASE_GET_ZONES_SQL + s"""WHERE id in ($questionMarks)""".stripMargin
            ).bind(zoneIdsList: _*)
              .map(extractZone(1))
              .list()
              .apply()
          }.toSet
        }
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

            SQL(
              BASE_GET_ZONES_SQL +
                " WHERE " + whereClause
            ).bind(zn: _*).map(extractZone(1)).list().apply()
          }.toSet
        }
      }
    }

  /**
    * This is somewhat complicated due to how we need to build the SQL.
    *
    * - Dynamically build the accessor list combining the user id and group ids
    * - Dynamically build the LIMIT clause.  We cannot specify an offset if this is the first page (offset == 0)
    *
    * @return a ListZonesResults
    */
  def listZonesByAdminGroupIds(
       authPrincipal: AuthPrincipal,
       startFrom: Option[String] = None,
       maxItems: Int = 100,
       adminGroupIds: Set[String],
       ignoreAccess: Boolean = false
  ): IO[ListZonesResults] =
    monitor("repo.ZoneJDBC.listZonesByAdminGroupIds") {
      IO {
        DB.readOnly { implicit s =>
          val (withAccessorCheck, accessors) =
            withAccessors(authPrincipal.signedInUser, authPrincipal.memberGroupIds, ignoreAccess)
          val sb = new StringBuilder
          sb.append(withAccessorCheck)

          if(adminGroupIds.nonEmpty) {
            val groupIds = adminGroupIds.map(x => "'" + x + "'").mkString(",")
            sb.append(s" WHERE admin_group_id IN ($groupIds) ")
          } else {
            sb.append(s" WHERE admin_group_id IN ('') ")
          }

          sb.append(s" GROUP BY z.name ")
          sb.append(s" LIMIT ${maxItems + 1}")

          val query = sb.toString

          val results: List[Zone] = SQL(query)
            .bind(accessors: _*)
            .map(extractZone(1))
            .list()
            .apply()

          val (newResults, nextId) =
            if (results.size > maxItems)
              (results.dropRight(1), results.dropRight(1).lastOption.map(_.name))
            else (results, None)


          ListZonesResults(
            zones = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            zonesFilter = None,
            ignoreAccess = ignoreAccess,
          )
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
    * @return a ListZonesResults
    */
  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      ignoreAccess: Boolean = false
  ): IO[ListZonesResults] =
    monitor("repo.ZoneJDBC.listZones") {
      IO {
        DB.readOnly { implicit s =>
          val (withAccessorCheck, accessors) =
            withAccessors(authPrincipal.signedInUser, authPrincipal.memberGroupIds, ignoreAccess)
          val sb = new StringBuilder
          sb.append(withAccessorCheck)

          val filters = if (zoneNameFilter.isDefined && (zoneNameFilter.get.takeRight(1) == "." || zoneNameFilter.get.contains("*"))) {
            List(
              zoneNameFilter.map(flt => s"z.name LIKE '${ensureTrailingDot(flt.replace('*', '%'))}'"),
              startFrom.map(os => s"z.name > '$os'")
            ).flatten
          } else {
            List(
              zoneNameFilter.map(flt => s"z.name LIKE '${flt.concat("%")}'"),
              startFrom.map(os => s"z.name > '$os'")
            ).flatten
          }

          if (filters.nonEmpty) {
            sb.append(" WHERE ")
            sb.append(filters.mkString(" AND "))
          }

          sb.append(s" GROUP BY z.name ")
          sb.append(s" LIMIT ${maxItems + 1}")

          val query = sb.toString

          val results: List[Zone] = SQL(query)
            .bind(accessors: _*)
            .map(extractZone(1))
            .list()
            .apply()

          val (newResults, nextId) =
            if (results.size > maxItems)
              (results.dropRight(1), results.dropRight(1).lastOption.map(_.name))
            else (results, None)

          ListZonesResults(
            zones = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            zonesFilter = zoneNameFilter,
            ignoreAccess = ignoreAccess
          )
        }
      }
    }

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]] =
    monitor("repo.ZoneJDBC.getZonesByAdminGroupId") {
      IO {
        DB.readOnly { implicit s =>
          GET_ZONES_BY_ADMIN_GROUP_ID.bind(adminGroupId).map(extractZone(1)).list().apply()
        }
      }
    }

  def getFirstOwnedZoneAclGroupId(groupId: String): IO[Option[String]] =
    monitor("repo.ZoneJDBC.getZoneAclGroupId") {
      IO {
        DB.readOnly { implicit s =>
          GET_ZONE_ACCESS_BY_ADMIN_GROUP_ID
            .bind(groupId)
            .map(_.string(1))
            .single
            .apply()
        }
      }
    }

  private def withAccessors(
      user: User,
      groupIds: Seq[String],
      ignoreAccessZones: Boolean
  ): (String, Seq[Any]) =
    // Super users do not need to join across to check zone access as they have access to all of the zones
    if (ignoreAccessZones || user.isSuper || user.isSupport) {
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

  /* Limit the accessors so that we don't have boundless parameterized queries */
  private def buildZoneSearchAccessorList(user: User, groupIds: Seq[String]): Seq[String] = {
    val allAccessors = user.id +: groupIds

    if (allAccessors.length > MAX_ACCESSORS) {
      logger.warn(
        s"User ${user.userName} with id ${user.id} is in more than $MAX_ACCESSORS groups, no all zones maybe returned!"
      )
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
      maxRetries: Int
  ): IO[Either[E, A]] =
    f(a).handleErrorWith { error =>
      if (maxRetries > 0)
        IO.sleep(delay) *> retryWithBackoff(f, a, delay * 2, maxRetries - 1)
      else
        IO.raiseError(error)
    }
}
