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

import japgolly.scalajs.react.CtorType.ChildArg
import japgolly.scalajs.react.Ref.WithScalaComponent
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

object ValidatedForm {
  case class Props(
      className: String,
      inputFieldProps: List[ValidatedInputField.Props],
      onSubmit: Unit => Callback)

  case class State(
      refsToInputFields: List[WithScalaComponent[ // scalastyle:ignore
        ValidatedInputField.Props,
        ValidatedInputField.State,
        _,
        ValidatedInputField.component.ctor.This]])

  val component = ScalaComponent
    .builder[Props]("ValidatedForm")
    .initialState(State(List()))
    .renderBackendWithChildren[Backend]
    .componentWillMount(e => e.backend.toState(e.props))
    .componentWillReceiveProps(e => e.backend.toState(e.nextProps))
    .build

  def apply(props: Props, children: ChildArg): Unmounted[Props, State, Backend] =
    component(props)(children)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State, children: PropsChildren): VdomElement =
      <.form(
        ^.className := P.className,
        ^.onSubmit ==> (e => validateForm(e, P, S)),
        P.inputFieldProps
          .zip(S.refsToInputFields)
          .map { z =>
            z._2.component(z._1)
          }
          .toTagMod,
        children
      )

    def validateForm(e: ReactEventFromInput, P: Props, S: State): Callback = {
      val validated = S.refsToInputFields.flatMap { r =>
        r.map(mounted => mounted.state.isValid).get.asCallback.runNow()
      }

      if (!validated.contains(false)) e.preventDefaultCB >> P.onSubmit(())
      else e.preventDefaultCB >> Callback.empty
    }

    def toState(P: Props): Callback = {
      val refs = for {
        _ <- P.inputFieldProps
      } yield Ref.toScalaComponent(ValidatedInputField.component)

      bs.modState(_.copy(refsToInputFields = refs))
    }
  }
}
