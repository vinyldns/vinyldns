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

package vinyldns.client.pages.zonelist.components

import scalacss.ScalaCssReact._
import vinyldns.client.models.zone.{Zone, ZoneConnection, ZoneCreateInfo}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.ValidatedInputField.DatalistOptions
import vinyldns.client.components._
import vinyldns.client.http.{CreateZoneRoute, Http, HttpResponse}
import vinyldns.client.models.membership.GroupList
import vinyldns.client.components.AlertBox.setNotification
import upickle.default.write
import vinyldns.client.css.GlobalStyle

object ZoneModal {
  case class State(
      zone: ZoneCreateInfo,
      customServer: Boolean = false,
      customTransfer: Boolean = false)
  case class Props(
      http: Http,
      close: Unit => Callback,
      refreshZones: Unit => Callback,
      groupList: GroupList)

  val component = ScalaComponent
    .builder[Props]("ZoneModal")
    .initialState(State(ZoneCreateInfo()))
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props("Connect to a DNS Zone", P.close),
        <.div(
          ^.className := "modal-body",
          // header
          <.div(
            ^.className := "panel-header",
            <.p(header)
          ),
          // form
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-zone-form",
              generateInputFieldProps(P, S),
              _ => createZone(P, S)
            ),
            <.div(
              // toggle custom connection
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Custom DNS Server  ",
                  <.input(
                    GlobalStyle.styleSheet.cursorPointer,
                    ^.className := "test-custom-server",
                    ^.`type` := "checkbox",
                    ^.checked := S.customServer,
                    ^.onChange --> toggleCustomServer
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  "Check if using a custom DNS connection (uncommon). Otherwise the default will be used."
                )
              ),
              // toggle custom transfer
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Custom DNS Transfer Server  ",
                  <.input(
                    GlobalStyle.styleSheet.cursorPointer,
                    ^.className := "test-custom-transfer",
                    ^.`type` := "checkbox",
                    ^.checked := S.customTransfer,
                    ^.onChange --> toggleCustomTransfer
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  "Check if using a custom DNS Transfer connection (uncommon). Otherwise the default will be used."
                )
              ),
              // submit and close buttons
              <.div(^.className := "ln_solid"),
              <.div(
                ^.className := "form-group",
                <.button(
                  ^.`type` := "submit",
                  ^.className := "btn btn-success pull-right",
                  "Submit"
                ),
                <.button(
                  ^.`type` := "button",
                  ^.className := "btn btn-default pull-right test-close-create-zone",
                  ^.onClick --> P.close(()),
                  "Close"
                )
              )
            )
          )
        )
      )

    def generateInputFieldProps(P: Props, S: State): List[ValidatedInputField.Props] = {
      val baseProps = List(
        ValidatedInputField.Props(
          changeName,
          inputClass = Some("test-name"),
          label = Some("Zone Name"),
          helpText = Some("Name of the DNS Zone, for example vinyldns.io."),
          initialValue = Some(S.zone.name),
          validations = Some(InputFieldValidations(required = true, noSpaces = true))
        ),
        ValidatedInputField.Props(
          changeEmail,
          inputClass = Some("test-email"),
          label = Some("Email"),
          helpText = Some("Zone contact email. Preferably a multi user distribution"),
          initialValue = Some(S.zone.email),
          typ = InputType.Email,
          validations = Some(InputFieldValidations(required = true))
        ),
        ValidatedInputField.Props(
          changeAdminGroupId,
          inputClass = Some("test-group-admin"),
          label = Some("Admin Group Id"),
          helpText = Some(s"""
               |The Vinyl Group that will be given admin rights to the zone.
               | All users in the admin group will have the ability to manage
               | all records in the zone, as well as change zone level information
               | and access rules. You can create a new group from the Groups page.
              """.stripMargin),
          validations = Some(InputFieldValidations(required = true, matchDatalist = true)),
          datalist = toAdminGroupDatalist(P.groupList),
          placeholder =
            if (P.groupList.groups.isEmpty)
              Some("Please make a Vinyl group or get added to one first")
            else Some("Search for a group you are in by name or id"),
          disabled = P.groupList.groups.isEmpty
        )
      )

      baseProps ::: generateCustomConnectionFields(S) ::: generateCustomTransferFields(S)
    }

    def generateCustomConnectionFields(S: State): List[ValidatedInputField.Props] =
      if (S.customServer)
        List(
          ValidatedInputField.Props(
            changeConnectionKeyName,
            inputClass = Some("test-connection-key-name"),
            label = Some("Connection Key Name"),
            helpText = Some("""
                            |The name of the key used to sign requests to the DNS server.
                            | This is set when the zone is setup in the DNS server, and is used to
                            | connect to the DNS server when performing updates and transfers.
                          """.stripMargin),
            initialValue = S.zone.connection.map(_.keyName),
            validations = Some(InputFieldValidations(required = true))
          ),
          ValidatedInputField.Props(
            changeConnectionKey,
            inputClass = Some("test-connection-key"),
            label = Some("Connection Key"),
            helpText = Some("The secret key used to sign requests sent to the DNS server."),
            initialValue = S.zone.connection.map(_.key),
            typ = InputType.Password,
            validations = Some(InputFieldValidations(required = true))
          ),
          ValidatedInputField.Props(
            changeConnectionServer,
            inputClass = Some("test-connection-server"),
            label = Some("Connection Server"),
            helpText = Some(
              """
              | The IP Address or host name of the backing DNS server for zone transfers.
              | This host will be the target for syncing zones with Vinyl. If the port is not specified,
              | port 53 is assumed.
            """.stripMargin),
            initialValue = S.zone.connection.map(_.keyName),
            validations = Some(InputFieldValidations(required = true))
          )
        )
      else List()

    def generateCustomTransferFields(S: State): List[ValidatedInputField.Props] =
      if (S.customTransfer)
        List(
          ValidatedInputField.Props(
            changeTransferKeyName,
            inputClass = Some("test-transfer-key-name"),
            label = Some("Transfer Connection Key Name"),
            helpText = Some("""
                              |The name of the key used to sign requests to the DNS server.
                              | This is set when the zone is setup in the DNS server, and is used to
                              | connect to the DNS server when performing updates and transfers.
                            """.stripMargin),
            initialValue = S.zone.transferConnection.map(_.keyName),
            validations = Some(InputFieldValidations(required = true))
          ),
          ValidatedInputField.Props(
            changeTransferKey,
            inputClass = Some("test-transfer-key"),
            label = Some("Transfer Connection Key"),
            helpText = Some("The secret key used to sign requests sent to the DNS server."),
            initialValue = S.zone.transferConnection.map(_.key),
            typ = InputType.Password,
            validations = Some(InputFieldValidations(required = true))
          ),
          ValidatedInputField.Props(
            changeTransferServer,
            inputClass = Some("test-transfer-server"),
            label = Some("Transfer Connection Server"),
            helpText = Some(
              """
                | The IP Address or host name of the backing DNS server for zone transfers.
                | This host will be the target for syncing zones with Vinyl. If the port is not specified,
                | port 53 is assumed.
              """.stripMargin),
            initialValue = S.zone.transferConnection.map(_.keyName),
            validations = Some(InputFieldValidations(required = true))
          )
        )
      else List()

    def toggleCustomServer(): Callback =
      bs.modState { s =>
        s.customTransfer match {
          case turningOn if false =>
            val zone = s.zone.copy(connection = Some(ZoneConnection()))
            s.copy(zone = zone, customTransfer = !turningOn)
          case turningOff if true =>
            val zone = s.zone.copy(connection = None)
            s.copy(zone = zone, customServer = !turningOff)
        }
      }

    def toggleCustomTransfer(): Callback =
      bs.modState { s =>
        s.customTransfer match {
          case turningOn if false =>
            val zone = s.zone.copy(transferConnection = Some(ZoneConnection()))
            s.copy(zone = zone, customTransfer = !turningOn)
          case turningOff if true =>
            val zone = s.zone.copy(transferConnection = None)
            s.copy(zone = zone, customTransfer = !turningOff)
        }
      }

    def createZone(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to connect to zone ${S.zone.name}?",
        Callback
          .lazily {
            val onFailure = { httpResponse: HttpResponse =>
              setNotification(
                P.http.toNotification(s"connecting to zone ${S.zone.name}", httpResponse))
            }
            val onSuccess = { (httpResponse: HttpResponse, _: Option[Zone]) =>
              setNotification(
                P.http.toNotification(s"connecting to zone ${S.zone.name}", httpResponse)) >>
                P.close(()) >>
                P.refreshZones(())
            }
            P.http.post(CreateZoneRoute, write(S.zone), onSuccess, onFailure)
          }
      )

    def toAdminGroupDatalist(groupList: GroupList): DatalistOptions =
      groupList.groups.map(g => g.id -> s"${g.name} (${g.id})").toMap

    def changeName(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.copy(name = value))
      }

    def changeEmail(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.copy(email = value))
      }

    def changeAdminGroupId(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.copy(adminGroupId = value))
      }

    def changeConnectionKeyName(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewConnectionKeyName(value))
      }

    def changeConnectionKey(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewConnectionKey(value))
      }

    def changeConnectionServer(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewConnectionServer(value))
      }

    def changeTransferKeyName(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewTransferKeyName(value))
      }

    def changeTransferKey(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewTransferKey(value))
      }

    def changeTransferServer(value: String): Callback =
      bs.modState { s =>
        s.copy(zone = s.zone.withNewTransferServer(value))
      }

    val header: String =
      """
        |Use this form to connect to an already existing DNS zone. If you need a new zone created in DNS,
        | please follow guidelines set by your VinylDNS Administrators.
      """.stripMargin
  }
}
