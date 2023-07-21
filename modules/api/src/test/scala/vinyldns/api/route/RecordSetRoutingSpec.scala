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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.Interfaces._
import vinyldns.api.config.LimitsConfig
import vinyldns.api.domain.record.{ListFailedRecordSetChangesResponse, ListRecordSetChangesResponse, ListRecordSetHistoryResponse, RecordSetServiceAlgebra}
import vinyldns.api.domain.zone._
import vinyldns.core.TestMembershipData.okAuth
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.RecordTypeSort.{ASC, DESC, RecordTypeSort}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._

import scala.util.Random

class RecordSetRoutingSpec
  extends AnyWordSpec
    with ScalatestRouteTest
    with VinylDNSJsonProtocol
    with VinylDNSRouteTestHelper
    with Matchers {

  private val zoneNotFound = Zone("not.found", "test@test.com")
  private val okZone = Zone("ok", "test@test.com")
  private val zoneDeleted = Zone("inactive", "test@test.com", ZoneStatus.Deleted)
  private val notAuthorizedZone = Zone("notAuth", "test@test.com")
  private val syncingZone = Zone("syncing", "test@test.com")
  private val invalidChangeZone = Zone("invalidChange", "test@test.com")

  private val rsAlreadyExists = RecordSet(
    okZone.id,
    "alreadyexists",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsPendingUpdate = RecordSet(
    okZone.id,
    "pendingupdate",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsNotFound = RecordSet(
    okZone.id,
    "notfound",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsOk = RecordSet(
    okZone.id,
    "ok",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsError = RecordSet(
    okZone.id,
    "error",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsInvalidRequest = RecordSet(
    okZone.id,
    "invalid-request",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsZoneNotFound = RecordSet(
    zoneNotFound.id,
    "zonenotfound",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsZoneDeleted = RecordSet(
    zoneDeleted.id,
    "zonedeleted",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsZoneSyncing = RecordSet(
    syncingZone.id,
    "zonesyncing",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rsNotAuthorized = RecordSet(
    notAuthorizedZone.id,
    "changenotauth",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rs1 = RecordSet(
    okZone.id,
    "rs1",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rs2 = RecordSet(
    okZone.id,
    "rs2",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1")),
    ownerGroupId = Some("my-group")
  )

  private val rs3 = RecordSet(
    okZone.id,
    "rs3",
    RecordType.A,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("10.1.1.1"))
  )

  private val rs4 = RecordSet(
    okZone.id,
    "rs4",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("example.com")))
  )

  private val aaaa = RecordSet(
    okZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AAAAData("1:2:3:4:5:6:7:8"))
  )

  private val cname = RecordSet(
    okZone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("cname.")))
  )

  private val mx = RecordSet(
    okZone.id,
    "mx",
    RecordType.MX,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(MXData(100, Fqdn("exchange.")))
  )

  private val ns = RecordSet(
    okZone.id,
    "ns",
    RecordType.NS,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NSData(Fqdn("nsrecordname")))
  )

  private val ptr = RecordSet(
    okZone.id,
    "ptr",
    RecordType.PTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(PTRData(Fqdn("ptr.")))
  )

  private val soa = RecordSet(
    okZone.id,
    "soa",
    RecordType.SOA,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SOAData(Fqdn("name"), "name", 1, 2, 3, 4, 5))
  )

  private val spf = RecordSet(
    okZone.id,
    "soa",
    RecordType.SPF,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SPFData("spf"))
  )

  private val srv = RecordSet(
    okZone.id,
    "srv",
    RecordType.SRV,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SRVData(1, 2, 3, Fqdn("target.")))
  )

  private val naptr = RecordSet(
    okZone.id,
    "naptr",
    RecordType.NAPTR,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(NAPTRData(1, 2, "U", "E2U+sip", "!.*!test.!", Fqdn("target.")))
  )

  private val sshfp = RecordSet(
    okZone.id,
    "sshfp",
    RecordType.SSHFP,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(SSHFPData(1, 2, "fingerprint"))
  )

  private val txt = RecordSet(
    okZone.id,
    "txt",
    RecordType.TXT,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("text"))
  )

  private val invalidCname = RecordSet(
    okZone.id,
    "cname",
    RecordType.CNAME,
    200,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("cname")), CNAMEData(Fqdn("cname2")))
  )

  private val rsMissingData: JValue =
    ("key" -> "value") ~~ ("other" -> "val")

  private val rsInvalidType: JValue =
    ("type" -> "invalidType") ~~
      ("zoneId" -> okZone.id) ~~
      ("name" -> "rsInvalidType") ~~
      ("ttl" -> 200) ~~
      ("records" -> Extraction.decompose(List(TXTData("text"))))

  private val rsInvalidRecord: JValue =
    ("type" -> Extraction.decompose(RecordType.A)) ~~
      ("zoneId" -> okZone.id) ~~
      ("name" -> "rsInvalidType") ~~
      ("ttl" -> 200) ~~
      ("records" -> Extraction.decompose(List(TXTData("text"))))

  private val rsInvalidTTL: JValue =
    ("type" -> Extraction.decompose(RecordType.A)) ~~
      ("zoneId" -> okZone.id) ~~
      ("name" -> "rsInvalidTTL") ~~
      ("ttl" -> 5) ~~
      ("records" -> Extraction.decompose(List(AData("1.1.1.1"))))

  private val recordSets = Map(
    rsOk.id -> rsOk,
    aaaa.id -> aaaa,
    cname.id -> cname,
    mx.id -> mx,
    ns.id -> ns,
    ptr.id -> ptr,
    soa.id -> soa,
    spf.id -> spf,
    srv.id -> srv,
    naptr.id -> naptr,
    sshfp.id -> sshfp,
    txt.id -> txt
  )

  private val rsOkSummary = RecordSetInfo(rsOk, None)

  private val rsChange1 = RecordSetChange(
    okZone,
    rs1,
    "system",
    RecordSetChangeType.Create,
    RecordSetChangeStatus.Complete
  )
  private val rsChange2 = RecordSetChange(
    okZone,
    rs2,
    "system",
    RecordSetChangeType.Create,
    RecordSetChangeStatus.Complete
  )
  private val changesWithUserName =
    List(RecordSetChangeInfo(rsChange1, Some("ok")), RecordSetChangeInfo(rsChange2, Some("ok")))
  private val listRecordSetChangesResponse = ListRecordSetChangesResponse(
    okZone.id,
    changesWithUserName,
    nextId = None,
    startFrom = None,
    maxItems = 100
  )

  private val changeWithUserName =
    List(RecordSetChangeInfo(rsChange1, Some("ok")))
  private val listRecordSetChangeHistoryResponse = ListRecordSetHistoryResponse(
    Some(okZone.id),
    changeWithUserName,
    nextId = None,
    startFrom = None,
    maxItems = 100
  )

  private val failedChangesWithUserName =
    List(rsChange1.copy(status = RecordSetChangeStatus.Failed) , rsChange2.copy(status = RecordSetChangeStatus.Failed))
  private val listFailedRecordSetChangeResponse = ListFailedRecordSetChangesResponse(
    failedChangesWithUserName,0,0,100
  )

  class TestService extends RecordSetServiceAlgebra {

    def evaluate(
                  rsId: String,
                  zoneId: String,
                  authPrincipal: AuthPrincipal,
                  chgType: RecordSetChangeType
                ): Either[Throwable, RecordSetChange] = zoneId match {
      case zoneNotFound.id => Left(ZoneNotFoundError(zoneId))
      case zoneDeleted.id => Left(ZoneInactiveError(zoneId))
      case notAuthorizedZone.id => Left(NotAuthorizedError(zoneId))
      case syncingZone.id => Left(ZoneUnavailableError(zoneId))
      case invalidChangeZone.id =>
        Right(
          RecordSetChange(
            zone = invalidChangeZone,
            recordSet = recordSets(rsId)
              .copy(
                status = RecordSetStatus.Active,
                created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
                updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
              ),
            status = RecordSetChangeStatus.Complete,
            changeType = chgType,
            userId = authPrincipal.userId
          )
        )
      case okZone.id =>
        rsId match {
          case rsError.id => Left(new RuntimeException("fail"))
          case rsAlreadyExists.id => Left(RecordSetAlreadyExists(rsId))
          case rsPendingUpdate.id => Left(PendingUpdateError(rsId))
          case rsNotFound.id => Left(RecordSetNotFoundError(rsId))
          case rsNotAuthorized.id => Left(NotAuthorizedError(rsId))
          case rsInvalidRequest.id => Left(InvalidRequest(rsId))
          case other =>
            if (chgType == RecordSetChangeType.Create)
              Right(
                RecordSetChange(
                  zone = okZone,
                  recordSet = recordSets(other)
                    .copy(status = RecordSetStatus.Active, created = Instant.now.truncatedTo(ChronoUnit.MILLIS)),
                  status = RecordSetChangeStatus.Complete,
                  changeType = chgType,
                  userId = authPrincipal.userId
                )
              )
            else
              Right(
                RecordSetChange(
                  zone = okZone,
                  recordSet = recordSets(other)
                    .copy(
                      status = RecordSetStatus.Active,
                      created = Instant.now.truncatedTo(ChronoUnit.MILLIS),
                      updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
                    ),
                  status = RecordSetChangeStatus.Complete,
                  changeType = chgType,
                  userId = authPrincipal.userId
                )
              )
        }
    }

    def addRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] = {
      evaluate(recordSet.id, recordSet.zoneId, auth, RecordSetChangeType.Create)
    }.map(c => c.asInstanceOf[ZoneCommandResult]).toResult

    def updateRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult] = {
      evaluate(recordSet.id, recordSet.zoneId, auth, RecordSetChangeType.Update)
    }.map(c => c.asInstanceOf[ZoneCommandResult]).toResult

    def deleteRecordSet(
                         recordSetId: String,
                         zoneId: String,
                         auth: AuthPrincipal
                       ): Result[ZoneCommandResult] = {
      evaluate(recordSetId, zoneId, auth, RecordSetChangeType.Delete)
    }.map(c => c.asInstanceOf[ZoneCommandResult]).toResult

    def getRecordSet(
                      recordSetId: String,
                      authPrincipal: AuthPrincipal
                    ): Result[RecordSetInfo] = {
      recordSetId match {
        case rsError.id => Left(new RuntimeException("fail"))
        case rsNotFound.id => Left(RecordSetNotFoundError(s"$recordSetId"))
        case rsOk.id => Right(rsOkSummary)
      }
    }.toResult

    def getRecordSetByZone(
                            recordSetId: String,
                            zoneId: String,
                            authPrincipal: AuthPrincipal
                          ): Result[RecordSetInfo] = {
      if (zoneId == zoneNotFound.id) {
        Left(ZoneNotFoundError(s"$zoneId"))
      } else {
        recordSetId match {
          case rsError.id => Left(new RuntimeException("fail"))
          case rsNotFound.id => Left(RecordSetNotFoundError(s"$zoneId"))
          case rsOk.id => Right(rsOkSummary)
        }
      }
    }.toResult

    def listRecordSets(
                        startFrom: Option[String],
                        maxItems: Option[Int],
                        recordNameFilter: String,
                        recordTypeFilter: Option[Set[RecordType]],
                        recordOwnerGroupFilter: Option[String],
                        nameSort: NameSort,
                        authPrincipal: AuthPrincipal,
                        recordTypeSort: RecordTypeSort
                      ): Result[ListGlobalRecordSetsResponse] = {
      if (recordTypeFilter.contains(Set(CNAME))) {
        Right(
          ListGlobalRecordSetsResponse(
            List(
              RecordSetGlobalInfo(rs4, okZone.name, okZone.shared, None)
            ),
            startFrom,
            None,
            maxItems,
            "rs*",
            recordTypeFilter,
            recordOwnerGroupFilter,
            nameSort
          )
        )
      } else {
        val recordSetList = recordOwnerGroupFilter match {
          case Some("my-group") => List(RecordSetGlobalInfo(rs2, okZone.name, okZone.shared, None))
          case _ =>
            List(
              RecordSetGlobalInfo(rs1, okZone.name, okZone.shared, None),
              RecordSetGlobalInfo(rs2, okZone.name, okZone.shared, None),
              RecordSetGlobalInfo(rs3, okZone.name, okZone.shared, None)
            )
        }
        Right(
          ListGlobalRecordSetsResponse(
            recordSetList,
            startFrom,
            None,
            maxItems,
            "rs*",
            recordTypeFilter,
            None,
            nameSort
          )
        )
      }
    }.toResult

    def listFailedRecordSetChanges(
                                    authPrincipal: AuthPrincipal,
                                    startFrom: Int,
                                    maxItems: Int
                                  ): Result[ListFailedRecordSetChangesResponse] = {
      val outcome = authPrincipal match {
        case _ => Right(listFailedRecordSetChangeResponse)
      }
      outcome.toResult
    }

    def searchRecordSets(
                           startFrom: Option[String],
                           maxItems: Option[Int],
                           recordNameFilter: String,
                           recordTypeFilter: Option[Set[RecordType]],
                           recordOwnerGroupFilter: Option[String],
                           nameSort: NameSort,
                           authPrincipal: AuthPrincipal,
                           recordTypeSort: RecordTypeSort
                         ): Result[ListGlobalRecordSetsResponse] = {
      if (recordTypeFilter.contains(Set(CNAME))) {
        Right(
          ListGlobalRecordSetsResponse(
            List(
              RecordSetGlobalInfo(rs4, okZone.name, okZone.shared, None)
            ),
            startFrom,
            None,
            maxItems,
            "rs*",
            recordTypeFilter,
            recordOwnerGroupFilter,
            nameSort
          )
        )
      } else {
        val recordSetList = recordOwnerGroupFilter match {
          case Some("my-group") => List(RecordSetGlobalInfo(rs2, okZone.name, okZone.shared, None))
          case _ =>
            List(
              RecordSetGlobalInfo(rs1, okZone.name, okZone.shared, None),
              RecordSetGlobalInfo(rs2, okZone.name, okZone.shared, None),
              RecordSetGlobalInfo(rs3, okZone.name, okZone.shared, None)
            )
        }
        Right(
          ListGlobalRecordSetsResponse(
            recordSetList,
            startFrom,
            None,
            maxItems,
            "rs*",
            recordTypeFilter,
            None,
            nameSort
          )
        )
      }
    }.toResult

    def listRecordSetsByZone(
                              zoneId: String,
                              startFrom: Option[String],
                              maxItems: Option[Int],
                              recordNameFilter: Option[String],
                              recordTypeFilter: Option[Set[RecordType]],
                              recordOwnerGroupFilter: Option[String],
                              nameSort: NameSort,
                              authPrincipal: AuthPrincipal,
                              recordTypeSort: RecordTypeSort
                            ): Result[ListRecordSetsByZoneResponse] = {
      zoneId match {
        case zoneNotFound.id => Left(ZoneNotFoundError(s"$zoneId"))
        // NameSort will be in ASC by default
        case okZone.id if recordTypeSort==DESC =>
          Right(
            ListRecordSetsByZoneResponse(
              List(
                RecordSetListInfo(RecordSetInfo(soa, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(cname, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(aaaa, None), AccessLevel.Read)
              ),
              startFrom,
              None,
              maxItems,
              recordNameFilter,
              recordTypeFilter,
              None,
              nameSort,
              recordTypeSort
            )
          )
        // NameSort will be in ASC by default
        case okZone.id if recordTypeSort==ASC=>
          Right(
            ListRecordSetsByZoneResponse(
              List(
                RecordSetListInfo(RecordSetInfo(aaaa, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(cname, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(soa, None), AccessLevel.Read)
              ),
              startFrom,
              None,
              maxItems,
              recordNameFilter,
              recordTypeFilter,
              None,
              nameSort,
              recordTypeSort
            )
          )
        case okZone.id if recordTypeFilter.contains(Set(CNAME)) =>
          Right(
            ListRecordSetsByZoneResponse(
              List(
                RecordSetListInfo(RecordSetInfo(rs4, None), AccessLevel.Read)
              ),
              startFrom,
              None,
              maxItems,
              recordNameFilter,
              recordTypeFilter,
              recordOwnerGroupFilter,
              nameSort,
              recordTypeSort=RecordTypeSort.ASC
            )
          )
        case okZone.id if recordTypeFilter.isEmpty =>
          Right(
            ListRecordSetsByZoneResponse(
              List(
                RecordSetListInfo(RecordSetInfo(rs1, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(rs2, None), AccessLevel.Read),
                RecordSetListInfo(RecordSetInfo(rs3, None), AccessLevel.Read)
              ),
              startFrom,
              None,
              maxItems,
              recordNameFilter,
              recordTypeFilter,
              None,
              nameSort,
              recordTypeSort=RecordTypeSort.ASC
            )
          )
      }
    }.toResult

    def getRecordSetChange(
                            zoneId: String,
                            changeId: String,
                            authPrincipal: AuthPrincipal
                          ): Result[RecordSetChange] = {
      changeId match {
        case "changeNotFound" => Left(RecordSetChangeNotFoundError(""))
        case "zoneNotFound" => Left(ZoneNotFoundError(""))
        case "forbidden" => Left(NotAuthorizedError(""))
        case _ => Right(rsChange1)
      }
    }.toResult

    def listRecordSetChanges(
                              zoneId: Option[String],
                              startFrom: Option[Int],
                              maxItems: Int,
                              fqdn: Option[String],
                              recordType: Option[RecordType],
                              authPrincipal: AuthPrincipal
                            ): Result[ListRecordSetChangesResponse] = {
      zoneId match {
        case Some(zoneNotFound.id) => Left(ZoneNotFoundError(s"$zoneId"))
        case Some(notAuthorizedZone.id) => Left(NotAuthorizedError("no way"))
        case _ => Right(listRecordSetChangesResponse)
      }
    }.toResult

    def listRecordSetChangeHistory(
                              zoneId: Option[String],
                              startFrom: Option[Int],
                              maxItems: Int,
                              fqdn: Option[String],
                              recordType: Option[RecordType],
                              authPrincipal: AuthPrincipal
                            ): Result[ListRecordSetHistoryResponse] = {
      zoneId match {
        case Some(zoneNotFound.id) => Left(ZoneNotFoundError(s"$zoneId"))
        case Some(notAuthorizedZone.id) => Left(NotAuthorizedError("no way"))
        case _ => Right(listRecordSetChangeHistoryResponse)
      }
    }.toResult

  }

  val recordSetService: RecordSetServiceAlgebra = new TestService

  val testLimitConfig: LimitsConfig =
    LimitsConfig(100,100,1000,1500,100,100,100)

  val recordSetRoute: Route =
    new RecordSetRoute(recordSetService,testLimitConfig, new TestVinylDNSAuthenticator(okAuth)).getRoutes

  private def rsJson(recordSet: RecordSet): String =
    compact(render(Extraction.decompose(recordSet)))

  private def post(recordSet: RecordSet): HttpRequest =
    Post(s"/zones/${recordSet.zoneId}/recordsets")
      .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(recordSet)))

  private def validateCreateRecordType(rs: RecordSet) =
    post(rs) ~> recordSetRoute ~> check {
      status shouldBe StatusCodes.Accepted

      val change = responseAs[RecordSetChange]
      change.changeType shouldBe RecordSetChangeType.Create
      change.status shouldBe RecordSetChangeStatus.Complete
      Option(change.created) shouldBe defined
      change.userId shouldBe "ok"

      val resultRs = change.recordSet
      validateRecordSet(rs, resultRs)
    }

  private def validateRecordSet(expected: RecordSet, actual: RecordSet) = {
    actual.records should contain theSameElementsAs expected.records
    Option(actual.id) shouldBe defined
    actual.zoneId shouldBe expected.zoneId
    actual.name shouldBe expected.name
    actual.typ shouldBe expected.typ
    actual.ttl shouldBe expected.ttl
    Option(actual.created) shouldBe defined
    actual.status shouldBe RecordSetStatus.Active
  }

  private def validateErrors(js: JValue, expectedErrs: String*) = {
    val errSet = expectedErrs.toSet
    Post(s"/zones/${okZone.id}/recordsets")
      .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(js)))) ~>
      Route.seal(recordSetRoute) ~> check {
      status shouldBe StatusCodes.BadRequest
      val result = responseAs[JValue]
      val errs = (result \ "errors").extractOpt[List[String]]
      errs should not be None
      errs.get.toSet shouldBe errSet
    }
  }

  private def testRecordBase(rt: RecordType, records: JValue): JValue =
    ("type" -> Extraction.decompose(rt)) ~~
      ("zoneId" -> okZone.id) ~~
      ("name" -> "rsErrorTestRecord") ~~
      ("ttl" -> 200) ~~
      ("records" -> records)

  private def testRecordType(rt: RecordType, record: JValue): JValue =
    ("type" -> Extraction.decompose(rt)) ~~
      ("zoneId" -> okZone.id) ~~
      ("name" -> "rsErrorTestRecord") ~~
      ("ttl" -> 200) ~~
      ("records" -> JArray(List(record)))

  "GET recordset change" should {
    "return the recordset change" in {
      Get(s"/zones/${okZone.id}/recordsets/test/changes/good") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK

        status shouldBe StatusCodes.OK
        val result = responseAs[RecordSetChange]
        val resultRs = result.recordSet
        validateRecordSet(rsChange1.recordSet, resultRs)

        result.id shouldBe rsChange1.id
      }
    }

    "return a 404 Not Found when the zone doesn't exist" in {
      Get(s"/zones/${zoneNotFound.id}/recordsets/test/changes/zoneNotFound") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 404 Not Found when the change doesn't exist" in {
      Get(s"/zones/${okZone.id}/recordsets/test/changes/changeNotFound") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a forbidden when the user cant see the zone" in {
      Get(s"/zones/${okZone.id}/recordsets/test/changes/forbidden") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  "GET recordset changes" should {
    "return the recordset changes" in {
      Get(s"/zones/${okZone.id}/recordsetchanges") ~> recordSetRoute ~> check {
        val response = responseAs[ListRecordSetChangesResponse]

        response.zoneId shouldBe okZone.id
        (response.recordSetChanges.map(_.id) should contain)
          .only(rsChange1.id, rsChange2.id)
      }
    }

    "return the ZoneNotFoundError when the zone does not exist" in {
      Get(s"/zones/${zoneNotFound.id}/recordsetchanges") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a Forbidden when the user is not authorized" in {
      Get(s"/zones/${notAuthorizedZone.id}/recordsetchanges") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "return a Bad Request when maxItems is out of Bounds" in {
      Get(s"/zones/${okZone.id}/recordsetchanges?maxItems=101") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Get(s"/zones/${okZone.id}/recordsetchanges?maxItems=0") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET recordset change history" should {
    "return the recordset change" in {
      Get(s"/recordsetchange/history?fqdn=rs1.ok.&recordType=A") ~> recordSetRoute ~> check {
        val response = responseAs[ListRecordSetHistoryResponse]

        response.zoneId shouldBe Some(okZone.id)
        (response.recordSetChanges.map(_.id) should contain)
          .only(rsChange1.id)
      }
    }

    "return an error when the record fqdn and type is not defined" in {
      Get(s"/recordsetchange/history") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a Bad Request when maxItems is out of Bounds" in {
      Get(s"/recordsetchange/history?maxItems=101") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Get(s"/recordsetchange/history?maxItems=0") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET failed record set changes" should {
    "return the failed record set changes" in {
      val rsChangeFailed1 = rsChange1.copy(status = RecordSetChangeStatus.Failed)
      val rsChangeFailed2 = rsChange2.copy(status = RecordSetChangeStatus.Failed)

      Get(s"/metrics/health/recordsetchangesfailure") ~> recordSetRoute ~> check {
        val changes = responseAs[ListFailedRecordSetChangesResponse]
        changes.failedRecordSetChanges.map(_.id) shouldBe List(rsChangeFailed1.id, rsChangeFailed2.id)

      }
    }
  }

  "GET recordset" should {
    "return the recordset summary info" in {
      Get(s"/zones/${okZone.id}/recordsets/${rsOk.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[GetRecordSetResponse].recordSet
        resultRs.id shouldBe rsOk.id
        resultRs.ownerGroupId shouldBe None
        resultRs.ownerGroupName shouldBe None
      }
    }

    "return a 404 Not Found when the record set doesn't exist" in {
      Get(s"/zones/${okZone.id}/recordsets/${rsNotFound.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 404 Not Found when the zone doesn't exist" in {
      Get(s"/zones/${zoneNotFound.id}/recordsets/${rsZoneNotFound.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "PUT recordset" should {
    "save the changes to the recordset" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsOk.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsOk))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Accepted

        val change = responseAs[RecordSetChange]
        change.changeType shouldBe RecordSetChangeType.Update
        change.status shouldBe RecordSetChangeStatus.Complete
        Option(change.created) shouldBe defined
        change.userId shouldBe "ok"

        val resultRs = change.recordSet
        resultRs.updated shouldBe defined
      }
    }

    "return a 404 Not Found when the record set doesn't exist" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsNotFound.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsNotFound))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 404 Not Found when the zone doesn't exist" in {
      Put(s"/zones/${zoneNotFound.id}/recordsets/${rsZoneNotFound.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsZoneNotFound))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 409 Conflict when the update conflicts with an existing recordset" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsAlreadyExists.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsAlreadyExists))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 400 BadRequest when the update is for a deleted zone" in {
      Put(s"/zones/${zoneDeleted.id}/recordsets/${rsZoneDeleted.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsZoneDeleted))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 409 Conflict when the update conflicts with a pending recordset" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsPendingUpdate.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsPendingUpdate))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 403 Forbidden when the update is not authorized" in {
      Put(s"/zones/${notAuthorizedZone.id}/recordsets/${rsNotAuthorized.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsNotAuthorized))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "return a 409 Conflict when the zone is syncing" in {
      Put(s"/zones/${syncingZone.id}/recordsets/${rsZoneSyncing.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsZoneSyncing))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 422 Unprocessable Entity when the request is invalid" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsInvalidRequest.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(rsInvalidRequest))) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.UnprocessableEntity
      }
    }

    "return appropriate errors for missing information" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsOk.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(rsMissingData)))) ~>
        Route.seal(recordSetRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing RecordSet.zoneId",
          "Missing RecordSet.name",
          "Missing RecordSet.type",
          "Missing RecordSet.ttl"
        )
      }
    }

    "return a 422 Unprocessable Entity if the zoneId does not match the one in the route" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsOk.id}")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            rsJson(rsOk.copy(zoneId = invalidChangeZone.id))
          )
        ) ~>
        recordSetRoute ~> check {
        status shouldBe StatusCodes.UnprocessableEntity
        val error = responseAs[String]
        error shouldBe "Cannot update RecordSet's zoneId attribute"
      }
    }

    "return appropriate errors for invalid information" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsOk.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(rsInvalidType)))) ~>
        Route.seal(recordSetRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set("Invalid RecordType")
      }
    }

    "return appropriate errors for correct metadata but invalid records" in {
      Put(s"/zones/${okZone.id}/recordsets/${rsOk.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, compact(render(rsInvalidRecord)))) ~>
        Route.seal(recordSetRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set("Missing A.address")
      }
    }

    "return appropriate errors for CNAME record set with multiple records" in {
      Put(s"/zones/${okZone.id}/recordsets/${invalidCname.id}")
        .withEntity(HttpEntity(ContentTypes.`application/json`, rsJson(invalidCname))) ~>
        Route.seal(recordSetRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set("CNAME record sets cannot contain multiple records")
      }
    }
  }

  "GET recordsets" should {
    "return all recordsets" in {
      Get(s"/recordsets?recordNameFilter=rs*") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListGlobalRecordSetsResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs1.id, rs2.id, rs3.id)
      }
    }

    "return all recordsets in descending order" in {
      Get(s"/recordsets?recordNameFilter=rs*&nameSort=desc") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListGlobalRecordSetsResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs3.id, rs2.id, rs1.id)
      }
    }

    "return recordsets of a specific type" in {
      Get(s"/recordsets?recordNameFilter=rs*&recordTypeFilter=cname") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListGlobalRecordSetsResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs4.id)
      }
    }

    "return all recordsets if given an invalid record type" in {
      Get(s"/recordsets?recordNameFilter=rs*&recordTypeFilter=FAKE") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListGlobalRecordSetsResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs1.id, rs2.id, rs3.id)
      }
    }

    "return all recordsets of a specific owner group" in {
      Get(s"/recordsets?recordNameFilter=rs*&recordOwnerGroupFilter=my-group") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListGlobalRecordSetsResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs2.id)
      }
    }
  }

  "GET recordsets by zone" should {
    "return all recordsets" in {
      Get(s"/zones/${okZone.id}/recordsets") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs1.id, rs2.id, rs3.id)
      }
    }

    "return all recordsets in descending order" in {
      Get(s"/zones/${okZone.id}/recordsets?nameSort=desc") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs3.id, rs2.id, rs1.id)
      }
    }

    "return all recordSets types in descending order" in {

      Get(s"/zones/${okZone.id}/recordsets?recordTypeSort=desc") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK

        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.typ) shouldBe List(soa.typ, cname.typ, aaaa.typ))
      }
    }

    "return all recordSets types in ascending order" in {

      Get(s"/zones/${okZone.id}/recordsets?recordTypeSort=asc") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK

        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.typ) shouldBe List(aaaa.typ, cname.typ, soa.typ))

      }
    }

    "return all record name in ascending order when name and type sort simultaneously" in {

      Get(s"/zones/${okZone.id}/recordsets?nameSort=desc&recordTypeSort=asc") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK

        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.name) shouldBe List(aaaa.name, cname.name, soa.name))

      }
    }

    "return recordsets of a specific type" in {
      Get(s"/zones/${okZone.id}/recordsets?recordTypeFilter=cname") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs4.id)
      }
    }

    "return all recordsets if given an invalid record type" in {
      Get(s"/zones/${okZone.id}/recordsets?recordTypeFilter=FAKE") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.OK
        val resultRs = responseAs[ListRecordSetsByZoneResponse]
        (resultRs.recordSets.map(_.id) should contain)
          .only(rs1.id, rs2.id, rs3.id)
      }
    }

    "return a 404 Not Found when the zone doesn't exist" in {
      Get(s"/zones/${zoneNotFound.id}/recordsets") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "DELETE recordset" should {
    "delete the recordset" in {
      Delete(s"/zones/${okZone.id}/recordsets/${rsOk.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Accepted
        val change = responseAs[RecordSetChange]
        change.changeType shouldBe RecordSetChangeType.Delete
        change.status shouldBe RecordSetChangeStatus.Complete
        Option(change.created) shouldBe defined
        change.userId shouldBe "ok"

        val rs = change.recordSet
        validateRecordSet(rsOk, rs)
      }
    }

    "return a 404 Not Found when the record set doesn't exist" in {
      Delete(s"/zones/${okZone.id}/recordsets/${rsNotFound.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 404 Not Found when the zone doesn't exist" in {
      Delete(s"/zones/${zoneNotFound.id}/recordsets/${rsOk.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 400 BadRequest when the delete is for a deleted zone" in {
      Delete(s"/zones/${zoneDeleted.id}/recordsets/${rsZoneDeleted.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 403 Forbidden when the delete is not authorized" in {
      Delete(s"/zones/${notAuthorizedZone.id}/recordsets/${rsOk.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "return a 409 Conflict when the zone is syncing" in {
      Delete(s"/zones/${syncingZone.id}/recordsets/${rsZoneSyncing.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 422 Unprocessable Entity when the request is invalid" in {
      Delete(s"/zones/${okZone.id}/recordsets/${rsInvalidRequest.id}") ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.UnprocessableEntity
      }
    }
  }

  "POST recordset" should {
    "return 202 Accepted when the the recordset is created" in {
      post(rsOk) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Accepted
      }
    }

    "return a 404 NOT FOUND if the zone does not exist" in {
      post(rsZoneNotFound) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return 409 Conflict when adding a record set with an existing name and type" in {
      post(rsAlreadyExists) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 400 BadRequest when the create is for a deleted zone" in {
      post(rsZoneDeleted) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 403 Forbidden when the create is not authorized" in {
      post(rsNotAuthorized) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "return a 409 Conflict when the zone is syncing" in {
      post(rsZoneSyncing) ~> recordSetRoute ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return an error if the ttl is too small" in {
      validateErrors(
        rsInvalidTTL,
        "RecordSet.ttl must be a positive signed 32 bit number greater than or equal to 30"
      )
    }

    "return appropriate errors for missing information" in {
      validateErrors(
        rsMissingData,
        "Missing RecordSet.zoneId",
        "Missing RecordSet.name",
        "Missing RecordSet.type",
        "Missing RecordSet.ttl"
      )
    }

    "return appropriate errors for invalid information" in {
      validateErrors(rsInvalidType, "Invalid RecordType")
    }

    "return appropriate errors for correct metadata but invalid records" in {
      validateErrors(rsInvalidRecord, "Missing A.address")
    }

    "returns errors for missing records" in {
      validateErrors(testRecordBase(RecordType.A, JNothing), "Missing A Records")
      validateErrors(testRecordBase(RecordType.AAAA, JNothing), "Missing AAAA Records")
      validateErrors(testRecordBase(RecordType.CNAME, JNothing), "Missing CNAME Records")
      validateErrors(testRecordBase(RecordType.MX, JNothing), "Missing MX Records")
      validateErrors(testRecordBase(RecordType.NS, JNothing), "Missing NS Records")
      validateErrors(testRecordBase(RecordType.PTR, JNothing), "Missing PTR Records")
      validateErrors(testRecordBase(RecordType.SOA, JNothing), "Missing SOA Records")
      validateErrors(testRecordBase(RecordType.SPF, JNothing), "Missing SPF Records")
      validateErrors(testRecordBase(RecordType.SRV, JNothing), "Missing SRV Records")
      validateErrors(testRecordBase(RecordType.NAPTR, JNothing), "Missing NAPTR Records")
      validateErrors(testRecordBase(RecordType.SSHFP, JNothing), "Missing SSHFP Records")
      validateErrors(testRecordBase(RecordType.TXT, JNothing), "Missing TXT Records")
    }

    "returns an error for multiple records in a CNAME record set" in {
      validateErrors(
        render(Extraction.decompose(invalidCname)),
        "CNAME record sets cannot contain multiple records"
      )
    }

    "supports A" in {
      validateCreateRecordType(rsOk)
    }

    "return errors for A record missing data" in {
      validateErrors(testRecordType(RecordType.A, "key" -> "val"), "Missing A.address")
    }

    "return errors for invalid A record data" in {
      validateErrors(
        testRecordType(RecordType.A, "address" -> "invalid"),
        "A must be a valid IPv4 Address"
      )
    }

    "supports AAAA" in {
      validateCreateRecordType(aaaa)
    }

    "return errors for AAAA record missing data" in {
      validateErrors(testRecordType(RecordType.AAAA, "key" -> "val"), "Missing AAAA.address")
    }

    "return errors for invalid AAAA record data" in {
      validateErrors(
        testRecordType(RecordType.AAAA, "address" -> "invalid"),
        "AAAA must be a valid IPv6 Address"
      )
    }

    "supports CNAME" in {
      validateCreateRecordType(cname)
    }

    "return errors for CNAME record missing data" in {
      validateErrors(testRecordType(RecordType.CNAME, "key" -> "val"), "Missing CNAME.cname")
    }

    "return errors for invalid CNAME record data" in {
      val data = "a." + Random.alphanumeric.take(260).mkString
      validateErrors(
        testRecordType(RecordType.CNAME, "cname" -> data),
        "CNAME domain name must not exceed 255 characters"
      )
    }

    "supports MX" in {
      validateCreateRecordType(mx)
    }

    "return errors for MX record missing data" in {
      validateErrors(
        testRecordType(RecordType.MX, "key" -> "val"),
        "Missing MX.preference",
        "Missing MX.exchange"
      )
    }

    "return errors for invalid MX record data" in {
      validateErrors(
        testRecordType(
          RecordType.MX,
          ("exchange" -> Random.alphanumeric.take(260).mkString) ~~ ("preference" -> -1)
        ),
        "MX.preference must be a 16 bit integer",
        "MX.exchange must be less than 255 characters"
      )
      validateErrors(
        testRecordType(
          RecordType.MX,
          ("exchange" -> Random.alphanumeric.take(10).mkString) ~~ ("preference" -> 700000)
        ),
        "MX.preference must be a 16 bit integer"
      )
    }

    "supports NS" in {
      validateCreateRecordType(ns)
    }

    "return errors for NS record missing data" in {
      validateErrors(testRecordType(RecordType.NS, "key" -> "val"), "Missing NS.nsdname")
    }

    "return errors for invalid NS record data" in {
      val data = "a." + Random.alphanumeric.take(260).mkString
      validateErrors(
        testRecordType(RecordType.NS, "nsdname" -> data),
        "NS must be less than 255 characters"
      )
    }

    "supports PTR" in {
      validateCreateRecordType(ptr)
    }

    "return errors for PTR record missing data" in {
      validateErrors(testRecordType(RecordType.PTR, "key" -> "val"), "Missing PTR.ptrdname")
    }

    "return errors for invalid PTR record data" in {
      validateErrors(
        testRecordType(RecordType.PTR, "ptrdname" -> Random.alphanumeric.take(260).mkString),
        "PTR must be less than 255 characters"
      )
    }

    "supports SOA" in {
      validateCreateRecordType(soa)
    }

    "return errors for SOA record missing data" in {
      validateErrors(
        testRecordType(RecordType.SOA, "key" -> "val"),
        "Missing SOA.mname",
        "Missing SOA.rname",
        "Missing SOA.serial",
        "Missing SOA.refresh",
        "Missing SOA.retry",
        "Missing SOA.expire",
        "Missing SOA.minimum"
      )
    }

    "return errors for invalid SOA record data" in {
      validateErrors(
        testRecordType(
          RecordType.SOA,
          ("mname" -> Random.alphanumeric.take(260).mkString) ~~
            ("rname" -> Random.alphanumeric.take(260).mkString) ~~
            ("serial" -> 5000000000L) ~~
            ("refresh" -> 5000000000L) ~~
            ("retry" -> 5000000000L) ~~
            ("expire" -> 5000000000L) ~~
            ("minimum" -> 5000000000L)
        ),
        "SOA.mname must be less than 255 characters",
        "SOA.rname must be less than 255 characters",
        "SOA.serial must be an unsigned 32 bit number",
        "SOA.refresh must be an unsigned 32 bit number",
        "SOA.retry must be an unsigned 32 bit number",
        "SOA.expire must be an unsigned 32 bit number",
        "SOA.minimum must be an unsigned 32 bit number"
      )
      validateErrors(
        testRecordType(
          RecordType.SOA,
          ("mname" -> "mname") ~~
            ("rname" -> "rname") ~~
            ("serial" -> -1) ~~
            ("refresh" -> -1) ~~
            ("retry" -> -1) ~~
            ("expire" -> -1) ~~
            ("minimum" -> -1)
        ),
        "SOA.serial must be an unsigned 32 bit number",
        "SOA.refresh must be an unsigned 32 bit number",
        "SOA.retry must be an unsigned 32 bit number",
        "SOA.expire must be an unsigned 32 bit number",
        "SOA.minimum must be an unsigned 32 bit number"
      )
    }

    "supports SPF" in {
      validateCreateRecordType(spf)
    }

    "return errors for SPF record missing data" in {
      validateErrors(testRecordType(RecordType.SPF, "key" -> "val"), "Missing SPF.text")
    }

    "return errors for invalid SPF record data" in {
      validateErrors(
        testRecordType(RecordType.SPF, "text" -> Random.alphanumeric.take(70000).mkString),
        "SPF record must be less than 64764 characters"
      )
    }

    "supports SRV" in {
      validateCreateRecordType(srv)
    }

    "return errors for SRV record missing data" in {
      validateErrors(
        testRecordType(RecordType.SRV, "key" -> "val"),
        "Missing SRV.priority",
        "Missing SRV.weight",
        "Missing SRV.port",
        "Missing SRV.target"
      )
    }

    "return errors for invalid SRV record data" in {
      validateErrors(
        testRecordType(
          RecordType.SRV,
          ("target" -> Random.alphanumeric.take(260).mkString) ~~
            ("priority" -> 50000000) ~~
            ("weight" -> 50000000) ~~
            ("port" -> 50000000)
        ),
        "SRV.priority must be an unsigned 16 bit number",
        "SRV.weight must be an unsigned 16 bit number",
        "SRV.port must be an unsigned 16 bit number",
        "SRV.target must be less than 255 characters"
      )
      validateErrors(
        testRecordType(
          RecordType.SRV,
          ("target" -> Random.alphanumeric.take(10).mkString) ~~
            ("priority" -> -1) ~~
            ("weight" -> -1) ~~
            ("port" -> -1)
        ),
        "SRV.priority must be an unsigned 16 bit number",
        "SRV.weight must be an unsigned 16 bit number",
        "SRV.port must be an unsigned 16 bit number"
      )
    }

    "supports NAPTR" in {
      validateCreateRecordType(naptr)
    }

    "return errors for NAPTR record missing data" in {
      validateErrors(
        testRecordType(RecordType.NAPTR, "key" -> "val"),
        "Missing NAPTR.order",
        "Missing NAPTR.preference",
        "Missing NAPTR.flags",
        "Missing NAPTR.service",
        "Missing NAPTR.regexp",
        "Missing NAPTR.replacement"
      )
    }

    "return errors for invalid NAPTR record data" in {
      val validFlags = List("U", "S", "A", "P")
      validateErrors(
        testRecordType(
          RecordType.NAPTR,
          ("replacement" -> Random.alphanumeric.take(260).mkString) ~~
            ("regexp" -> Random.alphanumeric.take(260).mkString) ~~
            ("service" -> Random.alphanumeric.take(260).mkString) ~~
            ("flags" -> Random.alphanumeric.take(2).mkString) ~~
            ("order" -> 50000000) ~~
            ("preference" -> 50000000)
        ),
        "NAPTR.order must be an unsigned 16 bit number",
        "NAPTR.preference must be an unsigned 16 bit number",
        "Invalid NAPTR.flag. Valid NAPTR flag value must be U, S, A or P",
        "NAPTR.service must be less than 255 characters",
        "Invalid NAPTR.regexp. Valid NAPTR regexp value must start and end with '!' or can be empty",
        "NAPTR.replacement must be less than 255 characters"
      )
      validateErrors(
        testRecordType(
          RecordType.NAPTR,
          ("regexp" -> "") ~~
            ("service" -> Random.alphanumeric.take(10).mkString) ~~
            ("replacement" -> Random.alphanumeric.take(10).mkString) ~~
            ("flags" -> validFlags.take(1).mkString) ~~
            ("order" -> -1) ~~
            ("preference" -> -1)
        ),
        "NAPTR.order must be an unsigned 16 bit number",
        "NAPTR.preference must be an unsigned 16 bit number"
      )
    }

    "supports SSHFP" in {
      validateCreateRecordType(sshfp)
    }

    "return errors for SSHFP record missing data" in {
      validateErrors(
        testRecordType(RecordType.SSHFP, "key" -> "val"),
        "Missing SSHFP.algorithm",
        "Missing SSHFP.type",
        "Missing SSHFP.fingerprint"
      )
    }

    "return errors for invalid SSHFP record data" in {
      validateErrors(
        testRecordType(
          RecordType.SSHFP,
          ("fingerprint" -> Random.alphanumeric.take(10).mkString) ~~
            ("algorithm" -> 50000000) ~~
            ("type" -> 50000000)
        ),
        "SSHFP.algorithm must be an unsigned 8 bit number",
        "SSHFP.type must be an unsigned 8 bit number"
      )
      validateErrors(
        testRecordType(
          RecordType.SSHFP,
          ("fingerprint" -> Random.alphanumeric.take(10).mkString) ~~
            ("algorithm" -> -1) ~~
            ("type" -> -1)
        ),
        "SSHFP.algorithm must be an unsigned 8 bit number",
        "SSHFP.type must be an unsigned 8 bit number"
      )
    }

    "supports TXT" in {
      validateCreateRecordType(txt)
    }

    "return errors for TXT record missing data" in {
      validateErrors(testRecordType(RecordType.TXT, "key" -> "val"), "Missing TXT.text")
    }

    "return errors for invalid TXT record data" in {
      validateErrors(
        testRecordType(RecordType.TXT, "text" -> Random.alphanumeric.take(70000).mkString),
        "TXT record must be less than 64764 characters"
      )
    }
  }
}
