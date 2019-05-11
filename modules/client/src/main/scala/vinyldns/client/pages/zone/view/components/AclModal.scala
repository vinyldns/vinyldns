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

package vinyldns.client.pages.zone.view.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import upickle.default.write
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.components.JsNative._
import vinyldns.client.components.{AlertBox, Modal}
import vinyldns.client.components.form._
import vinyldns.client.http._
import vinyldns.client.models.membership.{GroupResponse, UserResponse}
import vinyldns.client.models.zone.ACLRule.AclType
import vinyldns.client.models.zone.{ACLRule, Rules, ZoneResponse}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.AccessLevel

object AclModal {
  case class Props(
      zone: ZoneResponse,
      http: Http,
      groups: List[GroupResponse],
      close: Unit => Callback,
      refreshZone: Unit => Callback,
      existing: Option[(ACLRule, Int)] = None
  )

  case class State(
      rule: ACLRule,
      updateIndex: Int = -1,
      aclType: AclType.AclType,
      isUpdate: Boolean = false)

  val component = ScalaComponent
    .builder[Props]("AclModal")
    .initialStateFromProps { p =>
      p.existing match {
        case Some((r, i)) =>
          val aclType =
            if (r.userId.isDefined) AclType.User
            else AclType.Group
          State(r, i, aclType, isUpdate = true)
        case None => State(ACLRule.apply(), aclType = AclType.User)
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
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-acl-form",
              generateInputFieldProps(P, S),
              _ =>
                S.aclType match {
                  case AclType.User => lookupUser(P, S)
                  case _ => if (S.isUpdate) updateAclRule(P, S) else createAclRule(P, S)
              }
            ),
            <.div(
              recordTypesField(S),
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
                  ^.className := "btn btn-default pull-right test-close-acl",
                  ^.onClick --> P.close(()),
                  "Close"
                )
              )
            )
          )
        )
      )

    def recordTypesField(S: State): TagMod =
      <.div(
        ^.className := "form-group",
        <.label(
          ^.className := s"control-label col-md-3 col-sm-3 col-xs-12",
          "Record Types"
        ),
        <.div(
          ^.className := "form-group col-md-6 col-sm-6 col-xs-12",
          <.select(
            ^.className := "form-control test-recordtypes",
            ^.multiple := true,
            ^.onChange ==> ((e: ReactEventFromInput) => changeRecordTypes(e.target.value)),
            List(
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
              RecordType.SOA.toString -> RecordType.SOA.toString
            ).map {
              case (value, display) =>
                <.option(
                  ^.key := display,
                  ^.value := value,
                  ^.selected := S.rule.recordTypes.contains(RecordType.withName(value)),
                  display
                )
            }.toTagMod
          ),
          <.div(
            ^.className := "help-block",
            s"""
             |This rule will apply only to the selected record types. If no types are selected then the rule will
             | apply to all record types.
                     """.stripMargin
          ),
          <.div(
            ^.className := "help-block",
            if (S.rule.recordTypes.isEmpty) "Selected: All"
            else S.rule.recordTypes.foldLeft("Selected:") { case (acc, t) => s"$acc $t" }
          ),
          <.button(
            ^.`type` := "button",
            ^.className := "btn btn-sm btn-danger btn-round test-clear",
            ^.onClick --> bs.modState(s => s.copy(rule = s.rule.copy(recordTypes = Seq()))),
            "Clear Types"
          )
        )
      )

    def generateInputFieldProps(P: Props, S: State): List[ValidatedInput.Props] = {
      val selectApplyTo = ValidatedInput.Props(
        changeAclType,
        inputClass = Some("test-type"),
        label = Some("Apply rule to"),
        options = List(
          AclType.User.toString -> AclType.User.toString,
          AclType.Group.toString -> AclType.Group.toString
        ),
        inputType = InputType.Select,
        value = Some(S.aclType.toString),
        helpText = Some("User rules will have a higher priority than Group rules"),
        validations = Some(Validations(required = true))
      )

      val withTarget = S.aclType match {
        case AclType.User =>
          List(selectApplyTo) :+
            ValidatedInput.Props(
              changeUserName,
              inputClass = Some("test-username"),
              label = Some("Taget Username"),
              value = S.rule.userName,
              helpText = Some("The username of who the rule applies to"),
              validations = Some(Validations(required = true, noSpaces = true))
            )
        case AclType.Group =>
          List(selectApplyTo) :+
            ValidatedInput.Props(
              changeGroup(_, P.groups),
              value = S.rule.displayName,
              inputClass = Some("test-group-name"),
              label = Some("Target Group"),
              helpText = Some(s"""
                                 |The Vinyl Group that the rule applies to.
                                 | Search for a group you are in by name or Id
                              """.stripMargin),
              validations = Some(Validations(required = true, matchGroup = true)),
              options = P.groups.map(g => (g.name, s"${g.name} (id: ${g.id})")),
              inputType = InputType.Datalist
            )
      }

      val rest = withTarget :::
        List(
        ValidatedInput.Props(
          changeAccessLevel,
          inputClass = Some("test-accesslevel"),
          label = Some("AccessLevel"),
          options = List(
            AccessLevel.NoAccess.toString -> ACLRule.toAccessLevelDisplay(AccessLevel.NoAccess),
            AccessLevel.Read.toString -> ACLRule.toAccessLevelDisplay(AccessLevel.Read),
            AccessLevel.Write.toString -> ACLRule.toAccessLevelDisplay(AccessLevel.Write),
            AccessLevel.Delete.toString -> ACLRule.toAccessLevelDisplay(AccessLevel.Delete)
          ),
          inputType = InputType.Select,
          value = Some(S.rule.accessLevel.toString),
          validations = Some(Validations(required = true))
        ),
        ValidatedInput.Props(
          changeRecordMask,
          inputClass = Some("test-recordmask"),
          label = Some("Record Mask"),
          helpText = Some(s"""
              |Record masks is optional, and further refines the types of records this record applies to.
              | For non-PTR records, any valid regex will be accepted.
              | For PTR records, please input a CIDR rule.
            """.stripMargin),
          value = S.rule.recordMask
        ),
        ValidatedInput.Props(
          changeDescription,
          inputClass = Some("test-description"),
          label = Some("Description"),
          value = S.rule.description,
          inputType = InputType.TextArea
        )
      )

      rest
    }

    def changeAclType(value: String): Callback =
      bs.modState(
        s =>
          s.copy(
            rule = s.rule.copy(groupId = None, userId = None, userName = None),
            aclType = AclType.withName(value)))

    def changeUserName(value: String): Callback =
      bs.modState { s =>
        val userName =
          if (value.isEmpty) None
          else Some(value)
        s.copy(rule = s.rule.copy(userName = userName))
      }

    def changeGroup(name: String, groups: List[GroupResponse]): Callback =
      bs.modState { s =>
        val groupId = groups.find(_.name == name.trim).map(_.id)
        s.copy(rule = s.rule.copy(groupId = groupId, displayName = Some(name)))
      }

    def changeAccessLevel(value: String): Callback =
      bs.modState(s => s.copy(rule = s.rule.copy(accessLevel = AccessLevel.withName(value))))

    def changeRecordTypes(value: String): Callback =
      bs.modState { s =>
        val updatedTypes = s.rule.recordTypes.toSet + RecordType.withName(value)
        s.copy(rule = s.rule.copy(recordTypes = updatedTypes.toSeq))
      }

    def changeRecordMask(value: String): Callback =
      bs.modState { s =>
        val mask =
          if (value.isEmpty) None
          else Some(value)
        s.copy(rule = s.rule.copy(recordMask = mask))
      }

    def changeDescription(value: String): Callback =
      bs.modState { s =>
        val description =
          if (value.isEmpty) None
          else Some(value)
        s.copy(rule = s.rule.copy(description = description))
      }

    def createAclRule(P: Props, S: State): Callback =
      P.http.withConfirmation(
        "Are you sure you want to create this Zone Access Rule?",
        Callback.lazily {
          val updatedAclList = S.rule :: P.zone.acl.rules
          val updatedZone = P.zone.copy(acl = Rules(updatedAclList))

          val alertMessage = s"creating access rule ${S.rule.displayName.getOrElse("")}"
          val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
            addNotification(P.http.toNotification(alertMessage, httpResponse)) >>
              P.close(()) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshZone(()))
          }
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(alertMessage, httpResponse))
          }
          P.http.put(UpdateZoneRoute(P.zone.id), write(updatedZone), onSuccess, onFailure)
        }
      )

    def updateAclRule(P: Props, S: State): Callback =
      P.http.withConfirmation(
        "Are you sure you want to update this Zone Access Rule?",
        Callback.lazily {
          val updatedAclList = P.zone.acl.rules.patch(S.updateIndex, List(S.rule), 1)
          val updatedZone = P.zone.copy(acl = Rules(updatedAclList))

          val alertMessage = s"updating access rule ${S.rule.displayName.getOrElse("")}"
          val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
            addNotification(P.http.toNotification(alertMessage, httpResponse)) >>
              P.close(()) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshZone(()))
          }
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(alertMessage, httpResponse))
          }
          P.http.put(UpdateZoneRoute(P.zone.id), write(updatedZone), onSuccess, onFailure)
        }
      )

    def lookupUser(P: Props, S: State): Callback = {
      val username = S.rule.userName.getOrElse("")
      val onSuccess = { (_: HttpResponse, response: Option[UserResponse]) =>
        response match {
          case None =>
            val message = s"""could not find user "$username""""
            addNotification(Some(AlertBox.Notification(Some(message), isError = true)))
          case Some(user) =>
            bs.modState(
              { s =>
                s.copy(rule = s.rule.copy(userId = Some(user.id)))
              },
              bs.state >>= { newS =>
                if (S.isUpdate) updateAclRule(P, newS)
                else createAclRule(P, newS)
              }
            )
        }
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http
            .toNotification(s"""getting user ${S.rule.userName
              .getOrElse("")}""", httpResponse, onlyOnError = true)
        )
      }
      P.http.get(LookupUserRoute(S.rule.userName.getOrElse("")), onSuccess, onFailure)
    }

    def toTitle(S: State): String =
      if (S.isUpdate) "Update Access Rule"
      else "Create Access Rule"
  }
}
