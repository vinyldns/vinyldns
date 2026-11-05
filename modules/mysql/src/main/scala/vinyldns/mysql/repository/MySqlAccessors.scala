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

import org.slf4j.Logger
import vinyldns.core.domain.membership.User

private[repository] object MySqlAccessors {
  private final val MAX_ACCESSORS = 30
  private final val EVERYONE_ACCESSOR = "EVERYONE"

  def buildZoneSearchAccessorList(user: User, groupIds: Seq[String], logger: Logger): Seq[String] = {
    val allAccessors = user.id +: groupIds

    if (allAccessors.length > MAX_ACCESSORS) {
      logger.warn(
        s"User ${user.userName} with id ${user.id} has more than $MAX_ACCESSORS user/group memberships; some accessible zones may not be returned"
      )
    }

    allAccessors.take(MAX_ACCESSORS) :+ EVERYONE_ACCESSOR
  }
}
