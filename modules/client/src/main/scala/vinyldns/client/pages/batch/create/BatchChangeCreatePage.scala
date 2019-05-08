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

package vinyldns.client.pages.batch.create

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.{CreateBatchChangeRoute, Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.batch.{
  BatchChangeCreateInfo,
  BatchChangeResponse,
  SingleChangeCreateInfo
}
import vinyldns.client.models.record.RecordData
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import vinyldns.client.components.AlertBox.addNotification
import upickle.default._
import vinyldns.client.models.membership.{GroupListResponse, GroupResponse}
import vinyldns.client.router.{Page, ToBatchChangeListPage, ToBatchChangeViewPage}

import scala.annotation.tailrec
import scala.util.Try

object BatchChangeCreatePage extends PropsFromAppRouter {
  case class State(createInfo: BatchChangeCreateInfo, groupList: Option[GroupListResponse] = None)

  val component = ScalaComponent
    .builder[Props]("BatchCreatePage")
    .initialState(State(BatchChangeCreateInfo()))
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups(e.props))
    .build

  def apply(page: Page, routerCtl: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, routerCtl, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      S.groupList match {
        case Some(gl) =>
          <.div(
            GlobalStyle.Styles.height100,
            ^.className := "right_col",
            ^.role := "main",
            <.div(
              ^.className := "page-title",
              <.div(
                ^.className := "title_left",
                <.h3(<.span(^.className := "fa fa-list"), "  Create a Batch Change"))),
            <.div(^.className := "clearfix"),
            <.div(
              ^.className := "page-content-wrap",
              <.div(
                ^.className := "row",
                <.div(
                  ^.className := "col-md-12 col-sm-12 col-xs-12",
                  <.form(
                    ^.className := "test-create-form",
                    ^.onSubmit ==> { e: ReactEventFromInput =>
                      e.preventDefaultCB >>
                        submitBatchChange(P, S)
                    },
                    <.div(
                      ^.className := "panel panel-default",
                      <.div(
                        ^.className := "panel-heading",
                        <.div(
                          <.div(
                            ^.className := "form-group col-md-12",
                            <.label(
                              ^.className := "h5",
                              "Description"
                            ),
                            <.input(
                              ^.className := "form-control test-comments",
                              ^.onChange ==> { e: ReactEventFromInput =>
                                changeComments(e.target.value)
                              },
                              ^.value := s"${S.createInfo.comments.getOrElse("")}"
                            )
                          ),
                          ownerGroupField(S, gl.groups),
                          <.div(
                            ^.className := "form-group col-md-12",
                            <.p(
                              """
                                |Zones loaded into VinylDNS are either set to Shared or Private (default).
                                |In Private Zones, Owner Group is ignored and does not have an impact.
                                |In Shared Zones, all records will be created with an Owner Group, and further updates
                                |to those records must be made by someone in that Owner Group, or a Zone Admin.
                              """.stripMargin.replaceAll("\n", " ")
                            )
                          )
                        ),
                        <.div(^.className := "clearfix")
                      ),
                      <.div(
                        ^.className := "panel-body",
                        <.h4("Changes"),
                        <.table(
                          ^.className := "table",
                          toTableHeader(),
                          <.tbody(
                            S.createInfo.changes.zipWithIndex.map {
                              case (change, index) =>
                                singleChangeRow(change, index)
                            }.toTagMod,
                            <.tr( // last row with add button
                              <.td,
                              <.td,
                              <.td,
                              <.td,
                              <.td,
                              <.td,
                              <.td,
                              <.td(
                                <.button(
                                  ^.className := "btn btn-info test-add-button",
                                  ^.`type` := "button",
                                  ^.onClick --> addRow(),
                                  "Add Row"
                                )
                              )
                            )
                          )
                        )
                      ),
                      <.div(
                        ^.className := "panel-footer clearfix",
                        <.button(
                          ^.className := "btn btn-primary pull-right test-submit-button",
                          ^.`type` := "submit",
                          ^.disabled := !isValidOwnerGroup(S),
                          "Submit"
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        case None => <.p("Loading...")
      }

    def ownerGroupField(S: State, groups: List[GroupResponse]): TagMod = {
      val inputClass =
        if (isValidOwnerGroup(S)) "form-control test-owner-group-name"
        else "form-control parsley-error test-owner-group-name"
      <.div(
        ^.className := "form-group col-md-4",
        <.label(
          ^.className := "h5",
          "Owner Group Name"
        ),
        <.input(
          GlobalStyle.Styles.cursorPointer,
          ^.className := inputClass,
          ^.onChange ==> { e: ReactEventFromInput =>
            changeOwnerGroup(e.target.value, groups)
          },
          ^.placeholder := "Search for a group you are in",
          ^.list := "ownerGroupName",
          ^.value := s"${S.createInfo.ownerGroupName.getOrElse("")}"
        ),
        <.datalist(
          ^.id := "ownerGroupName",
          groups.map { g =>
            <.option(^.key := g.name, ^.value := g.name, s"${g.name} (id: ${g.id})")
          }.toTagMod
        ),
        (S.createInfo.ownerGroupName, S.createInfo.ownerGroupId) match {
          case (Some(_), Some(id)) =>
            <.div(
              ^.className := "help-block",
              s"Group ID: '$id'"
            )
          case (Some(name), None) =>
            <.ul(
              ^.className := "parsley-errors-list filled",
              <.li(
                ^.className := "parley-required",
                s"You are not in a group named '$name'"
              )
            )
          case _ => TagMod.empty
        }
      )
    }

    def isValidOwnerGroup(S: State): Boolean =
      (S.createInfo.ownerGroupName, S.createInfo.ownerGroupId) match {
        case (None, None) => true
        case (Some(_), Some(_)) => true
        case _ => false
      }

    def toTableHeader(): TagMod =
      <.thead(
        <.tr(
          <.th("#"),
          <.th("Change Type"),
          <.th("Record Type"),
          <.th(
            GlobalStyle.Styles.keepWhitespace,
            ^.className := "col-md-2",
            "Input Name ",
            <.span(
              VdomAttr("data-toggle") := "tooltip",
              ^.title := "Fully Qualified Domain Name (FQDN) or IP Address, depending on Record Type",
              ^.className := "fa fa-info-circle"
            )
          ),
          <.th(
            ^.className := "col-md-1",
            GlobalStyle.Styles.keepWhitespace,
            "TTL ",
            <.span(
              VdomAttr("data-toggle") := "tooltip",
              ^.title := "Time To Live",
              ^.className := "fa fa-info-circle"
            )
          ),
          <.th(^.className := "col-md-2", "Record Data"),
          <.th(^.className := "col-md-2"),
          <.th
        )
      )

    def addRow(): Callback =
      bs.modState { s =>
        val withNewRow = s.createInfo.changes ::: List(SingleChangeCreateInfo())
        val createInfoWithNewRow = s.createInfo.copy(changes = withNewRow)
        s.copy(createInfo = createInfoWithNewRow)
      }

    def singleChangeRow(change: SingleChangeCreateInfo, index: Int): TagMod =
      <.tr(
        ^.className := toSingleChangeRowClass(change),
        <.td(
          index + 1
        ),
        <.td(
          <.select(
            ^.className := "form-control test-change-type",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleChangeType(e.target.value, change, index)
            },
            ^.value := change.changeType,
            List("Add", "DeleteRecordSet").map(o => <.option(^.value := o, o)).toTagMod
          )
        ),
        <.td(
          <.select(
            ^.className := "form-control test-record-type",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleRecordType(e.target.value, change, index)
            },
            ^.value := change.`type`,
            List(
              "A+PTR", // +PTR will be converted before posting the batch change
              "AAAA+PTR", // +PTR will be converted before posting the batch change
              "A",
              "AAAA",
              "CNAME",
              "PTR",
              "TXT",
              "MX"
            ).map(o => <.option(^.value := o, o)).toTagMod
          )
        ),
        <.td(
          <.input(
            ^.className := "form-control test-input-name",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleInputName(e.target.value, change, index)
            },
            ^.value := change.inputName
          )
        ),
        <.td(
          if (change.changeType == "Add")
            <.input(
              ^.className := "form-control test-ttl",
              ^.required := true,
              ^.`type` := "Number",
              ^.onChange ==> { e: ReactEventFromInput =>
                changeSingleInputTTL(e.target.value, change, index)
              },
              ^.value := Try(change.ttl.get.toString).getOrElse("")
            )
          else TagMod.empty
        ),
        <.td(
          if (change.changeType == "Add" || change.`type` == "A+PTR" || change.`type` == "AAAA+PTR")
            if (change.`type` != "MX")
              <.input(
                ^.className := "form-control test-record-data",
                ^.required := true,
                ^.onChange ==> { e: ReactEventFromInput =>
                  changeSingleInputRecordData(e.target.value, change, index)
                },
                ^.value := toRecordDataDisplay(change)
              )
            else toMxInput(change, index)
          else TagMod.empty
        ),
        <.td(
          change.errors match {
            case Some(es) => es.map(e => <.p(e)).toTagMod
            case None => TagMod.empty
          }
        ),
        <.td(
          <.button(
            ^.className := "btn btn-danger test-delete-button",
            ^.`type` := "button",
            "Delete",
            ^.onClick --> bs.modState { s =>
              val singleChanges = s.createInfo.changes.patch(index, Nil, 1)
              val createInfo = s.createInfo.copy(changes = singleChanges)
              s.copy(createInfo = createInfo)
            }
          )
        )
      )

    def changeSingleChangeType(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(changeType = value)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleRecordType(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(`type` = value, record = Some(RecordData()))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputName(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(inputName = value)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputTTL(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(ttl = Some(Try(value.toInt).getOrElse(0)))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputRecordData(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        // MX is handled separately since it has multiple parts
        val recordData =
          change.`type` match {
            case address
                if address == "A" || address == "A+PTR" || address == "AAAA" || address == "AAAA+PTR" =>
              RecordData(address = Some(value))
            case cname if cname == "CNAME" => RecordData(cname = Some(value))
            case ptrdname if ptrdname == "PTR" => RecordData(ptrdname = Some(value))
            case text if text == "TXT" => RecordData(text = Some(value))
            case _ => RecordData()
          }
        val newSingleChange = change.copy(record = Some(recordData))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeMxPreference(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val preference = Some(Try(value.toInt).getOrElse(0))
        val recordData = change.record match {
          case Some(r) => r.copy(preference = preference)
          case None => RecordData(preference = preference)
        }
        val newSingleChange = change.copy(record = Some(recordData))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeMxExchange(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val exchange = Some(value)
        val recordData = change.record match {
          case Some(r) => r.copy(exchange = exchange)
          case None => RecordData(exchange = exchange)
        }
        val newSingleChange = change.copy(record = Some(recordData))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeComments(value: String): Callback =
      bs.modState { s =>
        val comments =
          if (value.isEmpty) None
          else Some(value)

        s.copy(createInfo = s.createInfo.copy(comments = comments))
      }

    def changeOwnerGroup(value: String, groups: List[GroupResponse]): Callback =
      bs.modState { s =>
        val ownerGroupName =
          if (value.isEmpty) None
          else Some(value)

        val ownerGroupId = groups.find(_.name == value.trim).map(_.id)

        s.copy(
          createInfo =
            s.createInfo.copy(ownerGroupName = ownerGroupName, ownerGroupId = ownerGroupId))
      }

    def toRecordDataDisplay(change: SingleChangeCreateInfo): String = {
      val recordData = change.record match {
        case Some(r) => r
        case None => RecordData()
      }
      change.`type` match {
        case address
            if address == "A" || address == "A+PTR" || address == "AAAA" || address == "AAAA+PTR" =>
          recordData.addressToString
        case cname if cname == "CNAME" => recordData.cnameToString
        case ptrdname if ptrdname == "PTR" => recordData.ptrdnameToString
        case text if text == "TXT" => recordData.textToString
      }
    }

    def toMxInput(change: SingleChangeCreateInfo, index: Int): TagMod = {
      val recordData = change.record match {
        case Some(r) => r
        case None => RecordData()
      }

      <.div(
        <.label(
          ^.className := "batch-label",
          "Preference"
        ),
        <.input(
          ^.className := "form-control test-preference",
          ^.required := true,
          ^.`type` := "Number",
          ^.onChange ==> { e: ReactEventFromInput =>
            changeMxPreference(e.target.value, change, index)
          },
          ^.value := recordData.preferenceToString
        ),
        <.br,
        <.label(
          ^.className := "batch-label",
          "Exchange"
        ),
        <.input(
          ^.className := "form-control test-exchange",
          ^.required := true,
          ^.onChange ==> { e: ReactEventFromInput =>
            changeMxExchange(e.target.value, change, index)
          },
          ^.value := recordData.exchangeToString
        )
      )
    }

    def toSingleChangeRowClass(change: SingleChangeCreateInfo): String =
      change.errors match {
        case Some(errors) =>
          if (errors.isEmpty) "test-single-change-row" else "test-single-change-row changeError"
        case None => "test-single-change-row"
      }

    @tailrec
    final def jointPtrConversion(createInfo: BatchChangeCreateInfo): BatchChangeCreateInfo = {
      val nextJointIndex =
        createInfo.changes.indexWhere(c => c.`type` == "A+PTR" || c.`type` == "AAAA+PTR")
      if (nextJointIndex == -1) createInfo
      else {
        val jointChange = createInfo.changes(nextJointIndex)
        val addressChange =
          if (jointChange.`type` == "A+PTR") jointChange.copy(`type` = "A")
          else jointChange.copy(`type` = "AAAA")
        val ptrChange = jointChange.copy(
          `type` = "PTR",
          inputName = Try(jointChange.record.get.addressToString).getOrElse(""),
          record = Some(RecordData(ptrdname = Some(jointChange.inputName)))
        )

        val patchedChanges =
          createInfo.changes.patch(nextJointIndex, List(addressChange, ptrChange), 1)
        jointPtrConversion(createInfo.copy(changes = patchedChanges))
      }
    }

    def submitBatchChange(P: Props, S: State): Callback =
      P.http.withConfirmation(
        "Are you sure you want to make this DNS Record Request?",
        Callback.lazily {
          val onSuccess = {
            (httpResponse: HttpResponse, _: Option[BatchChangeCreateInfo]) =>
              val id = Try(read[BatchChangeResponse](httpResponse.responseText).id).toOption
              val redirect = id match {
                case Some(batchId) => P.router.set(ToBatchChangeViewPage(batchId))
                case None => P.router.set(ToBatchChangeListPage)
              }
              addNotification(
                P.http.toNotification("creating batch request", httpResponse)
              ) >> redirect
          }
          val onFailure = {
            httpResponse: HttpResponse =>
              val withErrors = CreateBatchChangeRoute.parse(httpResponse) match {
                case Some(batchChangeErrors) =>
                  S.createInfo.copy(changes = batchChangeErrors.changes)
                case None => S.createInfo
              }

              addNotification(P.http.toNotification(s"creating batch request", httpResponse)) >>
                bs.modState(_.copy(createInfo = withErrors))
          }
          val convertedJointPtr = jointPtrConversion(S.createInfo)
          P.http.post(CreateBatchChangeRoute, write(convertedJointPtr), onSuccess, onFailure)
        }
      )

    def listGroups(P: Props): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupListResponse]) =>
        bs.modState(_.copy(groupList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("listing groups", httpResponse, onlyOnError = true))
      }
      P.http.get(ListGroupsRoute(1000), onSuccess, onFailure)
    }

  }
}
