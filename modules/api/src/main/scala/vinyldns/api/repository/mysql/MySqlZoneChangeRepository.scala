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

package vinyldns.api.repository.mysql

import cats.effect.IO
import org.slf4j.LoggerFactory
import vinyldns.core.domain.zone._
import vinyldns.core.protobuf._
import vinyldns.core.route.Monitored
import scalikejdbc._
import vinyldns.proto.VinylDNSProto

import scala.util.Try

class MySqlZoneChangeRepository
    extends ZoneChangeRepository
    with ProtobufConversions
    with Monitored {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneChangeRepository])

  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the zone change,
    * which will update the values if the zone change already exists
    * similar to a PUT in a KV store
    */
  private final val PUT_ZONE_CHANGE =
    sql"""
      |INSERT INTO zone_change (change_id, zone_id, data, created)
      |  VALUES ({change_id}, {zone_id}, {data}, {created}) ON DUPLICATE KEY
      |  UPDATE data=VALUES(data);
      """.stripMargin

  private final val LIST_ZONES_CHANGES =
    sql"""
      |SELECT zc.data
      |  FROM zone_change zc
      |  WHERE zc.zone_id = {zoneId}
      |  ORDER BY zc.created DESC
      |  LIMIT {maxItems}
      |  OFFSET {startFrom}
    """.stripMargin

  override def save(zoneChange: ZoneChange): IO[ZoneChange] =
    monitor("repo.ZoneChangeMySql.save") {
      logger.info(s"Saving zone change ${zoneChange.id}")
      IO {
        DB.localTx { implicit s =>
          PUT_ZONE_CHANGE
            .bindByName(
              'change_id -> zoneChange.id,
              'zone_id -> zoneChange.zoneId,
              'data -> toPB(zoneChange).toByteArray,
              'created -> zoneChange.created
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
      maxItems: Int): IO[ListZoneChangesResults] =
    // sorted from most recent, startFrom is an offset from the most recent change
    monitor("repo.ZoneChangeMySql.listZoneChanges") {
      logger.info(s"Getting zone changes for zone $zoneId")
      IO {
        DB.readOnly { implicit s =>
          val startValue = Try { startFrom.getOrElse("0").toInt }.getOrElse(0)
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
          val nextId =
            if (queryResult.size < maxItems) None else Some((startValue + maxItems).toString)
          val startFromReturn = startFrom match {
            case Some(i) => Some(i.toString)
            case None => None
          }

          ListZoneChangesResults(maxQueries, nextId, startFromReturn, maxItems)
        }
      }
    }

  private def extractZoneChange(colIndex: Int): WrappedResultSet => ZoneChange = res => {
    fromPB(VinylDNSProto.ZoneChange.parseFrom(res.bytes(colIndex)))
  }
}
