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

package vinyldns.client.components

import java.util.UUID

import scalacss.ScalaCssReact._
import cats.implicits._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.InputType.InputFieldType
import vinyldns.client.css.GlobalStyle

import scala.util.Try

case class InputFieldValidations(
    maxSize: Option[Int] = None,
    noSpaces: Boolean = false,
    required: Boolean = false,
    matchDatalist: Boolean = false)

object InputType extends Enumeration {
  type InputFieldType = Value
  val Text, Email, Number, Password = Value
}

object ValidatedInputField {
  type DatalistValue = String
  type DatalistDisplay = String
  type DatalistOptions = Map[DatalistValue, DatalistDisplay]

  case class Props(
      parentOnChange: String => Callback,
      labelSize: String = "col-md-3 col-sm-3 col-xs-12",
      inputSize: String = "col-md-6 col-sm-6 col-xs-12",
      label: Option[String] = None,
      labelClass: Option[String] = None,
      inputClass: Option[String] = None,
      placeholder: Option[String] = None,
      helpText: Option[String] = None,
      initialValue: Option[String] = None,
      typ: InputFieldType = InputType.Text,
      datalist: DatalistOptions = Map.empty[DatalistValue, DatalistDisplay],
      disabled: Boolean = false,
      validations: Option[InputFieldValidations] = None)
  case class State(
      value: Option[String],
      isValid: Boolean = true,
      errorMessage: Option[String] = None)

  val component = ScalaComponent
    .builder[Props]("ValidatedInput")
    .initialStateFromProps { P =>
      State(P.initialValue)
    }
    .renderBackend[Backend]
    .componentDidMount { e =>
      e.backend.onChange(e.state.value.getOrElse(""), e.props)
    }
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    import Backend._

    def render(P: Props, S: State): VdomElement = {
      val datalistBinding = UUID.randomUUID().toString
      <.div(
        ^.className := "form-group",
        P.label.map { l =>
          <.label(
            ^.className := s"control-label ${P.labelClass.getOrElse("")} ${P.labelSize}",
            l
          )
        },
        <.div(
          ^.className := s"form-group ${P.inputSize}",
          <.input(
            ^.className := s"form-control ${generateInputClass(P, S)}",
            ^.`type` := toInputType(P),
            ^.value := S.value.getOrElse(""),
            ^.placeholder := P.placeholder.getOrElse(""),
            ^.onChange ==> ((e: ReactEventFromInput) => onChange(e.target.value, P)),
            ^.required := Try(P.validations.get.required).getOrElse(false),
            ^.list := datalistBinding,
            ^.disabled := P.disabled,
            if (P.datalist.nonEmpty) GlobalStyle.styleSheet.cursorPointer
            else GlobalStyle.styleSheet.noop
          ),
          toDatalist(P, datalistBinding),
          helpText(P.helpText),
          errors(S)
        )
      )
    }

    // see html5 datalist, basically a combo of <select> and <input> fields
    // this is supported in all major browsers at the time of writing this, but considering
    // adding a polyfill for cases such someone not updating Mac OS and is on an old Safari
    def toDatalist(P: Props, datalistBinding: String): TagMod =
      if (P.datalist.isEmpty) TagMod.empty
      else
        <.datalist(
          ^.id := datalistBinding,
          P.datalist.map {
            case (value, display) => <.option(^.key := display, ^.value := value, display)
          }.toTagMod
        )

    // make use of simple html5 validations
    def toInputType(P: Props): String =
      P.typ match {
        case InputType.Text => "text"
        case InputType.Number => "number"
        case InputType.Email => "email"
        case InputType.Password => "password"
      }

    def onChange(value: String, P: Props): Callback = {
      val validatedValue = validate(value, P.validations, P.datalist)
      val localOnChange = validatedValue match {
        case Right(_) =>
          bs.modState { S =>
            S.copy(value = Some(value), isValid = true)
          }
        case Left(error) =>
          bs.modState { S =>
            S.copy(value = Some(value), isValid = false, errorMessage = Some(error))
          }
      }
      localOnChange >> P.parentOnChange(value)
    }

    def generateInputClass(P: Props, S: State): String =
      if (S.isValid) P.inputClass.getOrElse("")
      else s"${P.inputClass.getOrElse("")} parsley-error"

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
  }

  object Backend {
    def validate(
        value: String,
        validations: Option[InputFieldValidations],
        datalist: DatalistOptions): Either[String, Unit] =
      validations match {
        case Some(checks) =>
          for {
            _ <- validateRequired(value, checks)
            _ <- validateMaxSize(value, checks)
            _ <- validateNoSpaces(value, checks)
            _ <- validateDatalist(value, checks, datalist)
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
            value.length <= max,
            (),
            s"Must be less than $max characters"
          )
        case None => ().asRight
      }

    def validateNoSpaces(value: String, checks: InputFieldValidations): Either[String, Unit] =
      if (checks.noSpaces)
        Either.cond(
          !value.contains(" "),
          (),
          "Cannot contain spaces"
        )
      else ().asRight

    def validateDatalist(
        value: String,
        checks: InputFieldValidations,
        datalist: DatalistOptions): Either[String, Unit] =
      if (checks.matchDatalist)
        Either.cond(
          datalist.contains(value),
          (),
          "Must match an option in list"
        )
      else ().asRight
  }
}
