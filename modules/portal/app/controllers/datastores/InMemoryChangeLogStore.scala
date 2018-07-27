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

package controllers.datastores

import controllers.{ChangeLogMessage, ChangeLogStore, UserChangeMessage}

import scala.collection.mutable
import scala.util.Try

class InMemoryChangeLogStore extends ChangeLogStore {
  type InMemoryLog = mutable.MutableList[ChangeLogMessage]
  val userChangeLog = new InMemoryLog()

  def log(change: ChangeLogMessage): Try[ChangeLogMessage] =
    Try {
      change match {
        case ucm: UserChangeMessage =>
          userChangeLog += ucm
          ucm
      }
    }
}
