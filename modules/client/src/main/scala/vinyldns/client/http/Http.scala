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

package vinyldns.client.http

import japgolly.scalajs.react.Callback
import vinyldns.client.models.Notification
import vinyldns.client.models.user.User

trait Http {
  val csrf: String
  val loggedInUser: User
  type OnSuccess[T] = (HttpResponse, Option[T]) => Callback
  type OnFailure = HttpResponse => Callback

  def get[T](route: RequestRoute[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback

  def post[T](
      route: RequestRoute[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback

  def put[T](
      route: RequestRoute[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback

  def delete[T](route: RequestRoute[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback

  def withConfirmation(message: String, cb: Callback): Callback

  def toNotification(
      action: String,
      httpResponse: HttpResponse,
      onlyOnError: Boolean = false,
      verbose: Boolean = false): Option[Notification]

  def isError(httpResponse: HttpResponse): Boolean
}
