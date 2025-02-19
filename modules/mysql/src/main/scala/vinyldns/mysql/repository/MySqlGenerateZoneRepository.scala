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

import cats.effect.{ _}
import scalikejdbc._
import vinyldns.core.domain.zone.{GenerateZone, GenerateZoneRepository}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored


class MySqlGenerateZoneRepository extends GenerateZoneRepository with ProtobufConversions with Monitored {


  final val MAX_RETRIES = 10


  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the zone, which will update the values if the zone already exists
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

  /**
    * When we save a zone, if it is deleted we actually delete it from the repo.  This will force a cascade
    * delete on all linked records in the zone_access table.
    *
    * If the zone is not deleted, we have to save both the zone itself, as well as the zone access entries.
    */
//  def save(zone: GenerateZone): IO[Either[DuplicateZoneError, GenerateZone]] =
//    zone.status match {
//      case "Deleted" =>
//        val doDelete: GenerateZone => IO[Either[DuplicateZoneError, GenerateZone]] = z => deleteTx(z).map(Right(_))
//        retryWithBackoff(doDelete, zone, INITIAL_RETRY_DELAY, MAX_RETRIES)
//      case _ => retryWithBackoff(saveTx, zone, INITIAL_RETRY_DELAY, MAX_RETRIES)
//    }



   def save(generateZone: GenerateZone): IO[GenerateZone] = {
    monitor("repo.generatazone.save") {
      IO {
        DB.localTx { implicit s =>

        PUT_GENERATE_ZONE
          .bindByName(
            Seq(
              'id -> generateZone.id,
              'name -> generateZone.zoneName,
              'adminGroupId -> generateZone.groupId,
              'response -> generateZone.response,
              'data -> toPB(generateZone).toByteArray
            ): _*
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

//  def saveTx(zone: GenerateZone): IO[Either[DuplicateZoneError, GenerateZone]] =
//    monitor("repo.ZoneJDBC.save") {
//      IO {
//        DB.localTx { implicit s =>
//          getZoneByNameInSession(zone.zoneName) match {
//            case Some(foundZone) if zone.id != foundZone.id => DuplicateZoneError(zone.zoneName).asLeft
//            case _ =>
//              putGenerateZone(zone)
//              zone.asRight
//          }
//        }
//      }
//    }
//
//  def retryWithBackoff[E, A](
//                              f: A => IO[Either[E, A]],
//                              a: A,
//                              delay: FiniteDuration,
//                              maxRetries: Int
//                            ): IO[Either[E, A]] =
//    f(a).handleErrorWith { error =>
//      if (maxRetries > 0)
//        IO.sleep(delay) *> retryWithBackoff(f, a, delay * 2, maxRetries - 1)
//      else
//        IO.raiseError(error)
//    }
}

