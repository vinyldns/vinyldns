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

package vinyldns.client.pages.zoneview.components

import vinyldns.client.http.{CreateRecordSetRoute, Http, HttpResponse}
import vinyldns.client.models.record._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import upickle.default.write
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.components.JsNative._
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.Modal
import vinyldns.client.models.zone.Zone
import vinyldns.client.components.form._
import vinyldns.client.pages.zoneview.components.RecordDataInput._

import scala.util.Try

object RecordSetModal {
  case class State(recordSet: BasicRecordSetInfo, isUpdate: Boolean = false)
  case class Props(
      http: Http,
      zone: Zone,
      close: Unit => Callback,
      refreshRecords: Unit => Callback,
      existing: Option[RecordSet] = None)

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
        Modal.Props(toTitle(S), P.close),
        <.div(
          ^.className := "modal-body",
          <.div(
            ^.className := "panel-header",
            <.p(header)
          ),
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-record-form",
              generateInputFieldProps(S),
              _ => createRecordSet(P, S)
            ),
            <.div(
              generateCustomRecordDataInput(S),
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
                  ^.className := "btn btn-default pull-right test-close-create-group",
                  ^.onClick --> P.close(()),
                  "Close"
                )
              )
            )
          )
        )
      )

    def generateInputFieldProps(S: State): List[ValidatedInput.Props] =
      List(
        ValidatedInput.Props(
          changeType,
          inputClass = Some("test-type"),
          label = Some("Type"),
          options = List(
            "A" -> "A",
            "AAAA" -> "AAAA",
            "CNAME" -> "CNAME",
            "DS" -> "DS",
            "PTR" -> "PTR",
            "MX" -> "MX",
            "NS" -> "NS",
            "SRV" -> "SRV",
            "TXT" -> "TXT",
            "SSHFP" -> "SSHFP",
            "SPF" -> "SPF"
          ),
          inputType = InputType.Select,
          value = Some(S.recordSet.`type`),
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
        )
      ) ::: generateRecordDataInputFieldProps(S)

    def generateRecordDataInputFieldProps(S: State): List[ValidatedInput.Props] =
      // used when record inputs are just a normal field and not complex like mx or sshfp with multiple parts
      S.recordSet.`type` match {
        case aOrAaaa if aOrAaaa == "A" || aOrAaaa == "AAAA" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changeAddress(bs, value),
              inputClass = Some("test-address"),
              label = Some("IP addresses"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = Some(S.recordSet.records.flatMap(_.address).mkString("\n")),
              validations = Some(Validations(required = true))
            ))
        case cname if cname == "CNAME" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changeCname(bs, value),
              inputClass = Some("test-cname"),
              label = Some("CNAME Target"),
              value = Try(S.recordSet.records.head.cname).getOrElse(None),
              helpText = Some("Fully Qualified Domain Name"),
              validations = Some(Validations(required = true))
            ))
        case ptr if ptr == "PTR" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changePtrDName(bs, value),
              inputClass = Some("test-ptr"),
              label = Some("Fully Qualified Domain Names"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = Some(S.recordSet.records.flatMap(_.ptrdname).mkString("\n")),
              validations = Some(Validations(required = true))
            ))
        case ns if ns == "NS" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changeNsDName(bs, value),
              inputClass = Some("test-ns"),
              label = Some("Fully Qualified Domain Names"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = Some(S.recordSet.records.flatMap(_.nsdname).mkString("\n")),
              validations = Some(Validations(required = true))
            ))
        case spf if spf == "SPF" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changeText(bs, value),
              inputClass = Some("test-spf"),
              label = Some("Host Names/IP Addresses"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = Some(S.recordSet.records.flatMap(_.text).mkString("\n")),
              validations = Some(Validations(required = true))
            ))
        case txt if txt == "TXT" =>
          List(
            ValidatedInput.Props(
              (value: String) => RecordDataInput.RecordDataInput.changeText(bs, value),
              inputClass = Some("test-txt"),
              label = Some("Text Records"),
              helpText = Some("one per line"),
              inputType = InputType.TextArea,
              value = Some(S.recordSet.records.flatMap(_.text).mkString("\n")),
              validations = Some(Validations(required = true))
            ))
        case _ => List()
      }

    def generateCustomRecordDataInput(S: State): TagMod =
      // for more complex inputs that can't just use a simple text field
      S.recordSet.`type` match {
        case mx if mx == "MX" => MxInput.toTagMod(S, bs)
        case srv if srv == "SRV" => SrvInput.toTagMod(S, bs)
        case sshfp if sshfp == "SSHFP" => SshfpInput.toTagMod(S, bs)
        case ds if ds == "DS" => DSInput.toTagMod(S, bs)
        case _ => TagMod.empty
      }

    def changeType(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSet]
          val modified = record.copy(`type` = value, records = List(RecordData()))
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified = record.copy(`type` = value, records = List(RecordData()))
          s.copy(recordSet = modified)
        }
      }

    def changeName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSet]
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
        val ttl = if (value.isEmpty) 0 else value.toInt
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSet]
          val modified = record.copy(ttl = ttl)
          s.copy(recordSet = modified)
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          val modified = record.copy(ttl = ttl)
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
          val onSuccess = { (httpResponse: HttpResponse, _: Option[RecordSet]) =>
            addNotification(P.http.toNotification("creating record", httpResponse)) >>
              P.close(()) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshRecords(()))
          }
          P.http.post(
            CreateRecordSetRoute(P.zone.id),
            write[RecordSetCreateInfo](record),
            onSuccess,
            onFailure)
        }
      )

    def toTitle(S: State): String =
      if (S.isUpdate) s"Update Group ${S.recordSet.asInstanceOf[RecordSet].id}"
      else "Create RecordSet"

    private val header: String =
      s"""
        |Record set names are combined with the zone name to create a
        | Fully Qualified Domain Name (FQDN). For example, a record with the name
        | "www" in the zone "vinyldns.io." will read "www.vinyldns.io.". Use the symbol "@"
        | to create an apex record that has a FQDN equal to the zone name.
      """.stripMargin
  }
}
