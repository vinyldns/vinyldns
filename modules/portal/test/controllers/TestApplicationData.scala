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

package controllers
import akka.io.dns.RecordType
import cats.effect.IO
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Environment}
import play.api.libs.json.{JsObject, JsValue, Json}
import vinyldns.core.crypto.{CryptoAlgebra, NoOpCrypto}
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record._
import vinyldns.core.health.HealthService

import scala.util.Success

trait TestApplicationData { this: Mockito =>
  val frodoDetails = LdapUserDetails(
    "CN=frodo,OU=hobbits,DC=middle,DC=earth",
    "frodo",
    Some("fbaggins@hobbitmail.me"),
    Some("Frodo"),
    Some("Baggins"))

  val frodoUser = User(
    "fbaggins",
    "key",
    "secret",
    Some("Frodo"),
    Some("Baggins"),
    Some("fbaggins@hobbitmail.me"),
    DateTime.now,
    "frodo-uuid")

  val lockedFrodoUser = User(
    "lockedFbaggins",
    "lockedKey",
    "lockedSecret",
    Some("LockedFrodo"),
    Some("LockedBaggins"),
    Some("lockedfbaggins@hobbitmail.me"),
    DateTime.now,
    "locked-frodo-uuid",
    false,
    LockStatus.Locked
  )

  val superFrodoUser = User(
    "superBaggins",
    "superKey",
    "superSecret",
    Some("SuperFrodo"),
    Some("SuperBaggins"),
    Some("superfbaggins@hobbitmail.me"),
    DateTime.now,
    "super-frodo-uuid",
    true,
    LockStatus.Unlocked
  )

  val newFrodoLog = UserChange(
    "frodo-uuid",
    frodoUser,
    "fbaggins",
    DateTime.now,
    None,
    UserChangeType.Create
  ).toOption.get

  val serviceAccountDetails =
    LdapUserDetails("CN=frodo,OU=hobbits,DC=middle,DC=earth", "service", None, None, None)
  val serviceAccount =
    User("service", "key", "secret", None, None, None, DateTime.now, "service-uuid")

  val frodoJsonString: String =
    s"""{
       |  "userName":  "${frodoUser.userName}",
       |  "firstName": "${frodoUser.firstName}",
       |  "lastName":  "${frodoUser.lastName}",
       |  "email":     "${frodoUser.email}",
       |  "created":   "${frodoUser.created}",
       |  "id":        "${frodoUser.id}"
       |}
     """.stripMargin

  val samAccount = User(
    "sgamgee",
    "key",
    "secret",
    Some("Samwise"),
    Some("Gamgee"),
    Some("sgamgee@hobbitmail.me"),
    DateTime.now,
    "sam-uuid")
  val samDetails = LdapUserDetails(
    "CN=sam,OU=hobbits,DC=middle,DC=earth",
    "sam",
    Some("sgamgee@hobbitmail.me"),
    Some("Sam"),
    Some("Gamgee"))

  val frodoJson: String =
    s"""{
       |"name": "${frodoUser.userName}"
       |}
     """.stripMargin

  val userJson: JsValue = Json.parse(s"""{
      |  "userName":  "${frodoUser.userName}",
      |  "firstName": "${frodoUser.firstName}",
      |  "lastName":  "${frodoUser.lastName}",
      |  "email":     "${frodoUser.email}",
      |  "created":   "${frodoUser.created}",
      |  "id":        "${frodoUser.id}"
      |}
     """.stripMargin)

  val hobbitGroupId = "uuid-12345-abcdef"
  val hobbitGroup: JsValue = Json.parse(s"""{
       | "id":          "$hobbitGroupId",
       | "name":        "hobbits",
       | "email":       "hobbitAdmin@shire.me",
       | "description": "Hobbits of the shire",
       | "members":     [ { "id": "${frodoUser.id}" },  { "id": "samwise-userId" } ],
       | "admins":      [ { "id": "${frodoUser.id}" } ]
       | }
    """.stripMargin)

  val ringbearerGroup: JsValue = Json.parse(
    s"""{
       |  "id":          "ringbearer-group-uuid",
       |  "name":        "ringbearers",
       |  "email":       "future-minions@mordor.me",
       |  "description": "Corruptable folk of middle-earth",
       |  "members":     [ { "id": "${frodoUser.id}" },  { "id": "sauron-userId" } ],
       |  "admins":      [ { "id": "sauron-userId" } ]
       |  }
     """.stripMargin
  )
  val hobbitGroupRequest: JsValue = Json.parse(s"""{
      | "name":        "hobbits",
      | "email":       "hobbitAdmin@shire.me",
      | "description": "Hobbits of the shire",
      | "members":     [ { "id": "${frodoUser.id}" },  { "id": "samwise-userId" } ],
      | "admins":      [ { "id": "${frodoUser.id}" } ]
      | }
    """.stripMargin)

