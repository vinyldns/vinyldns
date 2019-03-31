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

package vinyldns.client.components.form

import cats.implicits._
import vinyldns.client.routes.AppRouter

case class Validations(
    maxSize: Option[Int] = None,
    noSpaces: Boolean = false,
    required: Boolean = false,
    matchOptions: Boolean = false,
    uuid: Boolean = false)

object Validations {
  def validate(
      value: String,
      validations: Option[Validations],
      options: List[(String, String)]): Either[String, Unit] =
    validations match {
      case Some(checks) =>
        for {
          _ <- validateRequired(value, checks)
          _ <- validateMaxSize(value, checks)
          _ <- validateNoSpaces(value, checks)
          _ <- validateIsOption(value, checks, options)
          _ <- validateUUID(value, checks)
        } yield ()
      case None => ().asRight
    }

  def validateRequired(value: String, checks: Validations): Either[String, Unit] =
    if (checks.required)
      Either.cond(
        value.length > 0,
        (),
        "Required"
      )
    else ().asRight

  def validateMaxSize(value: String, checks: Validations): Either[String, Unit] =
    checks.maxSize match {
      case Some(max) =>
        Either.cond(
          value.length <= max,
          (),
          s"Must be less than $max characters"
        )
      case None => ().asRight
    }

  def validateNoSpaces(value: String, checks: Validations): Either[String, Unit] =
    if (checks.noSpaces)
      Either.cond(
        !value.contains(" "),
        (),
        "Cannot contain spaces"
      )
    else ().asRight

  def validateIsOption(
      value: String,
      checks: Validations,
      options: List[(String, String)]): Either[String, Unit] =
    if (checks.matchOptions)
      Either.cond(
        options.exists { case (v, _) => v == value },
        (),
        "Must choose an option in list"
      )
    else ().asRight

  def validateUUID(value: String, checks: Validations): Either[String, Unit] =
    if (checks.uuid)
      Either.cond(
        value.matches(AppRouter.uuidRegex),
        (),
        "Must be a valid ID (not name)"
      )
    else ().asRight
}
