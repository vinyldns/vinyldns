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

package vinyldns.client.pages.zone.list.components

import scalacss.ScalaCssReact._
import vinyldns.client.models.zone.{ZoneConnection, ZoneCreateInfo, ZoneModalInfo, ZoneResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components._
import vinyldns.client.http.{CreateZoneRoute, Http, HttpResponse, UpdateZoneRoute}
import vinyldns.client.models.membership.{GroupListResponse, GroupResponse}
import vinyldns.client.components.AlertBox.addNotification
import upickle.default.write
import vinyldns.client.css.GlobalStyle
import vinyldns.client.components.JsNative._
import vinyldns.client.components.form._

object ZoneModal {
  case class State(
      zone: ZoneModalInfo,
      customServer: Boolean = false,
      customTransfer: Boolean = false,
      customBackendId: Boolean = false,
      isUpdate: Boolean = false)
  case class Props(
      http: Http,
      close: Unit => Callback,
      refreshZones: Unit => Callback,
      groupList: GroupListResponse,
      backendIds: List[String],
      existing: Option[ZoneResponse] = None)

  val component = ScalaComponent
    .builder[Props]("ZoneModal")
    .initialStateFromProps { p =>
      p.existing match {
        case Some(e) =>
          State(
            e,
            e.connection.isDefined,
            e.transferConnection.isDefined,
            e.backendId.isDefined,
            isUpdate = true)
        case None => State(ZoneCreateInfo("", "", "", None, shared = false, None, None, None))
      }
    }
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props(toTitle(S), P.close),
        <.div(
          ^.className := "modal-body",
          // header
          <.div(
            ^.className := "panel-header",
            if (S.isUpdate) <.p else <.p(createHeader)
          ),
          // form
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-zone-form",
              generateInputFieldProps(P, S),
              _ => if (S.isUpdate) updateZone(P, S) else createZone(P, S)
            ),
            // toggle backend id
            <.div(
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Backend ID  ",
                  <.input(
                    GlobalStyle.Styles.cursorPointer,
                    ^.className := "test-toggle-backendid",
                    ^.`type` := "checkbox",
                    ^.checked := S.customBackendId,
                    ^.onChange --> toggleBackendId
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  """
                    |Check if using the ID of a pre-configured connection (uncommon).
                  """.stripMargin.replaceAll("\n", " ")
                )
              ),
              // toggle custom connection
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Custom DNS Server  ",
                  <.input(
                    GlobalStyle.Styles.cursorPointer,
                    ^.className := "test-custom-server",
                    ^.`type` := "checkbox",
                    ^.checked := S.customServer,
                    ^.onChange --> toggleCustomServer
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  """
                    |Check if using a custom DNS connection (uncommon).
                    |Otherwise the default connection will be used.
                  """.stripMargin.replaceAll("\n", " ")
                )
              ),
              // toggle custom transfer
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Custom DNS Transfer Server  ",
                  <.input(
                    GlobalStyle.Styles.cursorPointer,
                    ^.className := "test-custom-transfer",
                    ^.`type` := "checkbox",
                    ^.checked := S.customTransfer,
                    ^.onChange --> toggleCustomTransfer
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  """
                    |Check if using a custom DNS Transfer connection (uncommon).
                    |Otherwise the default connection will be used.
                  """.stripMargin.replaceAll("\n", " ")
                )
              ),
              // toggle shared
              <.div(
                ^.className := "form-group",
                <.label(
                  ^.className := "check col-md-3 col-sm-3 col-xs-12 control-label",
                  "Shared  ",
                  <.input(
                    GlobalStyle.Styles.cursorPointer,
                    ^.className := "test-shared",
                    ^.`type` := "checkbox",
                    ^.checked := S.zone.shared,
                    ^.onChange --> toggleShared
                  )
                ),
                <.div(
                  ^.className := "help-block col-md-6 col-sm-6 col-xs-12",
                  """
                    |Normally updates to DNS records are restricted to the Zone Admin Group and those given
                    | permissions via Zone Access Rules. A Shared Zone also allows ANY VinylDNS user to
                    | create/update DNS records and claim them for another Group.
                    | The Zone Admins will always be able to update all records.
                  """.stripMargin
                )
              ),
              // submit and close buttons
              <.div(^.className := "ln_solid"),
              <.div(
                ^.className := "form-group",
                <.button(
                  ^.`type` := "submit",
                  ^.className := "btn btn-success pull-right",
                  ^.disabled := isUpdateDisabled(P, S),
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

    def generateInputFieldProps(P: Props, S: State): List[ValidatedInput.Props] = {
      val baseProps = List(
        ValidatedInput.Props(
          changeName,
          inputClass = Some("test-name"),
          label = Some("Zone Name"),
          helpText = Some("Name of the DNS Zone, for example vinyldns.io."),
          value = Some(S.zone.name),
          validations = Some(Validations(required = true, noSpaces = true)),
          disabled = S.isUpdate
        ),
        ValidatedInput.Props(
          changeEmail,
          inputClass = Some("test-email"),
          label = Some("Email"),
          helpText = Some("Zone contact email. Preferably a multi user distribution"),
          value = Some(S.zone.email),
          encoding = Encoding.Email,
          validations = Some(Validations(required = true))
        ),
        ValidatedInput.Props(
          changeAdminGroup(_, P.groupList.groups),
          value = S.zone.adminGroupName,
          inputClass = Some("test-group-admin"),
          label = Some("Admin Group"),
          helpText = Some(s"""
               |The Vinyl Group that will be given admin rights to the zone.
               | All users in the admin group will have the ability to manage
               | all records in the zone, as well as change zone level information
               | and access rules. You can create a new group from the Groups page.
              """.stripMargin),
          validations = Some(Validations(required = true, matchGroup = true)),
          options = toAdminGroupDatalist(P.groupList),
          placeholder =
            if (P.groupList.groups.isEmpty)
              Some("Please make a Vinyl group or get added to one first")
            else Some("Search for a group you are in by name or id"),
          disabled = P.groupList.groups.isEmpty,
          inputType = InputType.Datalist
        )
      )

      baseProps :::
        generateCustomBackendIdField(P, S) :::
        generateCustomConnectionFields(S) :::
        generateCustomConnectionFields(S, isTransfer = true)
    }

    def generateCustomBackendIdField(P: Props, S: State): List[ValidatedInput.Props] =
      if (S.customBackendId)
        List(
          ValidatedInput.Props(
            changeBackendId,
            value = Some(S.zone.backendId.getOrElse("")),
            inputClass = Some("test-backend-id"),
            label = Some("Backend ID"),
            inputType = InputType.Select,
            helpText = Some(
              """
                |The ID for a pre-configured DNS connection configuration. Please contact your DNS admin team
                |for more information if you believe you need a custom connection. In most cases, the default
                |connection will suffice.
              """.stripMargin
            ),
            options = List(("", "")) ::: P.backendIds.map(o => (o, o))
          )
        )
      else List()

    def generateCustomConnectionFields(
        S: State,
        isTransfer: Boolean = false): List[ValidatedInput.Props] =
      if ((S.customServer && !isTransfer) || (S.customTransfer && isTransfer)) {
        val connection = if (isTransfer) S.zone.transferConnection else S.zone.connection
        List(
          ValidatedInput.Props(
            if (isTransfer) changeTransferKeyName else changeConnectionKeyName,
            inputClass = Some("test-connection-key-name"),
            label = Some(s"${if (isTransfer) "Transfer " else ""}Connection Key Name"),
            helpText = Some("""
                |The name of the key used to sign requests to the DNS server.
                | This is set when the zone is setup in the DNS server, and is used to
                | connect to the DNS server when performing updates and transfers.
              """.stripMargin),
            value = connection.map(_.keyName),
            validations = Some(Validations(required = true))
          ),
          ValidatedInput.Props(
            if (isTransfer) changeTransferKey else changeConnectionKey,
            inputClass = Some("test-connection-key"),
            label = Some(s"${if (isTransfer) "Transfer " else ""}Connection Key"),
            helpText = Some("The secret key used to sign requests sent to the DNS server."),
            value = connection.map(_.key),
            encoding = Encoding.Password,
            validations = Some(Validations(required = true))
          ),
          ValidatedInput.Props(
            if (isTransfer) changeTransferServer else changeConnectionServer,
            inputClass = Some("test-connection-server"),
            label = Some(s"${if (isTransfer) "Transfer " else ""}Connection Server"),
            helpText = Some(
              """
                | The IP Address or host name of the backing DNS server for zone transfers.
                | This host will be the target for syncing zones with Vinyl. If the port is not specified,
                | port 53 is assumed.
              """.stripMargin),
            value = connection.map(_.primaryServer),
            validations = Some(Validations(required = true))
          )
        )
      } else List()

    def toggleCustomServer(): Callback =
      bs.modState { s =>
        val connection =
          if (s.customServer) None // turning off
          else Some(ZoneConnection()) // turning on

        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse].copy(connection = connection)
          s.copy(zone = zone, customServer = !s.customServer)
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo].copy(connection = connection)
          s.copy(zone = zone, customServer = !s.customServer)
        }
      }

    def toggleCustomTransfer(): Callback =
      bs.modState { s =>
        val transfer =
          if (s.customTransfer) None // turning off
          else Some(ZoneConnection()) // turning on

        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse].copy(transferConnection = transfer)
          s.copy(zone = zone, customTransfer = !s.customTransfer)
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo].copy(transferConnection = transfer)
          s.copy(zone = zone, customTransfer = !s.customTransfer)
        }
      }

    def toggleBackendId(): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(backendId = None), customBackendId = !s.customBackendId)
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(backendId = None), customBackendId = !s.customBackendId)
        }
      }

    def toggleShared(): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse].copy(shared = !s.zone.shared)
          s.copy(zone = zone)
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo].copy(shared = !s.zone.shared)
          s.copy(zone = zone)
        }
      }

    def createZone(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to connect to zone ${S.zone.name}?",
        Callback
          .lazily {
            val zone = S.zone.asInstanceOf[ZoneCreateInfo]
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(
                P.http.toNotification(s"connecting to zone ${S.zone.name}", httpResponse))
            }
            val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
              addNotification(
                P.http.toNotification(s"connecting to zone ${S.zone.name}", httpResponse)) >>
                P.close(()) >>
                withDelay(HALF_SECOND_IN_MILLIS, P.refreshZones(()))
            }
            P.http.post(CreateZoneRoute, write(zone), onSuccess, onFailure)
          }
      )

    def updateZone(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to update zone ${S.zone.name}?",
        Callback
          .lazily {
            val zone = S.zone.asInstanceOf[ZoneResponse]
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(P.http.toNotification(s"updating zone ${S.zone.name}", httpResponse))
            }
            val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
              addNotification(P.http.toNotification(s"updating zone ${S.zone.name}", httpResponse)) >>
                P.close(()) >>
                withDelay(HALF_SECOND_IN_MILLIS, P.refreshZones(()))
            }
            P.http.put(UpdateZoneRoute(zone.id), write(zone), onSuccess, onFailure)
          }
      )

    def toAdminGroupDatalist(groupList: GroupListResponse): List[(String, String)] =
      groupList.groups.map(g => g.name -> s"${g.name} (id: ${g.id})")

    def changeBackendId(value: String): Callback =
      bs.modState { s =>
        val id = if (value.isEmpty) None else Some(value)
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(backendId = id))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(backendId = id))
        }
      }

    def changeName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(name = value))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(name = value))
        }
      }

    def changeEmail(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(email = value))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(email = value))
        }
      }

    def changeAdminGroup(value: String, groups: List[GroupResponse]): Callback =
      bs.modState { s =>
        val adminGroupId = groups.find(_.name == value.trim).map(_.id)

        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(
            zone =
              zone.copy(adminGroupId = adminGroupId.getOrElse(""), adminGroupName = Some(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(
            zone =
              zone.copy(adminGroupId = adminGroupId.getOrElse(""), adminGroupName = Some(value)))
        }
      }

    def changeConnectionKeyName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(connection = zone.newConnectionKeyName(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(connection = zone.newConnectionKeyName(value)))
        }
      }

    def changeConnectionKey(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(connection = zone.newConnectionKey(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(connection = zone.newConnectionKey(value)))
        }
      }

    def changeConnectionServer(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(connection = zone.newConnectionServer(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(connection = zone.newConnectionServer(value)))
        }
      }

    def changeTransferKeyName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferKeyName(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferKeyName(value)))
        }
      }

    def changeTransferKey(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferKey(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferKey(value)))
        }
      }

    def changeTransferServer(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val zone = s.zone.asInstanceOf[ZoneResponse]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferServer(value)))
        } else {
          val zone = s.zone.asInstanceOf[ZoneCreateInfo]
          s.copy(zone = zone.copy(transferConnection = zone.newTransferServer(value)))
        }
      }

    val createHeader: String =
      """
        |Use this form to connect to an already existing DNS zone. If you need a new zone created in DNS,
        | please follow guidelines set by your VinylDNS Administrators.
      """.stripMargin

    def toTitle(S: State): String =
      if (S.isUpdate) "Update Zone"
      else "Connect to a DNS Zone"

    def isUpdateDisabled(P: Props, S: State): Boolean =
      P.existing match {
        case Some(e) => e == S.zone.asInstanceOf[ZoneResponse]
        case None => false
      }
  }
}
