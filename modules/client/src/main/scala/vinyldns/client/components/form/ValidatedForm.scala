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

import japgolly.scalajs.react.CtorType.ChildArg
import japgolly.scalajs.react.Ref.WithScalaComponent
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

object ValidatedForm {
  case class Props(
      className: String,
      inputFieldProps: List[ValidatedInput.Props],
      onSubmit: Unit => Callback,
      readOnly: Boolean = false)

  case class State(
      refsToInputFields: List[WithScalaComponent[ // scalastyle:ignore
        ValidatedInput.Props,
        ValidatedInput.State,
        _,
        ValidatedInput.component.ctor.This]])

  val component = ScalaComponent
    .builder[Props]("ValidatedForm")
    .initialState(State(List()))
    .renderBackendWithChildren[Backend]
    .getDerivedStateFromProps { (p, s) =>
      val refs = for {
        _ <- p.inputFieldProps
      } yield Ref.toScalaComponent(ValidatedInput.component)
      Some(s.copy(refsToInputFields = refs))
    }
    .build

  def apply(props: Props, children: ChildArg): Unmounted[Props, State, Backend] =
    component(props)(children)

  class Backend {
    def render(P: Props, S: State, children: PropsChildren): VdomElement =
      <.form(
        ^.className := P.className,
        ^.onSubmit ==> (e => validateForm(e, P, S)),
        <.fieldset(
          ^.disabled := P.readOnly,
          P.inputFieldProps
            .zip(S.refsToInputFields)
            .map { case (props, ref) => ref.component(props) }
            .toTagMod
        ),
        children
      )

    def validateForm(e: ReactEventFromInput, P: Props, S: State): Callback = {
      val validated = S.refsToInputFields.flatMap { r =>
        r.map(mounted => mounted.state.isValid).get.asCallback.runNow()
      }

      if (!validated.contains(false)) e.preventDefaultCB >> P.onSubmit(())
      else e.preventDefaultCB >> Callback.empty
    }
  }
}
