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

import java.util.UUID

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import scalacss.ScalaCssReact._
import vinyldns.client.components.form.Encoding.Encoding
import vinyldns.client.components.form.InputType.InputType
import vinyldns.client.css.GlobalStyle

import scala.util.Try

object Encoding extends Enumeration {
  type Encoding = Value
  val Text, Email, Number, Password = Value
}

object InputType extends Enumeration {
  type InputType = Value
  val Input, Select, Datalist, TextArea = Value
}

object ValidatedInput {
  case class Props(
      parentOnChange: String => Callback,
      labelSize: String = "col-md-3 col-sm-3 col-xs-12",
      inputSize: String = "col-md-6 col-sm-6 col-xs-12",
      label: Option[String] = None,
      labelClass: Option[String] = None,
      inputClass: Option[String] = None,
      placeholder: Option[String] = None,
      helpText: Option[String] = None,
      value: Option[String] = None,
      encoding: Encoding = Encoding.Text,
      inputType: InputType = InputType.Input,
      options: List[(String, String)] = List(),
      disabled: Boolean = false,
      validations: Option[Validations] = None)
  case class State(isValid: Boolean = true, errorMessage: Option[String] = None)

  val component = ScalaComponent
    .builder[Props]("ValidatedInput")
    .initialState(State())
    .renderBackend[Backend]
    .getDerivedStateFromProps { (p, s) =>
      Some(Backend.stateAfterValidations(p, s))
    }
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend {
    def render(P: Props, S: State): VdomElement =
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
          toInput(P, S),
          helpText(P.helpText),
          errors(S)
        )
      )

    def toInput(P: Props, S: State): TagMod =
      P.inputType match {
        case InputType.Input =>
          <.input(
            ^.className := s"form-control ${generateInputClass(P, S)}",
            ^.`type` := fromEncoding(P),
            ^.value := P.value.getOrElse(""),
            ^.placeholder := P.placeholder.getOrElse(""),
            ^.onChange ==> ((e: ReactEventFromInput) => P.parentOnChange(e.target.value)),
            ^.required := Try(P.validations.get.required).getOrElse(false),
            ^.disabled := P.disabled
          )
        case InputType.Datalist =>
          val dataListBinding = UUID.randomUUID().toString
          List(
            <.input(
              ^.className := s"form-control ${generateInputClass(P, S)}",
              ^.`type` := fromEncoding(P),
              ^.value := P.value.getOrElse(""),
              ^.placeholder := P.placeholder.getOrElse(""),
              ^.onChange ==> ((e: ReactEventFromInput) => P.parentOnChange(e.target.value)),
              ^.required := Try(P.validations.get.required).getOrElse(false),
              ^.list := dataListBinding,
              ^.disabled := P.disabled,
              if (P.options.nonEmpty) GlobalStyle.Styles.cursorPointer
              else GlobalStyle.Styles.noop
            ),
            toDatalist(P, dataListBinding)
          ).toTagMod
        case InputType.Select =>
          <.select(
            ^.className := s"form-control ${generateInputClass(P, S)}",
            ^.value := P.value.getOrElse(""),
            ^.onChange ==> ((e: ReactEventFromInput) => P.parentOnChange(e.target.value)),
            ^.disabled := P.disabled,
            P.options.map {
              case (value, display) =>
                <.option(
                  ^.key := display,
                  ^.value := value,
                  display
                )
            }.toTagMod
          )
        case InputType.TextArea =>
          <.textarea(
            ^.className := s"form-control ${generateInputClass(P, S)}",
            ^.value := P.value.getOrElse(""),
            ^.onChange ==> ((e: ReactEventFromInput) => P.parentOnChange(e.target.value)),
            ^.disabled := P.disabled,
            ^.placeholder := P.placeholder.getOrElse(""),
            ^.`type` := fromEncoding(P),
            ^.required := Try(P.validations.get.required).getOrElse(false),
            ^.rows := 5
          )
      }

    // most browsers support the HTML5 datalist, we also load a pollyfill in the play view file
    // this allows a normal input field to search through a list of options and autofill
    def toDatalist(P: Props, datalistBinding: String): TagMod =
      if (P.options.isEmpty) TagMod.empty
      else
        <.datalist(
          ^.id := datalistBinding,
          P.options.map {
            case (value, display) => <.option(^.key := display, ^.value := value, display)
          }.toTagMod
        )

    // make use of simple html5 validations
    def fromEncoding(P: Props): String =
      P.encoding match {
        case Encoding.Text => "text"
        case Encoding.Number => "number"
        case Encoding.Email => "email"
        case Encoding.Password => "password"
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
    def stateAfterValidations(P: Props, S: State): State = {
      val validatedValue = Validations.validate(P.value.getOrElse(""), P.validations, P.options)
      validatedValue match {
        case Right(_) =>
          S.copy(isValid = true)
        case Left(error) =>
          S.copy(isValid = false, errorMessage = Some(error))
      }
    }
  }
}
