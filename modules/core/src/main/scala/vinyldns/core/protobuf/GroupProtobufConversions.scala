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

package vinyldns.core.protobuf

import java.time._
import vinyldns.core.domain.membership.{Group, GroupChange, GroupChangeType, GroupStatus}
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._

trait GroupProtobufConversions {

  def fromPB(pb: VinylDNSProto.Group): Group =
    Group(
      id = pb.getId,
      name = pb.getName,
      email = pb.getEmail,
      description = if (pb.hasDescription) Option(pb.getDescription) else None,
      created = Instant.ofEpochMilli(pb.getCreated),
      status = GroupStatus.withName(pb.getStatus),
      memberIds = pb.getMemberIdsList.asScala.toSet,
      adminUserIds = pb.getAdminUserIdsList.asScala.toSet
    )

  def fromPB(groupChange: VinylDNSProto.GroupChange): GroupChange = {
    val newGroup = fromPB(groupChange.getNewGroup)
    val oldGroup = if (groupChange.hasOldGroup) Some(fromPB(groupChange.getOldGroup)) else None

    GroupChange(
      id = groupChange.getGroupChangeId,
      changeType = GroupChangeType.withName(groupChange.getChangeType),
      userId = groupChange.getUserId,
      created = Instant.ofEpochMilli(groupChange.getCreated),
      newGroup = newGroup,
      oldGroup = oldGroup
    )
  }

  def toPB(group: Group): VinylDNSProto.Group = {
    val pb = VinylDNSProto.Group.newBuilder()
    pb.setId(group.id)
    pb.setName(group.name)
    pb.setEmail(group.email)
    pb.setCreated(group.created.toEpochMilli)
    pb.setStatus(group.status.toString)

    group.memberIds.foreach(pb.addMemberIds)
    group.adminUserIds.foreach(pb.addAdminUserIds)
    group.description.foreach(pb.setDescription)

    pb.build()
  }

  def toPB(groupChange: GroupChange): VinylDNSProto.GroupChange = {
    val pb = VinylDNSProto.GroupChange
      .newBuilder()
      .setGroupChangeId(groupChange.id)
      .setGroupId(groupChange.newGroup.id)
      .setChangeType(groupChange.changeType.toString)
      .setUserId(groupChange.userId)
      .setCreated(groupChange.created.toEpochMilli)
      .setNewGroup(toPB(groupChange.newGroup))
    groupChange.oldGroup.foreach(oldGroup => pb.setOldGroup(toPB(oldGroup)))

    pb.build()
  }
}
