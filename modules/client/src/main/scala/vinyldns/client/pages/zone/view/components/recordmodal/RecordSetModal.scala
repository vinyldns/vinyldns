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

package vinyldns.client.pages.zone.view.components.recordmodal

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import upickle.default.write
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.components.JsNative._
import vinyldns.client.components.Modal
import vinyldns.client.components.form._
import vinyldns.client.http.{CreateRecordSetRoute, Http, HttpResponse, UpdateRecordSetRoute}
import vinyldns.client.models.membership.{GroupListResponse, GroupResponse}
import vinyldns.client.models.record._
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.pages.zone.view.components.recordmodal.recordinput._
import vinyldns.core.domain.record.RecordType

import scala.util.Try

object RecordSetModal {
  case class State(recordSet: RecordSetModalInfo, isUpdate: Boolean = false)
  case class Props(
      http: Http,
      zone: ZoneResponse,
      groupList: GroupListResponse,
      close: Unit => Callback,
      refreshRecords: Unit => Callback,
      refreshChanges: Unit => Callback,
      existing: Option[RecordSetResponse] = None,
      readOnly: Boolean = false)

  val component = ScalaComponent
    .builder[Props]("RecordModal")
    .initialStateFromProps { p =>
      p.existing match {
        case Some(r) =>
          State(r, isUpdate = true)
        case None => State(RecordSetCreateInfo(p.zone.id))
      }
    }
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props(toTitle(P, S), P.close),
        <.div(
          ^.className := "modal-body",
          <.div(
            ^.className := "panel-header",
            <.p(header)
          ),
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-record-form",
              generateInputFieldProps(P, S),
              _ => if (S.isUpdate) updateRecordSet(P, S) else createRecordSet(P, S),
              readOnly = P.readOnly
            ),
            <.div(
              <.fieldset(
                ^.disabled := P.readOnly,
                generateCustomRecordDataInput(S)
              ),
              <.div(^.className := "ln_solid"),
              <.div(
                ^.className := "form-group",
                if (P.readOnly) <.span
                else
                  <.button(
                    ^.`type` := "submit",
                    ^.className := "btn btn-success pull-right",
                    ^.disabled := isSubmitDisabled(P, S),
                    "Submit"
                  ),
                <.button(
                  ^.`type` := "button",
                  ^.className := "btn btn-default pull-right test-close-recordset",
                  ^.onClick --> P.close(()),
                  "Close"
                )
              )
            )
          )
        )
      )

    def generateInputFieldProps(P: Props, S: State): List[ValidatedInput.Props] =
      List(
        ValidatedInput.Props(
          changeType,
          inputClass = Some("test-type"),
          label = Some("Type"),
          options = List(
            RecordType.A.toString -> RecordType.A.toString,
            RecordType.AAAA.toString -> RecordType.AAAA.toString,
            RecordType.CNAME.toString -> RecordType.CNAME.toString,
            RecordType.DS.toString -> RecordType.DS.toString,
            RecordType.PTR.toString -> RecordType.PTR.toString,
            RecordType.MX.toString -> RecordType.MX.toString,
            RecordType.NS.toString -> RecordType.NS.toString,
            RecordType.SRV.toString -> RecordType.SRV.toString,
            RecordType.TXT.toString -> RecordType.TXT.toString,
            RecordType.SSHFP.toString -> RecordType.SSHFP.toString,
            RecordType.SPF.toString -> RecordType.SPF.toString,
            RecordType.NAPTR.toString -> RecordType.NAPTR.toString
          ),
          inputType = InputType.Select,
          value = Some(S.recordSet.`type`.toString),
          validations = Some(Validations(required = true))
        ),
        ValidatedInput.Props(
          changeName,
          inputClass = Some("test-name"),
          label = Some("Name"),
          value = Some(S.recordSet.name),
          validations = Some(Validations(required = true, noSpaces = true))
        ),
        ValidatedInput.Props(
          changeTTL,
          inputClass = Some("test-ttl"),
          label = Some("Time To Live (seconds)"),
          value = Some(S.recordSet.ttl.toString),
          encoding = Encoding.Number,
          validations = Some(Validations(required = true))
        ),
        ValidatedInput.Props(
          changeOwnerGroup(_, P.groupList.groups),
          value = if (P.readOnly) S.recordSet.ownerGroupId else S.recordSet.ownerGroupName,
          inputClass = Some("test-owner-group"),
          label = Some("Record Owner Group"),
          helpText = Some(s"""
                             |If set and the Zone is SHARED, then this group will own the Record and other
                             | creates/updates must be made by a member of the Group. The Zone Admin Group will
                             | always have full access, and other Zone Access Rules will still apply.
              """.stripMargin),
          validations = if (P.readOnly) None else Some(Validations(matchGroup = true)),
          options = P.groupList.groups.map(g => g.name -> s"${g.name} (id: ${g.id})"),
          placeholder = Some("Search for a Group you are in by name or id"),
          inputType = InputType.Datalist
        )
      ) ::: generateRecordDataInputFieldProps(S)

    def generateRecordDataInputFieldProps(S: State): List[ValidatedInput.Props] =
      // used when record inputs are just a normal field and not complex like mx or sshfp with multiple parts
      S.recordSet.`type` match {
        case RecordType.A | RecordType.AAAA =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changeAddress(bs, value),
              inputClass = Some("test-address"),
              label = Some("IP addresses"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = RecordData.addressesToInput(S.recordSet.records),
              validations = Some(Validations(required = true, noEmptyLines = true))
            ))
        case RecordType.CNAME =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changeCname(bs, value),
              inputClass = Some("test-cname"),
              label = Some("CNAME Target"),
              value = RecordData.cnameToInput(S.recordSet.records),
              helpText = Some("Fully Qualified Domain Name"),
              validations = Some(Validations(required = true))
            ))
        case RecordType.PTR =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changePtrDName(bs, value),
              inputClass = Some("test-ptr"),
              label = Some("Fully Qualified Domain Names"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = RecordData.ptrdnamesToInput(S.recordSet.records),
              validations = Some(Validations(required = true, noEmptyLines = true))
            ))
        case RecordType.NS =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changeNsDName(bs, value),
              inputClass = Some("test-ns"),
              label = Some("Fully Qualified Domain Names"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = RecordData.nsdnamesToInput(S.recordSet.records),
              validations = Some(Validations(required = true, noEmptyLines = true))
            ))
        case RecordType.SPF =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changeText(bs, value),
              inputClass = Some("test-spf"),
              label = Some("Host Names/IP Addresses"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = RecordData.textsToInput(S.recordSet.records),
              validations = Some(Validations(required = true, noEmptyLines = true))
            ))
        case RecordType.TXT =>
          List(
            ValidatedInput.Props(
              (value: String) => recordinput.RecordDataInput.changeText(bs, value),
              inputClass = Some("test-txt"),
              label = Some("Text Records"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = RecordData.textsToInput(S.recordSet.records),
              validations = Some(Validations(required = true, noEmptyLines = true))
            ))
        case _ => List()
      }

    def generateCustomRecordDataInput(S: State): TagMod =
      // for more complex inputs that can't just use a simple text field
      S.recordSet.`type` match {
        case RecordType.MX => MxInput.toTagMod(S, bs)
        case RecordType.SRV => SrvInput.toTagMod(S, bs)
        case RecordType.SSHFP => SshfpInput.toTagMod(S, bs)
        case RecordType.DS => DsInput.toTagMod(S, bs)
        case RecordType.NAPTR => NaptrInput.toTagMod(S, bs)
        case _ => TagMod.empty
      }

    def changeType(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSetResponse]
          val modified =
            record.copy(`type` = RecordType.withName(value), records = List(RecordData()))
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified =
            record.copy(`type` = RecordType.withName(value), records = List(RecordData()))
          s.copy(recordSet = modified)
        }
      }

    def changeName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSetResponse]
          val modified = record.copy(name = value)
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified = record.copy(name = value)
          s.copy(recordSet = modified)
        }
      }

    def changeTTL(value: String): Callback =
      bs.modState { s =>
        val ttl = if (value.isEmpty) 0 else Try(value.toInt).getOrElse(0)
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSetResponse]
          val modified = record.copy(ttl = ttl)
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified = record.copy(ttl = ttl)
          s.copy(recordSet = modified)
        }
      }

    def changeOwnerGroup(name: String, groups: List[GroupResponse]): Callback =
      bs.modState { s =>
        val ownerGroupId = groups.find(_.name == name.trim).map(_.id)
        val ownerGroupName = if (name.isEmpty) None else Some(name)

        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSetResponse]
          val modified = record.copy(ownerGroupId = ownerGroupId, ownerGroupName = ownerGroupName)
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified = record.copy(ownerGroupId = ownerGroupId, ownerGroupName = ownerGroupName)
          s.copy(recordSet = modified)
        }
      }

    def createRecordSet(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to create record set ${S.recordSet.name}",
        Callback.lazily {
          val record = S.recordSet.asInstanceOf[RecordSetCreateInfo]
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification("creating record", httpResponse))
          }
          val onSuccess = { (httpResponse: HttpResponse, _: Option[RecordSetChangeResponse]) =>
            addNotification(P.http.toNotification("creating record", httpResponse)) >>
              P.close(()) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshRecords(()) >> P.refreshChanges(()))
          }
          P.http.post(
            CreateRecordSetRoute(P.zone.id),
            write[RecordSetCreateInfo](record),
            onSuccess,
            onFailure)
        }
      )

    def updateRecordSet(P: Props, S: State): Callback = {
      val record = S.recordSet.asInstanceOf[RecordSetResponse]
      P.http.withConfirmation(
        s"Are you sure you want to update record set ${record.id}",
        Callback.lazily {
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(s"updating record ${record.id}", httpResponse))
          }
          val onSuccess = { (httpResponse: HttpResponse, _: Option[RecordSetChangeResponse]) =>
            addNotification(P.http.toNotification(s"updating record ${record.id}", httpResponse)) >>
              P.close(()) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshRecords(()) >> P.refreshChanges(()))
          }
          P.http.put(
            UpdateRecordSetRoute(P.zone.id, record.id),
            write[RecordSetResponse](record),
            onSuccess,
            onFailure)
        }
      )
    }

    def isSubmitDisabled(P: Props, S: State): Boolean =
      P.existing match {
        case Some(e) => e == S.recordSet.asInstanceOf[RecordSetResponse]
        case None => false
      }

    def toTitle(P: Props, S: State): String =
      if (P.readOnly)
        s"""View Record Set ${Try(S.recordSet.asInstanceOf[RecordSetResponse].id).getOrElse("")}"""
      else if (S.isUpdate)
        s"""Update Record Set ${Try(S.recordSet.asInstanceOf[RecordSetResponse].id)
          .getOrElse("")}"""
      else "Create Record Set"

    private val header: String =
      s"""
        |Record set names are combined with the zone name to create a
        | Fully Qualified Domain Name (FQDN). For example, a record with the name
        | "www" in the zone "vinyldns.io." will read "www.vinyldns.io.". Use the symbol "@"
        | to create an apex record that has a FQDN equal to the zone name.
      """.stripMargin
  }
}
