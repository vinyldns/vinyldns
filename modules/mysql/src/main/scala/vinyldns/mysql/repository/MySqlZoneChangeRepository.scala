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

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.zone._
import vinyldns.core.protobuf._
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

class MySqlZoneChangeRepository
    extends ZoneChangeRepository
    with ProtobufConversions
    with Monitored {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneChangeRepository])

  private final val MAX_ACCESSORS = 30

  private final val PUT_ZONE_CHANGE =
    sql"""
      |REPLACE INTO zone_change (change_id, zone_id, data, created_timestamp, zone_name, zone_status)
      |  VALUES ({change_id}, {zone_id}, {data}, {created_timestamp},{zone_name}, {zone_status})
      """.stripMargin

  private final val BASE_ZONE_CHANGE_SEARCH_SQL =
    """
      |SELECT zc.data
      |  FROM zone_change zc
       """.stripMargin

  private final val LIST_ZONES_CHANGES =
    sql"""
      |SELECT zc.data
      |  FROM zone_change zc
      |  WHERE zc.zone_id = {zoneId} AND zc.created_timestamp <= {startFrom}
      |  ORDER BY zc.created_timestamp DESC
      |  LIMIT {maxItems}
    """.stripMargin

  private final val LIST_ZONES_CHANGES_DATA =
    sql"""
         |SELECT zc.data
         |  FROM zone_change zc
         |  JOIN zone z ON z.id = zc.zone_id
         |  ORDER BY zc.created_timestamp DESC
    """.stripMargin

  override def save(zoneChange: ZoneChange): IO[ZoneChange] =
    monitor("repo.ZoneChange.save") {
      IO {
        logger.debug(s"Saving zone change '${zoneChange.id}' for zone '${zoneChange.zone.name}'")
        DB.localTx { implicit s =>
          PUT_ZONE_CHANGE
            .bindByName(
              'change_id -> zoneChange.id,
              'zone_id -> zoneChange.zoneId,
              'data -> toPB(zoneChange).toByteArray,
              'created_timestamp -> zoneChange.created.toEpochMilli,
              'zone_name -> zoneChange.zone.name,
              'zone_status -> zoneChange.zone.status.toString
            )
            .update()
            .apply()

          zoneChange
        }
      }
    }

  override def listZoneChanges(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Int
  ): IO[ListZoneChangesResults] =
    // sorted from most recent, startFrom is an offset from the most recent change
    monitor("repo.ZoneChange.listZoneChanges") {
      IO {
        logger.debug(s"Getting zone changes for zone $zoneId")
        DB.readOnly { implicit s =>
          val startValue = startFrom.getOrElse(Instant.now.truncatedTo(ChronoUnit.MILLIS).toEpochMilli.toString)
          // maxItems gets a plus one to know if the table is exhausted so we can conditionally give a nextId
          val queryResult = LIST_ZONES_CHANGES
            .bindByName(
              'zoneId -> zoneId,
              'startFrom -> startValue,
              'maxItems -> (maxItems + 1)
            )
            .map(extractZoneChange(1))
            .list()
            .apply()
          val maxQueries = queryResult.take(maxItems)

          // nextId is Option[String] to maintains backwards compatibility
          // earlier maxItems was incremented, if the (maxItems + 1) size is not reached then pages are exhausted
          val nextId = queryResult match {
            case _ if queryResult.size <= maxItems | queryResult.isEmpty => None
            case _ => Some(queryResult.last.created.toEpochMilli.toString)
          }

          ListZoneChangesResults(maxQueries, nextId, startFrom, maxItems)
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
      (BASE_ZONE_CHANGE_SEARCH_SQL, Seq.empty)
    } else {
      // User is not super or support,
      // let's join across to the zone access table so we return only zones a user has access to
      val accessors = buildZoneSearchAccessorList(user, groupIds)
      val questionMarks = List.fill(accessors.size)("?").mkString(",")
      val withAccessorCheck = BASE_ZONE_CHANGE_SEARCH_SQL +
        s"""
           |    JOIN zone_access za ON zc.zone_id = za.zone_id
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

  def listDeletedZones(
                        authPrincipal: AuthPrincipal,
                        zoneNameFilter: Option[String] = None,
                        startFrom: Option[String] = None,
                        maxItems: Int = 100,
                        ignoreAccess: Boolean = false,
                        accessFilter: Option[Int] = None
                      ): IO[ListDeletedZonesChangeResults] =
    monitor("repo.ZoneChange.listDeletedZoneInZoneChanges") {
      IO {
        DB.readOnly { implicit s =>
          val (withAccessorCheck, accessors) =
            withAccessors(authPrincipal.signedInUser, authPrincipal.memberGroupIds, ignoreAccess)
          val sb = new StringBuilder
          sb.append(withAccessorCheck)
          sb.append("\n  LEFT JOIN zone z ON zc.zone_name = z.name")
          sb.append(" WHERE z.name IS NULL")
          sb.append(" AND zc.zone_status = 'Deleted'")

          val extraBindParams = scala.collection.mutable.ListBuffer[Any]()
          val filters = if (zoneNameFilter.isDefined && zoneNameFilter.get.contains("*"))
              zoneNameFilter.map(flt => s"zc.zone_name LIKE '${flt.replace('*', '%')}'")
          else zoneNameFilter.map(flt => s"zc.zone_name LIKE '${flt.concat("%")}'")

          if(zoneNameFilter.isDefined)
            sb.append(s" AND ")

          sb.append(filters.mkString)

          accessFilter.foreach { af =>
            sb.append(s" AND INSTR(zc.data, X'4801') ${if (af == 0) "> 0" else "= 0"}")
          }

          startFrom.foreach { cursorName =>
            sb.append(" AND zc.zone_name >= ?")
            extraBindParams += cursorName
          }

          sb.append(
            s"""
               |    GROUP BY zc.zone_name
               |    ORDER BY zc.zone_name ASC
               |    LIMIT ${maxItems + 1}
             """.stripMargin)

          val query = sb.toString
          val allBindParams = accessors ++ extraBindParams.toSeq


          val deletedZoneResults: List[ZoneChange] =
            SQL(query)
              .bind(allBindParams: _*)
              .map(extractZoneChange(1))
                .list()
                .apply()

          val (newResults, nextId) =
            if (deletedZoneResults.size > maxItems)
              (deletedZoneResults.dropRight(1), deletedZoneResults.lastOption.map(_.zone.name))
            else (deletedZoneResults, None)

          ListDeletedZonesChangeResults(
            zoneDeleted = newResults,
            nextId = nextId,
            startFrom = startFrom,
            maxItems = maxItems,
            zoneChangeFilter = zoneNameFilter,
            ignoreAccess = ignoreAccess
          )
        }}}

  def listFailedZoneChanges(maxItems: Int, startFrom: Int): IO[ListFailedZoneChangesResults] =
    monitor("repo.ZoneChange.listFailedZoneChanges") {
      IO {
        DB.readOnly { implicit s =>
          val queryResult = LIST_ZONES_CHANGES_DATA
            .map(extractZoneChange(1))
            .list()
            .apply()

          val failedZoneChanges = queryResult.filter(zc => zc.status == ZoneChangeStatus.Failed).drop(startFrom).take(maxItems)
          val nextId = if (failedZoneChanges.size < maxItems) 0 else startFrom + maxItems

          ListFailedZoneChangesResults(failedZoneChanges,nextId,startFrom,maxItems)
        }
      }
    }

  def countAllAbandonedStats(): IO[(Int, Int, Int)] =
    monitor("repo.ZoneChange.countAllAbandonedStats") {
      IO {
        DB.readOnly { implicit s =>
          SQL("""
            |SELECT
            |  COUNT(DISTINCT zc.zone_name),
            |  COUNT(DISTINCT CASE WHEN zc.zone_name LIKE '%in-addr.arpa.' OR zc.zone_name LIKE '%ip6.arpa.' THEN zc.zone_name END),
            |  COUNT(DISTINCT CASE WHEN INSTR(zc.data, X'4801') > 0 THEN zc.zone_name END)
            |FROM zone_change zc
            |LEFT JOIN zone z ON zc.zone_name = z.name
            |WHERE zc.zone_status = 'Deleted'
            |  AND z.name IS NULL
          """.stripMargin)
            .map(rs => (rs.int(1), rs.int(2), rs.int(3)))
            .single()
            .apply()
            .getOrElse((0, 0, 0))
        }
      }
    }

  def countAllAbandonedStatsForUser(authPrincipal: AuthPrincipal): IO[(Int, Int, Int)] =
    monitor("repo.ZoneChange.countAllAbandonedStatsForUser") {
      IO {
        DB.readOnly { implicit s =>
          val user = authPrincipal.signedInUser
          val accessors = buildZoneSearchAccessorList(user, authPrincipal.memberGroupIds)
          val questionMarks = List.fill(accessors.size)("?").mkString(",")
          SQL(
            s"""
              |SELECT
              |  COUNT(DISTINCT zc.zone_name),
              |  COUNT(DISTINCT CASE WHEN zc.zone_name LIKE '%in-addr.arpa.' OR zc.zone_name LIKE '%ip6.arpa.' THEN zc.zone_name END),
              |  COUNT(DISTINCT CASE WHEN INSTR(zc.data, X'4801') > 0 THEN zc.zone_name END)
              |FROM zone_change zc
              |JOIN zone_access za ON zc.zone_id = za.zone_id AND za.accessor_id IN ($questionMarks)
              |LEFT JOIN zone z ON zc.zone_name = z.name
              |WHERE zc.zone_status = 'Deleted'
              |  AND z.name IS NULL
            """.stripMargin)
            .bind(accessors: _*)
            .map(rs => (rs.int(1), rs.int(2), rs.int(3)))
            .single()
            .apply()
            .getOrElse((0, 0, 0))
        }
      }
    }

  private def extractZoneChange(colIndex: Int): WrappedResultSet => ZoneChange = res => {
    fromPB(VinylDNSProto.ZoneChange.parseFrom(res.bytes(colIndex)))
  }
}
