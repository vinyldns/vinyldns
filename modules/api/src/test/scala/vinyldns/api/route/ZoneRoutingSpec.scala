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

package vinyldns.api.route

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.OneInstancePerTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.Interfaces._
import vinyldns.api.config.LimitsConfig
import vinyldns.api.domain.zone.{ZoneServiceAlgebra, _}
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.crypto.{JavaCrypto, NoOpCrypto}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone._

class ZoneRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with VinylDNSJsonProtocol
    with VinylDNSRouteTestHelper
    with OneInstancePerTest
    with Matchers {

  private val alreadyExists = Zone("already.exists.", "already-exists@test.com")
  private val notFound = Zone("not.found.", "not-found@test.com")
  private val notAuthorized = Zone("not.authorized.", "not-authorized@test.com")
  private val badAdminId = Zone("bad.admin.", "bad-admin@test.com")
  private val nonSuperUserSharedZone =
    Zone("non-super-user-shared-zone.", "non-super-user-shared-zone@test.com")

  private val userAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME)
  )
  private val userAclRuleInfo = ACLRuleInfo(
    AccessLevel.Read,
    Some("desc"),
    Some("johnny"),
    None,
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME)
  )
  private val groupAclRule = ACLRule(
    AccessLevel.Read,
    Some("desc"),
    None,
    Some("group"),
    Some("www-*"),
    Set(RecordType.A, RecordType.AAAA, RecordType.CNAME)
  )
  private val zoneAcl = ZoneACL(Set(userAclRule, groupAclRule))

  private val ok = Zone("ok.", "ok@test.com", acl = zoneAcl, adminGroupId = "test")
  private val aclAsInfo = ZoneACLInfo(zoneAcl.rules.map(ACLRuleInfo(_, Some("name"))))
  private val okAsZoneInfo = ZoneInfo(ok, aclAsInfo, okGroup.name, AccessLevel.Read)
  private val badRegex = Zone("ok.", "bad-regex@test.com", adminGroupId = "test")
  private val trailingDot = Zone("trailing.dot", "trailing-dot@test.com")
  private val connectionOk = Zone(
    "connection.ok.",
    "connection-ok@test.com",
    connection = Some(ZoneConnection("connection.ok", "keyName", "key", "10.1.1.1")),
    transferConnection = Some(ZoneConnection("connection.ok", "keyName", "key", "10.1.1.1"))
  )
  private val connectionFailed = Zone(
    "connection.fail",
    "connection-failed@test.com",
    connection = Some(ZoneConnection("connection.fail", "keyName", "key", "10.1.1.1"))
  )
  private val zoneValidationFailed = Zone(
    "validation.fail",
    "zone-validation-failed@test.com",
    connection = Some(ZoneConnection("validation.fail", "keyName", "key", "10.1.1.1"))
  )
  private val zone1 = Zone("zone1.", "zone1@test.com", ZoneStatus.Active)
  private val zoneSummaryInfo1 = ZoneSummaryInfo(zone1, okGroup.name, AccessLevel.NoAccess)
  private val zone2 = Zone("zone2.", "zone2@test.com", ZoneStatus.Active)
  private val zoneSummaryInfo2 = ZoneSummaryInfo(zone2, okGroup.name, AccessLevel.NoAccess)
  private val zone3 = Zone("zone3.", "zone3@test.com", ZoneStatus.Active)
  private val zoneSummaryInfo3 = ZoneSummaryInfo(zone3, okGroup.name, AccessLevel.NoAccess)
  private val zone4 =
    Zone("zone4.", "zone4@test.com", ZoneStatus.Active, adminGroupId = xyzGroup.id)
  private val zoneSummaryInfo4 = ZoneSummaryInfo(zone4, xyzGroup.name, AccessLevel.NoAccess)
  private val zone5 =
    Zone("zone5.", "zone5@test.com", ZoneStatus.Active, adminGroupId = xyzGroup.id)
  private val zoneSummaryInfo5 = ZoneSummaryInfo(zone5, xyzGroup.name, AccessLevel.NoAccess)
  private val error = Zone("error.", "error@test.com")

  private val missingFields: JValue =
    ("invalidField" -> "randomValue") ~~
      ("connection" -> ("k" -> "value"))

  private val zoneWithInvalidId: JValue =
    ("id" -> true) ~~
      ("name" -> "invalidZoneStatus.") ~~
      ("email" -> "invalid-zone-status@test.com") ~~
      ("status" -> "invalidStatus") ~~
      ("adminGroupId" -> "admin-group-id")

  private val zoneCreate = ZoneChange(ok, "ok", ZoneChangeType.Create, ZoneChangeStatus.Synced)
  private val listZoneChangeResponse = ListZoneChangesResponse(
    ok.id,
    List(zoneCreate, zoneUpdate),
    nextId = None,
    startFrom = None,
    maxItems = 100
  )

  val crypto = new JavaCrypto(
    ConfigFactory.parseString(
      """secret = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61""""
    )
  )
  val testLimitConfig: LimitsConfig =
    LimitsConfig(100,100,1000,1500,100,100,100)

  val zoneRoute: Route =
    new ZoneRoute(TestZoneService,testLimitConfig, new TestVinylDNSAuthenticator(okAuth), crypto).getRoutes

  object TestZoneService extends ZoneServiceAlgebra {
    def connectToZone(
        createZoneInput: CreateZoneInput,
        auth: AuthPrincipal
    ): Result[ZoneCommandResult] = {
      val outcome = createZoneInput.email match {
        case alreadyExists.email => Left(ZoneAlreadyExistsError(s"$createZoneInput"))
        case notFound.email => Left(ZoneNotFoundError(s"$createZoneInput"))
        case notAuthorized.email => Left(NotAuthorizedError(s"$createZoneInput"))
        case badAdminId.email => Left(InvalidGroupError(s"$createZoneInput"))
        case ok.email | connectionOk.email | trailingDot.email | "invalid-zone-status@test.com" =>
          Right(
            zoneCreate.copy(
              status = ZoneChangeStatus.Synced,
              zone = Zone(createZoneInput, false).copy(status = ZoneStatus.Active)
            )
          )
        case error.email => Left(new RuntimeException("fail"))
        case connectionFailed.email => Left(ConnectionFailed(Zone(createZoneInput, false), "fail"))
        case zoneValidationFailed.email =>
          Left(ZoneValidationFailed(Zone(createZoneInput, false), List("fail"), "failure message"))
        case nonSuperUserSharedZone.email =>
          Left(NotAuthorizedError("unauth"))
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def updateZone(
        updateZoneInput: UpdateZoneInput,
        auth: AuthPrincipal
    ): Result[ZoneCommandResult] = {
      val outcome = updateZoneInput.email match {
        case alreadyExists.email => Left(ZoneAlreadyExistsError(s"$updateZoneInput"))
        case notFound.email => Left(ZoneNotFoundError(s"$updateZoneInput"))
        case notAuthorized.email => Left(NotAuthorizedError(s"$updateZoneInput"))
        case badAdminId.email => Left(InvalidGroupError(s"$updateZoneInput"))
        case ok.email | connectionOk.email =>
          Right(
            zoneUpdate.copy(
              status = ZoneChangeStatus.Synced,
              zone = Zone(updateZoneInput, zoneUpdate.zone).copy(status = ZoneStatus.Active)
            )
          )
        case error.email => Left(new RuntimeException("fail"))
        case zone1.email => Left(ZoneUnavailableError(s"$updateZoneInput"))
        case connectionFailed.email =>
          Left(ConnectionFailed(Zone(updateZoneInput, zoneUpdate.zone), "fail"))
        case zoneValidationFailed.email =>
          Left(
            ZoneValidationFailed(
              Zone(updateZoneInput, zoneUpdate.zone),
              List("fail"),
              "failure message"
            )
          )
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def deleteZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] = {
      val outcome = zoneId match {
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case notAuthorized.id => Left(NotAuthorizedError(s"$zoneId"))
        case ok.id | connectionOk.id =>
          Right(ZoneChange(ok, "ok", ZoneChangeType.Delete, ZoneChangeStatus.Synced))
        case error.id => Left(new RuntimeException("fail"))
        case zone1.id => Left(ZoneUnavailableError(zoneId))
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def syncZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult] = {
      val outcome = zoneId match {
        case ok.id => Right(zoneUpdate.copy(changeType = ZoneChangeType.Sync))
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case notAuthorized.id => Left(NotAuthorizedError(s"$zoneId"))
        case zone1.id => Left(InvalidSyncStateError(s"$zoneId"))
        case zone2.id => Left(PendingUpdateError(s"$zoneId"))
        case zone3.id => Left(RecentSyncError(s"$zoneId"))
        case zone4.id => Left(ZoneInactiveError(s"$zoneId"))
        case zone5.id => Left(ZoneUnavailableError(s"$zoneId"))
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def getZone(zoneId: String, auth: AuthPrincipal): Result[ZoneInfo] = {
      val outcome = zoneId match {
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case ok.id => Right(okAsZoneInfo)
        case error.id => Left(new RuntimeException("fail"))
      }
      outcome.toResult
    }

    def getZoneByName(zoneName: String, auth: AuthPrincipal): Result[ZoneInfo] = {
      val outcome = zoneName match {
        case notFound.name => Left(ZoneNotFoundError(s"$zoneName"))
        case ok.name => Right(okAsZoneInfo)
        case error.name => Left(new RuntimeException("fail"))
      }
      outcome.toResult
    }

    def listZones(
        authPrincipal: AuthPrincipal,
        nameFilter: Option[String],
        startFrom: Option[String],
        maxItems: Int,
        searchByAdminGroup: Boolean = false,
        ignoreAccess: Boolean = false,
        includeReverse: Boolean = true
    ): Result[ListZonesResponse] = {

      val outcome = (authPrincipal, nameFilter, startFrom, maxItems, ignoreAccess) match {
        case (_, None, Some("zone3."), 3, false) =>
          Right(
            ListZonesResponse(
              zones = List(zoneSummaryInfo1, zoneSummaryInfo2, zoneSummaryInfo3),
              nameFilter = None,
              startFrom = Some("zone3."),
              nextId = Some("zone6."),
              maxItems = 3,
              ignoreAccess = false
            )
          )
        case (_, None, Some("zone4."), 4, false) =>
          Right(
            ListZonesResponse(
              zones = List(zoneSummaryInfo1, zoneSummaryInfo2, zoneSummaryInfo3),
              nameFilter = None,
              startFrom = Some("zone4."),
              nextId = None,
              maxItems = 4,
              ignoreAccess = false
            )
          )

        case (_, None, None, 3, false) =>
          Right(
            ListZonesResponse(
              zones = List(zoneSummaryInfo1, zoneSummaryInfo2, zoneSummaryInfo3),
              nameFilter = None,
              startFrom = None,
              nextId = Some("zone3."),
              maxItems = 3,
              ignoreAccess = false
            )
          )

        case (_, None, None, 5, true) =>
          Right(
            ListZonesResponse(
              zones = List(
                zoneSummaryInfo1,
                zoneSummaryInfo2,
                zoneSummaryInfo3,
                zoneSummaryInfo4,
                zoneSummaryInfo5
              ),
              nameFilter = None,
              startFrom = None,
              nextId = None,
              maxItems = 5,
              ignoreAccess = true
            )
          )

        case (_, Some(filter), Some("zone4."), 4, false) =>
          Right(
            ListZonesResponse(
              zones = List(zoneSummaryInfo1, zoneSummaryInfo2, zoneSummaryInfo3),
              nameFilter = Some(filter),
              startFrom = Some("zone4."),
              nextId = None,
              maxItems = 4,
              ignoreAccess = false
            )
          )

        case (_, None, None, _, _) =>
          Right(
            ListZonesResponse(
              zones = List(zoneSummaryInfo1, zoneSummaryInfo2, zoneSummaryInfo3),
              nameFilter = None,
              startFrom = None,
              nextId = None,
              ignoreAccess = false
            )
          )

        case _ => Left(InvalidRequest("shouldnt get here"))
      }

      outcome.toResult
    }

    def listZoneChanges(
        zoneId: String,
        authPrincipal: AuthPrincipal,
        startFrom: Option[String],
        maxItems: Int
    ): Result[ListZoneChangesResponse] = {
      val outcome = zoneId match {
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case notAuthorized.id => Left(NotAuthorizedError("no way"))
        case _ => Right(listZoneChangeResponse)
      }
      outcome.toResult
    }

    def addACLRule(
        zoneId: String,
        aclRuleInfo: ACLRuleInfo,
        authPrincipal: AuthPrincipal
    ): Result[ZoneCommandResult] = {
      val outcome = zoneId match {
        case badRegex.id =>
          Left(InvalidRequest("record mask x{5,-3} is an invalid regex"))
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case notAuthorized.id => Left(NotAuthorizedError(s"$zoneId"))
        case ok.id | connectionOk.id =>
          val newRule = ACLRule(aclRuleInfo)
          Right(
            ZoneChangeGenerator
              .forUpdate(
                ok.addACLRule(newRule),
                ok,
                authPrincipal,
                NoOpCrypto.instance
              )
              .copy(status = ZoneChangeStatus.Synced)
          )
        case error.id => Left(new RuntimeException("fail"))
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def deleteACLRule(
        zoneId: String,
        aclRuleInfo: ACLRuleInfo,
        authPrincipal: AuthPrincipal
    ): Result[ZoneCommandResult] = {
      val outcome = zoneId match {
        case notFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        case notAuthorized.id => Left(NotAuthorizedError(s"$zoneId"))
        case ok.id | connectionOk.id =>
          val rule = ACLRule(aclRuleInfo)
          Right(
            ZoneChangeGenerator
              .forUpdate(
                ok.deleteACLRule(rule),
                ok,
                authPrincipal,
                NoOpCrypto.instance
              )
              .copy(status = ZoneChangeStatus.Synced)
          )
        case error.id => Left(new RuntimeException("fail"))
      }
      outcome.map(c => c.asInstanceOf[ZoneCommandResult]).toResult
    }

    def getBackendIds(): Result[List[String]] = List("backend-1", "backend-2").toResult
  }

  val zoneService: ZoneServiceAlgebra = TestZoneService

  def zoneJson(name: String, email: String): String =
    zoneJson(Zone(name, email, connection = null, created = null, status = null, id = null))

  def zoneJson(zone: Zone): String = compact(render(Extraction.decompose(zone)))

  def post(zone: Zone): HttpRequest =
    Post("/zones").withEntity(HttpEntity(ContentTypes.`application/json`, zoneJson(zone)))

  def post(js: JValue): HttpRequest =
    Post("/zones").withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(js))))

  def js(aclInfo: ACLRuleInfo): String = compact(render(Extraction.decompose(aclInfo)))

  def js(jv: JValue): String = compact(render(jv))

  def ruleThatMatchesRequest(ruleInfo: ACLRuleInfo): ACLRule => Boolean =
    rule =>
      ruleInfo.recordTypes == rule.recordTypes &&
        ruleInfo.recordMask == rule.recordMask &&
        ruleInfo.description == rule.description &&
        ruleInfo.accessLevel == rule.accessLevel &&
        ruleInfo.userId == rule.userId &&
        ruleInfo.groupId == rule.groupId

  "PUT zone acl rule" should {
    val goodRequest = ACLRuleInfo(
      AccessLevel.Read,
      Some("desc"),
      Some("user"),
      None,
      Some("www-*"),
      Set(RecordType.AAAA, RecordType.CNAME)
    )
    "return a 202 Accepted when the acl rule is good" in {
      Put(s"/zones/${ok.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(goodRequest))) ~> zoneRoute ~> check {
        status shouldBe Accepted

        val result = responseAs[ZoneChange]
        val zone = result.zone
        val acl = zone.acl
        val rule = acl.rules.find(ruleThatMatchesRequest(goodRequest)).get

        rule.accessLevel shouldBe goodRequest.accessLevel
        rule.description shouldBe goodRequest.description
        rule.userId shouldBe goodRequest.userId
        rule.groupId shouldBe goodRequest.groupId
        rule.recordMask shouldBe goodRequest.recordMask
        rule.recordTypes shouldBe goodRequest.recordTypes
      }
    }

    "return a 202 Accepted when the acl rule has neither user nor group id" in {
      val requestAllUsers = ACLRuleInfo(
        AccessLevel.Read,
        Some("desc"),
        userId = None,
        groupId = None,
        recordMask = None,
        recordTypes = Set()
      )

      Put(s"/zones/${ok.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(requestAllUsers))) ~> zoneRoute ~> check {
        status shouldBe Accepted

        val result = responseAs[ZoneChange]
        val zone = result.zone
        val acl = zone.acl
        val rule = acl.rules.find(ruleThatMatchesRequest(requestAllUsers)).get

        rule.userId shouldBe None
        rule.groupId shouldBe None
      }
    }

    "return a 404 Not Found when the zone is not found" in {
      Put(s"/zones/${notFound.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }

    "return a 403 Forbidden if not authorized" in {
      Put(s"/zones/${notAuthorized.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "return a 500 if there is an unexpected failure" in {
      Put(s"/zones/${error.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> Route
        .seal(zoneRoute) ~> check {
        status shouldBe InternalServerError
      }
    }

    "return a 400 Bad Request when accessLevel is not provided" in {
      val missingACLAccessLevel: JValue =
        ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny")))

      Put(s"/zones/${ok.id}/acl/rules")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, compact(render(missingACLAccessLevel)))
        ) ~> Route.seal(zoneRoute) ~> check {
        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing ACLRule.accessLevel"
        )
      }
    }

    "return a 400 Bad Request when userId and groupId are both provided" in {
      val withUserAndGroup: JValue =
        ("accessLevel" -> "Read") ~~
          ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny"))) ~~
          ("groupId" -> Extraction.decompose(Some("level2")))

      Put(s"/zones/${ok.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(withUserAndGroup)))) ~> Route
        .seal(zoneRoute) ~> check {

        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Cannot specify both a userId and a groupId"
        )
      }
    }

    "return a 400 Bad Request when given an invalid regex mask" in {
      val badRequest = ACLRuleInfo(
        AccessLevel.Read,
        Some("desc"),
        Some("user"),
        None,
        Some("x{5,-3}"),
        Set(RecordType.AAAA, RecordType.CNAME)
      )
      Put(s"/zones/${badRegex.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(badRequest))) ~> zoneRoute ~> check {

        status shouldBe BadRequest
        val result = responseAs[JValue]
        result.extract[String] shouldBe "record mask x{5,-3} is an invalid regex"
      }
    }
  }

  "DELETE zone acl rule" should {
    val goodRequest = ACLRuleInfo(
      AccessLevel.Read,
      Some("desc"),
      Some("user"),
      None,
      Some("www-*"),
      Set(RecordType.AAAA, RecordType.CNAME)
    )
    "return a 202 Accepted when the acl rule is good" in {
      Delete(s"/zones/${ok.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(goodRequest))) ~> zoneRoute ~> check {
        status shouldBe Accepted

        // just make sure we have a zone change as a response
        val result = responseAs[ZoneChange]
        result.zone.id shouldBe ok.id
      }
    }

    "return a 404 Not Found when the zone is not found" in {
      Delete(s"/zones/${notFound.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }

    "return a 403 Forbidden if not authorized" in {
      Delete(s"/zones/${notAuthorized.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "return a 500 if there is an unexpected failure" in {
      Delete(s"/zones/${error.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, js(userAclRuleInfo))) ~> Route
        .seal(zoneRoute) ~> check {
        status shouldBe InternalServerError
      }
    }

    "return a 400 Bad Request when accessLevel is not provided" in {
      val missingACLAccessLevel: JValue =
        ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny")))

      Delete(s"/zones/${ok.id}/acl/rules")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, compact(render(missingACLAccessLevel)))
        ) ~> Route.seal(zoneRoute) ~> check {

        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing ACLRule.accessLevel"
        )
      }
    }

    "return a 400 Bad Request when userId and groupId are both provided" in {
      val withUserAndGroup: JValue =
        ("accessLevel" -> "Read") ~~
          ("description" -> Extraction.decompose(Some("description"))) ~~
          ("userId" -> Extraction.decompose(Some("johnny"))) ~~
          ("groupId" -> Extraction.decompose(Some("level2")))

      Delete(s"/zones/${ok.id}/acl/rules")
        .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(withUserAndGroup)))) ~> Route
        .seal(zoneRoute) ~> check {

        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Cannot specify both a userId and a groupId"
        )
      }
    }
  }

  "POST zone" should {
    "return 202 Accepted when the zone is created" in {
      post(ok) ~> zoneRoute ~> check {
        status shouldBe Accepted
      }
    }

    "encrypt the connection and transfer connection keys" in {
      post(connectionOk) ~> zoneRoute ~> check {
        status shouldBe Accepted
        val result = responseAs[ZoneChange]
        val resultKey = result.zone.connection.get.key
        val resultTCKey = result.zone.transferConnection.get.key

        val decrypted = crypto.decrypt(resultKey)
        val decryptedTC = crypto.decrypt(resultTCKey)
        decrypted shouldBe connectionOk.connection.get.key
        decryptedTC shouldBe connectionOk.transferConnection.get.key
      }
    }

    "return a fully populated zone in the response" in {
      post(ok) ~> zoneRoute ~> check {
        val result = responseAs[ZoneChange]
        result.changeType shouldBe ZoneChangeType.Create
        Option(result.status) shouldBe defined
        result.userId shouldBe "ok"
        Option(result.created) shouldBe defined

        val resultZone = result.zone
        resultZone.email shouldBe ok.email
        resultZone.name shouldBe ok.name
        Option(resultZone.created) shouldBe defined
        Option(resultZone.status) shouldBe defined
        resultZone.updated shouldBe None
        Option(resultZone.id) shouldBe defined
        resultZone.account shouldBe "system"
        resultZone.shared shouldBe ok.shared
        resultZone.acl shouldBe ok.acl
        resultZone.adminGroupId shouldBe "test"
      }
    }

    "change the zone name to a fully qualified domain name" in {
      post(trailingDot) ~> zoneRoute ~> check {
        status shouldBe Accepted
        val result = responseAs[ZoneChange]
        result.changeType shouldBe ZoneChangeType.Create
        Option(result.status) shouldBe defined
        result.userId shouldBe "ok"
        Option(result.created) shouldBe defined

        val resultZone = result.zone
        resultZone.email shouldBe trailingDot.email
        resultZone.name shouldBe (trailingDot.name + '.')
        Option(resultZone.created) shouldBe defined
        Option(resultZone.status) shouldBe defined
        resultZone.updated shouldBe None
        Option(resultZone.id) shouldBe defined
        resultZone.account shouldBe "system"
        resultZone.shared shouldBe trailingDot.shared
      }
    }

    "return 409 Conflict if the zone already exists" in {
      post(alreadyExists) ~> zoneRoute ~> check {
        status shouldBe Conflict
      }
    }

    "return 400 BadRequest if the zone adminGroupId is invalid" in {
      post(badAdminId) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "return 403 Forbidden if the zone is shared and user is not authorized" in {
      post(nonSuperUserSharedZone) ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "validate the connection when it is posted" in {
      post(connectionOk) ~> zoneRoute ~> check {
        status shouldBe Accepted
      }
    }

    "fail if the connection validation fails" in {
      post(connectionFailed) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "fail if the zone validation fails" in {
      post(zoneValidationFailed) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "report missing data" in {
      post(missingFields) ~> Route.seal(zoneRoute) ~> check {
        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing Zone.name",
          "Missing Zone.email",
          "Missing ZoneConnection.name",
          "Missing ZoneConnection.keyName",
          "Missing ZoneConnection.key",
          "Missing ZoneConnection.primaryServer",
          "Missing Zone.adminGroupId"
        )
      }
    }

    "ignore fields not defined in CreateZoneInput" in {
      post(zoneWithInvalidId) ~> Route.seal(zoneRoute) ~> check {
        status shouldBe Accepted
      }
    }
  }

  "DELETE zone" should {
    "return 202 on successful delete of existing zone" in {
      Delete(s"/zones/${ok.id}") ~> zoneRoute ~> check {
        status shouldBe Accepted

        val result = responseAs[ZoneChange]
        result.changeType shouldBe ZoneChangeType.Delete
        Option(result.status) shouldBe defined
        result.userId shouldBe "ok"
        Option(result.created) shouldBe defined
      }
    }

    "return 404 if the zone does not exist" in {
      Delete(s"/zones/${notFound.id}") ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }

    "return 403 if the user is not authorized" in {
      Delete(s"/zones/${notAuthorized.id}") ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "return a 409 Conflict if the zone is unavailable" in {
      Delete(s"/zones/${zone1.id}") ~> zoneRoute ~> check {
        status shouldBe Conflict
      }
    }
  }

  "GET zone" should {
    "return the zone is retrieved" in {
      Get(s"/zones/${ok.id}") ~> zoneRoute ~> check {
        status shouldBe OK

        val resultZone = responseAs[GetZoneResponse].zone
        resultZone.email shouldBe ok.email
        resultZone.name shouldBe ok.name
        Option(resultZone.created) shouldBe defined
        Option(resultZone.status) shouldBe defined
        resultZone.updated shouldBe None
        Option(resultZone.id) shouldBe defined
        resultZone.account shouldBe "system"
        resultZone.acl shouldBe aclAsInfo
        resultZone.adminGroupId shouldBe "test"
      }
    }

    "return 404 if the zone does not exist" in {
      Get(s"/zones/${notFound.id}") ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }
  }

  "GET zone by name " should {
    "return the zone is retrieved" in {
      Get(s"/zones/name/${ok.name}") ~> zoneRoute ~> check {
        status shouldBe OK

        val resultZone = responseAs[GetZoneResponse].zone
        resultZone.email shouldBe ok.email
        resultZone.name shouldBe ok.name
        Option(resultZone.created) shouldBe defined
        Option(resultZone.status) shouldBe defined
        resultZone.updated shouldBe None
        Option(resultZone.id) shouldBe defined
        resultZone.account shouldBe "system"
        resultZone.acl shouldBe aclAsInfo
        resultZone.adminGroupId shouldBe "test"
      }
    }

    "return 404 if the zone does not exist" in {
      Get(s"/zones/name/${notFound.name}") ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }
  }

  "GET all zones" should {
    "return the zones" in {
      Get("/zones") ~> zoneRoute ~> check {
        val zones = responseAs[ListZonesResponse].zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
      }
    }
  }

  "GET zones" should {
    "return the next id when more results exist" in {
      Get(s"/zones?startFrom=zone3.&maxItems=3") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
        resp.nextId shouldBe Some("zone6.")
        resp.maxItems shouldBe 3
        resp.startFrom shouldBe Some("zone3.")
      }
    }

    "not return the next id when there are no more results" in {
      Get(s"/zones?startFrom=zone4.&maxItems=4") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
        resp.nextId shouldBe None
        resp.maxItems shouldBe 4
        resp.startFrom shouldBe Some("zone4.")
        resp.ignoreAccess shouldBe false
      }
    }

    "not return the start from when not provided" in {
      Get(s"/zones?maxItems=3") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
        resp.nextId shouldBe Some("zone3.")
        resp.maxItems shouldBe 3
        resp.startFrom shouldBe None
        resp.ignoreAccess shouldBe false
      }
    }

    "return the name filter when provided" in {
      Get(s"/zones?nameFilter=foo&startFrom=zone4.&maxItems=4") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
        resp.nextId shouldBe None
        resp.maxItems shouldBe 4
        resp.startFrom shouldBe Some("zone4.")
        resp.nameFilter shouldBe Some("foo")
        resp.ignoreAccess shouldBe false
      }
    }

    "return zones by admin group name when searchByAdminGroup is true" in {
      Get(s"/zones?nameFilter=ok&startFrom=zone4.&maxItems=4&searchByAdminGroup=true") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id)
        resp.nextId shouldBe None
        resp.maxItems shouldBe 4
        resp.startFrom shouldBe Some("zone4.")
        resp.nameFilter shouldBe Some("ok")
        resp.ignoreAccess shouldBe false
      }
    }

    "return all zones when list all is true" in {
      Get(s"/zones?maxItems=5&ignoreAccess=true") ~> zoneRoute ~> check {
        val resp = responseAs[ListZonesResponse]
        val zones = resp.zones
        (zones.map(_.id) should contain)
          .only(zone1.id, zone2.id, zone3.id, zone4.id, zone5.id)
        resp.nextId shouldBe None
        resp.maxItems shouldBe 5
        resp.startFrom shouldBe None
        resp.nameFilter shouldBe None
        resp.ignoreAccess shouldBe true
      }
    }

    "return an error if the max items is out of range" in {
      Get(s"/zones?maxItems=700") ~> zoneRoute ~> check {
        status shouldBe BadRequest
        responseEntity.toString should include(
          "maxItems was 700, maxItems must be between 0 and 100"
        )
      }
    }
  }

  "GET zone changes" should {
    "return the zone changes" in {
      Get(s"/zones/${ok.id}/changes") ~> zoneRoute ~> check {
        val changes = responseAs[ListZoneChangesResponse]

        changes.zoneId shouldBe ok.id
        (changes.zoneChanges.map(_.id) should contain)
          .only(zoneCreate.id, zoneUpdate.id)
      }
    }

    "return the ZoneNotFoundError when the zone does not exist" in {
      Get(s"/zones/${notFound.id}/changes") ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }

    "return a Forbidden when the user is not authorized" in {
      Get(s"/zones/${notAuthorized.id}/changes") ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "return a Bad Request when maxItems is out of Bounds" in {
      Get(s"/zones/${ok.id}/changes?maxItems=101") ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
      Get(s"/zones/${ok.id}/changes?maxItems=0") ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }
  }

  "PUT zone" should {
    "return 202 when the zone is updated" in {
      Put(s"/zones/${ok.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, zoneJson(ok))) ~> zoneRoute ~> check {
        status shouldBe Accepted
        val result = responseAs[ZoneChange]
        result.changeType shouldBe ZoneChangeType.Update
        Option(result.status) shouldBe defined
        result.userId shouldBe "ok"
        Option(result.created) shouldBe defined

        val resultZone = result.zone
        resultZone.email shouldBe ok.email
        resultZone.name shouldBe ok.name
        Option(resultZone.created) shouldBe defined
        resultZone.status shouldBe ZoneStatus.Active
        Option(resultZone.updated) shouldBe defined
        Option(resultZone.id) shouldBe defined
        resultZone.account shouldBe "system"
        resultZone.acl shouldBe ok.acl
        resultZone.adminGroupId shouldBe "test"
      }
    }

    "return 404 NotFound if the zone is not found" in {
      Put(s"/zones/${notFound.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, zoneJson(notFound))) ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }

    "return 403 if the user is not authorized" in {
      Put(s"/zones/${notAuthorized.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, zoneJson(notAuthorized))
      ) ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }

    "return 400 BadRequest if the zone adminGroupId is invalid" in {
      Put(s"/zones/${badAdminId.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, zoneJson(badAdminId))) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "return a 409 Conflict if the zone is unavailable" in {
      Put(s"/zones/${zone1.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, zoneJson(zone1))) ~> zoneRoute ~> check {
        status shouldBe Conflict
      }
    }

    "validate the connection when the update is posted" in {
      Put(s"/zones/${connectionOk.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, zoneJson(connectionOk))
      ) ~> zoneRoute ~> check {
        status shouldBe Accepted
      }
    }

    "fail the update if the connection validation fails" in {
      Put(s"/zones/${connectionFailed.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, zoneJson(connectionFailed))
      ) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "fail the update if the zone validation fails" in {
      Put(s"/zones/${zoneValidationFailed.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, zoneJson(zoneValidationFailed))
      ) ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }

    "report missing data" in {
      Put(s"/zones/${ok.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, compact(render(missingFields)))
      ) ~> Route.seal(zoneRoute) ~> check {
        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing Zone.id",
          "Missing Zone.name",
          "Missing Zone.email",
          "Missing ZoneConnection.name",
          "Missing ZoneConnection.keyName",
          "Missing ZoneConnection.key",
          "Missing ZoneConnection.primaryServer",
          "Missing Zone.adminGroupId"
        )
      }
    }

    "report type mismatch" in {
      Put(s"/zones/${ok.id}").withEntity(
        HttpEntity(ContentTypes.`application/json`, compact(render(zoneWithInvalidId)))
      ) ~> Route
        .seal(zoneRoute) ~> check {
        status shouldBe BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get shouldBe List("Do not know how to convert JBool(true) into class java.lang.String")
      }
    }
  }

  "POST zone sync" should {
    "return 202 Accepted if the zone can be synced" in {
      Post(s"/zones/${ok.id}/sync") ~> zoneRoute ~> check {
        val result = responseAs[ZoneChange]
        result.changeType shouldBe ZoneChangeType.Sync
        status shouldBe Accepted
      }
    }
    "return 404 NotFound if the zone is not found" in {
      Post(s"/zones/${notFound.id}/sync") ~> zoneRoute ~> check {
        status shouldBe NotFound
      }
    }
    "return a Forbidden if the user is not authorized" in {
      Post(s"/zones/${notAuthorized.id}/sync") ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }
    "return a BadRequest if the zone is in an invalid state to be synced" in {
      Post(s"/zones/${zone1.id}/sync") ~> zoneRoute ~> check {
        status shouldBe BadRequest
      }
    }
    "return a Conflict if the zone has a pending update" in {
      Post(s"/zones/${zone2.id}/sync") ~> zoneRoute ~> check {
        status shouldBe Conflict
      }
    }
    "return Forbidden if the zone has recently been synced" in {
      Post(s"/zones/${zone3.id}/sync") ~> zoneRoute ~> check {
        status shouldBe Forbidden
      }
    }
    "return a Conflict if the zone is currently syncing" in {
      Post(s"/zones/${zone5.id}/sync") ~> zoneRoute ~> check {
        status shouldBe Conflict
      }
    }
  }

  "GET backendids" should {
    "return a 200 OK with the backend ids" in {
      Get("/zones/backendids") ~> zoneRoute ~> check {
        status shouldBe OK
        val result = responseAs[List[String]]
        result shouldBe List("backend-1", "backend-2")
      }
    }
  }
}
