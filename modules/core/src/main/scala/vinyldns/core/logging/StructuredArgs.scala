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

package vinyldns.core.logging

import net.logstash.logback.argument.{StructuredArgument, StructuredArguments}
import vinyldns.core.domain.batch.BatchChange
import vinyldns.core.domain.membership.{Group, GroupChange, User, UserChange}
import vinyldns.core.domain.record.{ChangeSet, RecordSet, RecordSetChange}
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneCommand}
import vinyldns.core.queue.{CommandMessage, MessageCount}

import scala.collection.JavaConverters._

object StructuredArgs {

  //custom field names
  val _Id = "id"
  val _Ids = "ids"
  val Name = "name"
  val Entity = "entity"
  val Threshold = "threshold"
  val Account = "account"
  val Created = "created"
  val userId = "userId"
  val changeType = "changeType"
  val Status = "status"
  val systemMessage = "systemMessage"
  val ZoneId = "zoneId"
  val Zone = "zone"
  val ZoneName = "zoneName"

  //common event types
  val Event = "event"
  val Type = "type"
  val Save = "save"
  val Delete = "delete"
  val Read = "read"
  val ReadAll = "readAll"
  val Value = "value"

  case class Id(id: String, _type: String)
  case class Ids(ids: Set[String], _type: String)
  case class Relation(id: Id, ids: Ids, _type: String)
  case class Detail[T](c: T)

  /**
    * Builds the { @link StructuredArgument} from the given Map.
    *
    * @param e Map containing fields to be logged.
    * @return { @link net.logstash.logback.argument.StructuredArgument}
    */
  def entries(e: Map[_, _]): StructuredArgument =
    StructuredArguments.entries(convert(e))

  /**
    * Builds application event information for logging
    * @param e name of the event, e.x. save, delete, create, read, addMember etc.
    * @param t entity involved in the event, e.x. zone, user, group, changeSet etc.
    * @param x additional fields
    * @tparam T entity type
    * @return Map containing the field desired for logging
    */
  def event[T](e: String, t: T, x: Map[_, _] = Map.empty): Map[_, _] =
    Map(Event -> Map("action" -> e), Entity -> (fields(t) ++ x))

  /**
    * handler function to extract acceptable fields from domain objects to include to log statements.
    *
    * @param t domain object of type T
    * @tparam T type construct for domain object
    * @return Map containing the acceptable fields
    */
  private def fields[T](t: T): Map[_, _] = t match {
    //core domains
    case u: User => Map(_Id -> u.id, Name -> u.userName, Type -> "user")
    case g: Group => Map(_Id -> g.id, Name -> g.name, Type -> "group")
    case z: Zone => Map(_Id -> z.id, Name -> z.name, Type -> "zone")
    case gc: GroupChange => Map(_Id -> gc.id, Name -> gc.newGroup.name, Type -> "groupChange")
    case cs: ChangeSet =>
      Map(_Id -> cs.id, "zone" -> cs.zoneId, Type -> "changeSet", "size" -> cs.changes.size)
    case uc: UserChange => Map(_Id -> uc.id, Type -> "userChange")
    case mc: MessageCount => Map("count" -> mc.value, Type -> "message")
    case zc: ZoneChange => Map(_Id -> zc.id, Name -> zc.zone.name, Type -> "zoneChange")
    case bc: BatchChange => Map(_Id -> bc.id, "count" -> bc.changes.size, Type -> "batchChange")
    case cm: CommandMessage =>
      Map(
        _Id -> cm.id.value,
        "commandId" -> cm.command.id,
        ZoneId -> cm.command.zoneId,
        Type -> "commandMessage")
    case zc: ZoneCommand =>
      Map(_Id -> zc.id, Type -> "zoneCommand", Name -> zc.zoneId)

    //wrapper
    case id: Id => Map(_Id -> id.id, Type -> id._type)
    case ids: Ids => Map(_Ids -> ids.ids.toArray, Type -> ids._type)
    case Detail(z) => detailedFields(z)
    case r: Relation =>
      Map("left" -> fields(r.id), "right" -> r.ids.ids.toArray, Type -> r._type) //1toMany

    case s: Seq[_] => Map("arr" -> s.map(fields).toArray)
    case m: Map[_, _] => m
    case x: String => Map(Value -> x)
  }

  private def detailedFields[T](t: T): Map[_, _] = {
    t match {
      case dzc: ZoneChange =>
        Map(
          Id -> dzc.id,
          Type -> "zoneChange",
          userId -> dzc.userId,
          changeType -> dzc.changeType,
          Status -> dzc.status.toString,
          systemMessage -> dzc.systemMessage.toString,
          Created -> dzc.created.getMillis,
          Zone -> fields(Detail(dzc.zone))
        )

      case drs: RecordSetChange =>
        Map(
          Id -> drs.id,
          Type -> "recordSetChange",
          userId -> drs.userId,
          changeType -> drs.changeType,
          Status -> drs.status.toString,
          systemMessage -> drs.systemMessage.toString,
          ZoneId -> drs.zone.id,
          ZoneName -> drs.zone.name,
          "singleBatchChangeIds" -> drs.singleBatchChangeIds.toArray,
          "recordSet" -> fields(Detail(drs.recordSet))
        )

      case dz: Zone =>
        Map(
          _Id -> dz.id,
          Type -> "zone",
          Name -> dz.name,
          Account -> dz.account,
          Status -> dz.status.toString,
          Created -> dz.created,
          "adminGroupId" -> dz.adminGroupId,
          "shared" -> dz.shared,
          "connection" -> dz.connection.toString,
          "transferConnection" -> dz.transferConnection.toString,
          "reverse" -> dz.isReverse,
          "isTest" -> dz.isTest,
          "updated" -> dz.updated.fold(0L)(_.getMillis),
          "latestSync" -> dz.latestSync.fold(0L)(_.getMillis)
        )

      case rs: RecordSet =>
        Map(
          _Id -> rs.id,
          Type -> "recordSet",
          Name -> rs.name,
          Account -> rs.account,
          Created -> rs.created.getMillis,
          Status -> rs.status.toString,
          ZoneId -> rs.zoneId,
          "ownerGroupId" -> rs.ownerGroupId,
          "recordSetType" -> rs.typ.toString,
          "updated" -> rs.updated.fold(0L)(_.getMillis)
        )
    }
  }

  /**
    * Converts scala Map to Java Map required for structured logging library.
    *
    * @param m scala map
    * @return a java map
    */
  private def convert(m: Map[_, _]): java.util.Map[_, _] =
    m.map { e =>
      e._2 match {
        case mm: Map[_, _] => e._1 -> convert(mm)
        case _ => e
      }
    }.asJava
}
