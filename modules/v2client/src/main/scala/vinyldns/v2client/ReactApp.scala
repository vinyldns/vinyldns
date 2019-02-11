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

package vinyldns.v2client

import org.scalajs.dom
import vinyldns.v2client.css.AppCSS
import vinyldns.v2client.routes.AppRouter

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("ReactApp")
object ReactApp {
  @JSExport
  def main(containerId: String): Unit = {
    AppCSS.load
    AppRouter.router().renderIntoDOM(dom.document.getElementById(containerId))
  }
}
