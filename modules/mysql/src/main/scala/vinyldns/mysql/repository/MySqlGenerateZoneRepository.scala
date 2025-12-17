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
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.{GenerateZone, GenerateZoneRepository, ListGeneratedZonesResults}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto


class MySqlGenerateZoneRepository extends GenerateZoneRepository with ProtobufConversions with Monitored {


  final val MAX_RETRIES = 10


  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the generate zone, which will update the values if the zone already exists
    * similar to a PUT in a KV store
    */
  private final val PUT_GENERATE_ZONE =
    sql"""
         |INSERT INTO generate_zone(id, name, provider, admin_group_id, response, data)
         |     VALUES ({id}, {name}, {provider}, {adminGroupId}, {response}, {data}) ON DUPLICATE KEY
         |     UPDATE name=VALUES(name),
         |            provider=VALUES(provider),
         |            admin_group_id=VALUES(admin_group_id),
         |            response=VALUES(response),
         |            data=VALUES(data);
        """.stripMargin

  private final val DELETE_GENERATED_ZONE =
    sql"""
         |DELETE
         |  FROM generate_zone
         | WHERE id = (?)
         |
      """.stripMargin

  private final val GET_GENERATED_ZONE_BY_NAME =
    sql"""
         |SELECT data
         |  FROM generate_zone
         | WHERE name = ?
        """.stripMargin

  private final val GET_GENERATED_ZONE_BY_ID =
    sql"""
         |SELECT data
         |  FROM generate_zone
         | WHERE id = ?
        """.stripMargin

  private final val BASE_GENERATE_ZONE_SEARCH_SQL =
    """
      |SELECT gz.data
      |  FROM generate_zone gz
       """.stripMargin

   def save(generateZone: GenerateZone): IO[GenerateZone] = {
      monitor("repo.generateZone.save") {
        IO {
            DB.localTx { implicit s =>
              PUT_GENERATE_ZONE
              .bindByName(
                  'id -> generateZone.id,
                  'name -> generateZone.zoneName,
                  'provider -> generateZone.provider,
                  'adminGroupId -> generateZone.groupId,
                  'response -> toPB(generateZone.response.get).toByteArray,
                  'data -> toPB(generateZone).toByteArray
              )
              .update()
              .apply()
            }
            generateZone
          }
      }}


  private def deleteGeneratedZone(generateZone: GenerateZone)(implicit session: DBSession): GenerateZone = {
    DELETE_GENERATED_ZONE.bind(generateZone.id).update().apply()
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
    GET_GENERATED_ZONE_BY_NAME.bind(zoneName).map(extractGenerateZone(1)).first().apply()

  private def getGenerateZoneByIdInSession(zoneId: String)(implicit session: DBSession): Option[GenerateZone] =
    GET_GENERATED_ZONE_BY_ID.bind(zoneId).map(extractGenerateZone(1)).first().apply()

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
                         maxItems: Int = 100,
                         ignoreAccess: Boolean = false
                       ): IO[ListGeneratedZonesResults] =
    monitor("repo.ZoneJDBC.listGeneratedZones") {
      IO {
        DB.readOnly { implicit s =>
          val sb = new StringBuilder
          sb.append(BASE_GENERATE_ZONE_SEARCH_SQL)

          val filters = if (zoneNameFilter.isDefined && (zoneNameFilter.get.takeRight(1) == "." || zoneNameFilter.get.contains("*"))) {
            List(
              zoneNameFilter.map(flt => s"gz.name LIKE '${ensureTrailingDot(flt.replace('*', '%'))}'"),
              startFrom.map(os => s"gz.name > '$os'")
            ).flatten
          } else {
            List(
              zoneNameFilter.map(flt => s"gz.name LIKE '${flt.concat("%")}'"),
              startFrom.map(os => s"gz.name > '$os'")
            ).flatten
          }

          if (filters.nonEmpty) {
            sb.append(" WHERE ")
            sb.append(filters.mkString(" AND "))
          }

          sb.append(s" GROUP BY gz.name ")
          sb.append(s" LIMIT ${maxItems + 1}")

          val query = sb.toString

          val results: List[GenerateZone] = SQL(query)
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
            zonesFilter = zoneNameFilter,
            ignoreAccess = ignoreAccess
          )
        }
      }
    }

  def listGeneratedZonesByAdminGroupIds(
                                         authPrincipal: AuthPrincipal,
                                         startFrom: Option[String] = None,
                                         maxItems: Int = 100,
                                         adminGroupIds: Set[String],
                                         ignoreAccess: Boolean = false
                                       ): IO[ListGeneratedZonesResults] =
    monitor("repo.ZoneJDBC.listZonesByAdminGroupIds") {
      IO {
        DB.readOnly { implicit s =>

          val sb = new StringBuilder
          sb.append(BASE_GENERATE_ZONE_SEARCH_SQL)

          if(adminGroupIds.nonEmpty) {
            val groupIds = adminGroupIds.map(x => "'" + x + "'").mkString(",")
            sb.append(s" WHERE admin_group_id IN ($groupIds) ")
          } else {
            sb.append(s" WHERE admin_group_id IN ('') ")
          }

          if(startFrom.isDefined){
            sb.append(" AND ")
            sb.append(s"gz.name > '${startFrom.get}'")
          }

          sb.append(s" GROUP BY gz.name ")
          sb.append(s" LIMIT ${maxItems + 1}")

          val query = sb.toString

          val results: List[GenerateZone] = SQL(query)
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
            zonesFilter = None,
            ignoreAccess = ignoreAccess
          )
        }
      }
    }



}

