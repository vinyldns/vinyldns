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

package vinyldns.v2client.components

import cats.implicits._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

import scala.util.Try

case class InputFieldValidations(
    maxSize: Option[Int] = None,
    canContainSpaces: Boolean = true,
    required: Boolean = false)

object ValidatedInputField {
  case class Props(
      parentOnChange: String => Callback,
      labelClass: String = "control-label",
      labelSize: String = "col-md-3 col-sm-3 col-xs-12",
      inputClass: String = "form-control",
      inputSize: String = "col-md-6 col-sm-6 col-xs-12",
      label: Option[String] = None,
      placeholder: Option[String] = None,
      helpText: Option[String] = None,
      initialValue: Option[String] = None,
      isNumber: Boolean = false,
      isEmail: Boolean = false,
      validations: Option[InputFieldValidations] = None)
  case class State(
      value: Option[String],
      isValid: Boolean = true,
      errorMessage: Option[String] = None)

  class Backend(bs: BackendScope[Props, State]) {
    def toInputType(P: Props): String =
      if (P.isNumber) "number"
      else if (P.isEmail) "email"
      else "text"

    def onChange(e: ReactEventFromInput, P: Props): Callback = {
      val target = e.target
      val value = target.value
      val validatedValue = validate(value, P)
      val localOnChange = validatedValue match {
        case Right(_) =>
          bs.modState { S =>
            // this is kinda cool, in HTML5 if an input has `customValidity` set to a non empty
            // string it won't let you submit the form
            target.setCustomValidity("")
            S.copy(value = Some(value), isValid = true)
          }
        case Left(error) => {
          bs.modState { S =>
            target.setCustomValidity(error)
            S.copy(value = Some(value), isValid = false, errorMessage = Some(error))
          }
        }
      }
      localOnChange >> P.parentOnChange(value)
    }

    def validate(value: String, P: Props): Either[String, Unit] =
      P.validations match {
        case Some(checks) =>
          for {
            _ <- validateRequired(value, checks)
            _ <- validateMaxSize(value, checks)
            _ <- validateNoSpaces(value, checks)
          } yield ()
        case None => ().asRight
      }

    def validateRequired(value: String, checks: InputFieldValidations): Either[String, Unit] =
      if (checks.required)
        Either.cond(
          value.length > 0,
          (),
          "Required"
        )
      else ().asRight

    def validateMaxSize(value: String, checks: InputFieldValidations): Either[String, Unit] =
      checks.maxSize match {
        case Some(max) =>
          Either.cond(
            value.length < max,
            (),
            s"Must be less than $max characters"
          )
        case None => ().asRight
      }

    def validateNoSpaces(value: String, checks: InputFieldValidations): Either[String, Unit] =
      if (!checks.canContainSpaces)
        Either.cond(
          !value.contains(" "),
          (),
          "Cannot contain spaces"
        )
      else ().asRight

    def generateInputClass(P: Props, S: State): String =
      if (S.isValid) P.inputClass
      else s"${P.inputClass} parsley-error"

    def errors(S: State): VdomNode =
      if (S.isValid) <.span
      else
        <.ul(
          ^.className := "parsley-errors-list filled",
          <.li(
            ^.className := "parley-required",
            S.errorMessage.getOrElse[String]("Invalid")
          )
        )

    def helpText(text: Option[String]): VdomNode =
      text match {
        case Some(t) => <.div(^.className := "help-block", t)
        case None => <.div
      }

    def render(P: Props, S: State): VdomElement =
      <.div(
        ^.className := "form-group",
        P.label.map { l =>
          <.label(
            ^.className := s"${P.labelClass} ${P.labelSize}",
            l
          )
        },
        <.div(
          ^.className := P.inputSize,
          <.input(
            ^.className := generateInputClass(P, S),
            ^.`type` := toInputType(P),
            ^.value := S.value.getOrElse(""),
            ^.placeholder := P.placeholder.getOrElse(""),
            ^.onChange ==> (e => onChange(e, P)),
            ^.required := Try(P.validations.get.required).getOrElse(false)
          ),
          helpText(P.helpText),
          errors(S)
        )
      )
  }

  val component = ScalaComponent
    .builder[Props]("Input")
    .initialStateFromProps { P =>
      State(P.initialValue)
    }
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)
}
