package vinyldns.api.repository.mysql

import cats.effect.IO
import org.slf4j.LoggerFactory
import vinyldns.core.domain.zone._
import vinyldns.core.protobuf._
import vinyldns.core.route.Monitored
import scalikejdbc._
import vinyldns.proto.VinylDNSProto

import scala.util.Try

class MySqlZoneChangeRepository extends ZoneChangeRepository with ProtobufConversions with Monitored {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneChangeRepository])

  /**
    * use INSERT INTO ON DUPLICATE KEY UPDATE for the zone change,
    * which will update the values if the zone change already exists
    * similar to a PUT in a KV store
    */
  private final val PUT_ZONE_CHANGE =
    sql"""
      |INSERT INTO zone_change (change_id, zone_id, status, data, created)
      |  VALUES ({change_id}, {zone_id}, {status}, {data}, {created}) ON DUPLICATE KEY
      |  UPDATE data=VALUES(data);
      """.stripMargin

  private final val LIST_ZONES_CHANGES =
    sql"""
      |SELECT zc.data
      |  FROM zone_change zc
      |  WHERE zc.zone_id = {zoneId}
      |  ORDER BY zc.created DESC
      |  OFFSET {startFrom}
      |  LIMIT {maxItems}
    """.stripMargin

  private final val GET_ALL_PENDING_ZONE_IDS =
    sql"""
      |SELECT DISTINCT zc.zone_id
      |  FROM zone_change zc
      |  WHERE zc.status = {pending}
      |  ORDER BY zc.created DESC
    """.stripMargin

  private final val GET_PENDING_ZONE_CHANGES_IN_ZONE =
    sql"""
      |SELECT zc.data
      |  FROM zone_change zc
      |  WHERE zc.zone_id = {zoneId}
      |    AND (
      |      zc.status = {pending}
      |      OR zc.status = {complete}
      |      )
      |  ORDER BY zc.created DESC
    """.stripMargin

  override def save(zoneChange: ZoneChange): IO[ZoneChange] = {
    monitor("repo.ZoneChangeMySql.save") {
      IO {
        DB.localTx { implicit s =>
          PUT_ZONE_CHANGE
            .bindByName(
              'change_id -> zoneChange.id,
              'zone_id -> zoneChange.zoneId,
              'status -> zoneChange.status,
              'data -> toPB(zoneChange),
              'created -> zoneChange.created
            )
            .update()
            .apply()

          zoneChange
        }
      }
    }
  }

  override def getPending(zoneId: String): IO[List[ZoneChange]] = {
    // gets 'pending' and 'complete' (non-synced) zone changes in zone
    monitor("repo.ZoneChangeMySql.getPendingZoneChanges") {
      IO {
        DB.readOnly { implicit s =>
          GET_PENDING_ZONE_CHANGES_IN_ZONE
            .bindByName(
              'zoneId -> zoneId,
              'pending -> ZoneChangeStatus.Pending,
              'complete -> ZoneChangeStatus.Complete)
            .map(extractZoneChange(_))
            .list()
            .apply()
        }
      }
    }
  }

  override def getAllPendingZoneIds(): IO[List[String]] = {
    // gets zoneIds that have 'pending' changes
    monitor("repo.ZoneChangeMySql.getAllPendingZoneIds") {
      IO {
        DB.readOnly { implicit s =>
          GET_ALL_PENDING_ZONE_IDS
            .bindByName('pending -> ZoneChangeStatus.Pending)
            .map(_.string("zone_id"))
            .list()
            .apply()
        }
      }
    }
  }

  override def listZoneChanges(zoneId: String, startFrom: Option[String], maxItems: Int): IO[ListZoneChangesResults] = {
    // sorted from most recent, startFrom is an offset from the most recent change
    monitor("repo.ZoneChangeMySql.listZoneChanges") {
      IO {
        DB.readOnly { implicit s =>
          val startValue = Try {startFrom.getOrElse("0").toInt}.getOrElse(0)
          // maxItems gets a plus one to know if the table is exhausted so we can conditionally give a nextId
          val queryResult = LIST_ZONES_CHANGES
            .bindByName(
              'zoneId -> zoneId,
              'startFrom -> startValue,
              'maxItems -> + 1
            )
            .map(extractZoneChange())
            .list()
            .apply()
          val maxQueries = queryResult.take(maxItems)

          // nextId is Option[String] to maintains backwards compatibility
          val nextId = if (queryResult.size < maxItems) None else Some((startValue + maxItems).toString)
          val startFromReturn = startFrom match {
            case Some(i) => Some(i.toString)
            case None => None
          }

          ListZoneChangesResults(maxQueries, nextId, startFromReturn, maxItems)
        }
      }
    }
  }

  private def extractZoneChange(): WrappedResultSet => ZoneChange = res => {
    fromPB(VinylDNSProto.ZoneChange.parseFrom(res.bytes("data")))
  }
}
