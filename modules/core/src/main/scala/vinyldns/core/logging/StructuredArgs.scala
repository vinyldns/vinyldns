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
import vinyldns.core.domain.membership.{Group, GroupChange, User, UserChange}
import vinyldns.core.domain.record.ChangeSet
import vinyldns.core.domain.zone.{Zone, ZoneChange}

import scala.collection.JavaConverters._

object StructuredArgs {

  //custom field names
  val _Id = "id"
  val _Ids = "ids"
  val Name = "name"
  val Entity = "entity"
  val Threshold = "threshold"

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

  def entries(e: Map[_, _]): StructuredArgument =
    StructuredArguments.entries(convert(e))

  def fields[T](t: T): Map[_, _] = t match {
    case z: Zone => Map(_Id -> z.id, Name -> z.name, Type -> "zone")
    case u: User => Map(_Id -> u.id, Name -> u.userName, Type -> "user")
    case g: Group => Map(_Id -> g.id, Name -> g.name, Type -> "group")
    case gc: GroupChange => Map(_Id -> gc.id, Name -> gc.newGroup.name, Type -> "groupChange")
    case zc: ZoneChange => Map(_Id -> zc.id, Name -> zc.zone.name, Type -> "zoneChange")
    case cs: ChangeSet =>
      Map(_Id -> cs.id, "zone" -> cs.zoneId, Type -> "changeSet", "size" -> cs.changes.size)
    case uc: UserChange => Map(_Id -> uc.id, Type -> "userChange")
    case id: Id => Map(_Id -> id.id, Type -> id._type)
    case ids: Ids => Map(_Ids -> ids.ids.toArray, Type -> ids._type)
    case r: Relation =>
      Map("left" -> fields(r.id), "right" -> fields(r.ids), Type -> r._type) //1toMany
    case s: Seq[_] => Map("arr" -> s.map(fields).toArray)
    case m: Map[_, _] => m
    case x: String => Map(Value -> x)
  }

  /**
    * Builds application event information for logging
    * @param e name of the event, e.x. save, delete, create, read etc.
    * @param t entity involved in the event, e.x. zone, user, group, changeSet etc.
    * @param x additional fields
    * @tparam T entity type
    * @return Map containing the field desired for logging
    */
  def event[T](e: String, t: T, x: Map[_, _] = Map.empty): Map[_, _] =
    Map(Event -> Map("action" -> e), Entity -> (fields(t) ++ x))

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