  val invalidHobbitGroup: JsValue = Json.parse(s"""{
      | "name":        "hobbits",
      | "email":       "hobbitAdmin@shire.me",
      | "description": "Hobbits of the shire",
      | "members":     [ { "id": "${frodoUser.id}" },  { "id": "merlin-userId" } ],
      | "admins":      [ { "id": "${frodoUser.id}" } ]
      | }
    """.stripMargin)

  val hobbitGroupMembers: JsValue = Json.parse(
    s"""{
       | "members": [ $frodoJsonString ],
       | "maxItems": 100
       |}
     """.stripMargin
  )

  val hobbitZoneId = "uuid-abcdef-12345"
  val hobbitZoneName = "hobbits"
  val hobbitZone: JsValue = Json.parse(s"""{
      | "id":             "$hobbitZoneId",
      | "name":           "$hobbitZoneName",
      | "email":          "hobbitAdmin@shire.me",
      | "status":         "Active",
      | "account":        "system",
      | "shared":         false,
      | "adminGroupName": "hobbits",
      | "adminGroupId":   "$hobbitGroupId"
      | }
    """.stripMargin)

  val hobbitZoneRequest: JsValue = Json.parse(s"""{
      | "name":           "hobbits",
      | "email":          "hobbitAdmin@shire.me",
      | "status":         "Active",
      | "account":        "system",
      | "shared":         false,
      | "adminGroupName": "hobbits",
      | "adminGroupId":   "$hobbitGroupId"
      | }
    """.stripMargin)

  val hobbitRecordSetId = "uuid-record-12345"
  val hobbitRecordSet: JsValue = Json.parse(s"""{
      | "zoneId":        "$hobbitZoneId",
      | "name":          "ok",
      | "typ":           "${RecordType.A}",
      | "ttl":           "200",
      | "status":        "${RecordSetStatus.Active}",
      | "records":       [ { "address": "10.1.1.1" } ],
      | "id":            "$hobbitRecordSetId"
      | }
    """.stripMargin)

  val hobbitBatchChange: JsValue = Json.parse(s"""{
      | "userId":               "vinyl",
      | "userName":             "vinyl201",
      | "comments":             "this is optional",
      | "createdTimestamp":     "2018-05-08T18:46:34Z",
      | "ownerGroupId":         "f42385e4-5675-38c0-b42f-64105e743bfe",
      | "changes":              [],
      | "status":               "Complete",
      | "id":                   "937191c4-b1fd-4ab5-abb4-9553a65b44ab",
      | "approvalStatus":       "AutoApproved"
      | }
    """.stripMargin)

  val groupList: JsObject = Json.obj("groups" -> Json.arr(hobbitGroup))
  val emptyGroupList: JsObject = Json.obj("groups" -> Json.arr())

  val frodoGroupList: JsObject = Json.obj("groups" -> Json.arr(hobbitGroup, ringbearerGroup))

  val simulatedBackendPort: Int = 9001

  val testConfigLdap: Configuration =
    Configuration.load(Environment.simple()) ++ Configuration.from(
      Map(
        "portal.vinyldns.backend.url" -> s"http://localhost:$simulatedBackendPort",
        "oidc.enabled" -> false)
    )

  val mockAuth: Authenticator = mock[Authenticator]
  val mockUserRepo: UserRepository = mock[UserRepository]
  val mockUserChangeRepo: UserChangeRepository = mock[UserChangeRepository]

  mockAuth.authenticate("frodo", "secondbreakfast").returns(Right(frodoDetails))
  mockUserRepo.getUser(anyString).returns(IO.pure(Some(frodoUser)))
  mockUserChangeRepo.save(any[UserChange]).returns(IO.pure(newFrodoLog))

  def app: Application =
    GuiceApplicationBuilder()
      .disable[modules.VinylDNSModule]
      .bindings(
        bind[Authenticator].to(mockAuth),
        bind[UserRepository].to(mockUserRepo),
        bind[UserChangeRepository].to(mockUserChangeRepo),
        bind[CryptoAlgebra].to(new NoOpCrypto()),
        bind[HealthService].to(new HealthService(List()))
      )
      .configure(testConfigLdap)
      .build()
}
