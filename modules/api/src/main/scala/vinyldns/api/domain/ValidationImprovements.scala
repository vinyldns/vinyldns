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

package vinyldns.api.domain
import cats.data.ValidatedNel
import cats.implicits._

object ValidationImprovements {

  /**
    * Runs a validator on an optional value, returning a ValidatedNel of an option if it is present.
    *
    * If the value is not present, will return None as success
    *
    */
  def validateIfDefined[E, A](
      value: => Option[A]
  )(validator: A => ValidatedNel[E, A]): ValidatedNel[E, Option[A]] =
    value match {
      case None => Option.empty[A].validNel[E]
      case Some(a) => validator(a).map(Some(_))
    }
}
