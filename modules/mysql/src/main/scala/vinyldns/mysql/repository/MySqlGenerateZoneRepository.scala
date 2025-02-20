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
import vinyldns.core.domain.zone.{GenerateZone, GenerateZoneRepository}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored


class MySqlGenerateZoneRepository extends GenerateZoneRepository with ProtobufConversions with Monitored {


  final val MAX_RETRIES = 10


  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the generate zone, which will update the values if the zone already exists
    * similar to a PUT in a KV store
    */
  private final val PUT_GENERATE_ZONE =
    sql"""
         |INSERT INTO generate_zone(id, name, admin_group_id, response, data)
         |     VALUES ({id}, {name}, {adminGroupId}, {response}, {data}) ON DUPLICATE KEY
         |     UPDATE name=VALUES(name),
         |            admin_group_id=VALUES(admin_group_id),
         |            response=VALUES(response),
         |            data=VALUES(data);
        """.stripMargin

//  private final val DELETE_GENERATED_ZONE =
//    sql"""
//         |DELETE
//         |  FROM generate_zone
//         | WHERE id = (?)
//         |
//      """.stripMargin
//
//  private final val GET_GENERATED_ZONE_BY_NAME =
//    sql"""
//         |SELECT data
//         |  FROM generate_zone
//         | WHERE name = ?
//        """.stripMargin

   def save(generateZone: GenerateZone): IO[GenerateZone] = {
      monitor("repo.generateZone.save") {
        IO {
            DB.localTx { implicit s =>
              PUT_GENERATE_ZONE
              .bindByName(
                  'id -> generateZone.id,
                  'name -> generateZone.zoneName,
                  'adminGroupId -> generateZone.groupId,
                  'response -> generateZone.response,
                  'data -> toPB(generateZone).toByteArray
              )
              .update()
              .apply()
            }
        generateZone

          }
      }}

//
//  private def deleteGeneratedZone(zone: GenerateZone)(implicit session: DBSession): GenerateZone = {
//    DELETE_GENERATED_ZONE.bind(zone.id).update().apply()
//    zone
//  }

//  private def extractGenerateZone(columnIndex: Int): WrappedResultSet => GenerateZone = res => {
//    fromPB(VinylDNSProto.GenerateZone.parseFrom(res.bytes(columnIndex)))
//  }

//  def deleteTx(zone: GenerateZone): IO[GenerateZone] =
//    monitor("repo.ZoneJDBC.delete") {
//      IO {
//        DB.localTx { implicit s =>
//          deleteGeneratedZone(zone)
//        }
//      }
//    }



//  private def getZoneByNameInSession(zoneName: String)(implicit session: DBSession): Option[GenerateZone] =
//    GET_GENERATED_ZONE_BY_NAME.bind(zoneName).map(extractGenerateZone(1)).first().apply()



}

