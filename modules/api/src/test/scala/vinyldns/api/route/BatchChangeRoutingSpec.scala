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
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.data.EitherT
import cats.implicits._
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.domain.batch._
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._
import cats.effect._
import vinyldns.api.domain.BatchChangeIsEmpty
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch._

class BatchChangeRoutingSpec
    extends WordSpec
    with ScalatestRouteTest
    with BatchChangeRoute
    with JsonValidationRejection
    with VinylDNSDirectives
    with VinylDNSJsonProtocol
    with Matchers {

  val batchChangeService: BatchChangeServiceAlgebra = TestBatchChangeService
  val vinylDNSAuthenticator: VinylDNSAuthenticator = new TestVinylDNSAuthenticator(okAuth)

  import vinyldns.core.domain.batch.SingleChangeStatus._

  object TestData {
    import vinyldns.api.domain.batch.ChangeInputType._

    val batchChangeLimit = 1000

    /* Builds BatchChange response */
    def createBatchChangeResponse(
        comments: Option[String] = None,
        ownerGroupId: Option[String] = None,
        auth: AuthPrincipal = okAuth): BatchChange =
      BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        comments,
        DateTime.now,
        List(
          SingleAddChange(
            Some("zoneId"),
            Some("zoneName"),
            Some("recordName"),
            "fqdn",
            A,
            3600,
            AData("1.1.1.1"),
            Pending,
            Some("systemMessage"),
            None,
            None,
            "singleAddChangeId"),
          SingleDeleteChange(
            Some("zoneId"),
            Some("zoneName"),
            Some("recordName"),
            "fqdn",
            A,
            Pending,
            Some("systemMessage"),
            None,
            None,
            "singleDeleteChangeId")
        ),
        ownerGroupId,
        BatchChangeApprovalStatus.AutoApproved,
        None,
        None,
        None,
        "batchId"
      )

    /* Builds BatchChange response */
    def createBatchChangeInfoResponse(
        batchChange: BatchChange,
        ownerGroupName: Option[String] = None): BatchChangeInfo =
      BatchChangeInfo(batchChange, ownerGroupName)

    def buildAddChangeInput(
        inputName: Option[String] = None,
        typ: Option[RecordType] = None,
        ttl: Option[Int] = None,
        record: Option[RecordData] = None): JObject =
      JObject(
        List(
          inputName.map("inputName" -> JString(_)),
          typ.map("type" -> Extraction.decompose(_)),
          ttl.map("ttl" -> JInt(_)),
          record.map("record" -> Extraction.decompose(_))).flatten)

    def buildDeleteChangeInput(
        inputName: Option[String] = None,
        typ: Option[RecordType] = None): JObject =
      JObject(List(
        inputName.map("inputName" -> JString(_)),
        typ.map("type" -> Extraction.decompose(_))).flatten)

    val addAChangeInput: JObject =
      buildAddChangeInput(Some("bar."), Some(A), Some(3600), Some(AData("127.0.0.1")))
    val deleteAChangeInput: JObject = buildDeleteChangeInput(Some("bar."), Some(A))

    val changeList: JObject = "changes" -> List(
      ("changeType" -> Extraction.decompose(Add)) ~~ addAChangeInput,
      ("changeType" -> Extraction.decompose(Add)) ~~ addAChangeInput,
      ("changeType" -> Extraction.decompose(DeleteRecordSet)) ~~ deleteAChangeInput,
      ("changeType" -> Extraction.decompose(DeleteRecordSet)) ~~ deleteAChangeInput
    )

    def buildValidBatchChangeInputJson(comments: String): String =
      compact(render(("comments" -> comments) ~~ changeList))

    val batchChangeSummaryInfo1 = BatchChangeSummary(createBatchChangeResponse(Some("first")))
    val batchChangeSummaryInfo2 = BatchChangeSummary(createBatchChangeResponse(Some("second")))
    val batchChangeSummaryInfo3 = BatchChangeSummary(createBatchChangeResponse(Some("third")))
    val batchChangeSummaryInfo4 = BatchChangeSummary(
      createBatchChangeResponse(Some("fourth"), auth = dummyAuth))

    val validResponseWithComments: BatchChange = createBatchChangeResponse(
      Some("validChangeWithComments"))
    val validResponseWithoutComments: BatchChange = createBatchChangeResponse()
    val validResponseWithOwnerGroupId: BatchChange =
      createBatchChangeResponse(ownerGroupId = Some("some-group-id"))
    val genericValidResponse: BatchChange = createBatchChangeResponse(
      Some("generic valid response"))

    val backwardsCompatibleDel = SingleDeleteChange(
      None,
      None,
      None,
      "fqdn.two",
      A,
      Pending,
      Some("systemMessage"),
      None,
      None,
      "singleDeleteChangeId")
    val backwardsCompatibleAdd = SingleDeleteChange(
      None,
      None,
      None,
      "fqdn.two",
      A,
      Pending,
      Some("systemMessage"),
      None,
      None,
      "singleDeleteChangeId")

    val backwardsCompatable: BatchChange = genericValidResponse.copy(
      id = "testBwComp",
      changes = List(backwardsCompatibleAdd, backwardsCompatibleDel))

    val validListBatchChangeSummariesResponse: BatchChangeSummaryList = BatchChangeSummaryList(
      List(BatchChangeSummary(createBatchChangeResponse(None))))
  }

  import TestData._

  object TestBatchChangeService extends BatchChangeServiceAlgebra {
    def applyBatchChange(
        batchChangeInput: BatchChangeInput,
        auth: AuthPrincipal): EitherT[IO, BatchChangeErrorResponse, BatchChange] =
      batchChangeInput.comments match {
        case Some("validChangeWithComments") =>
          EitherT[IO, BatchChangeErrorResponse, BatchChange](
            IO.pure(Right(validResponseWithComments)))
        case None =>
          EitherT[IO, BatchChangeErrorResponse, BatchChange](
            IO.pure(Right(validResponseWithoutComments)))
        case Some("runtimeException") =>
          throw new RuntimeException("Unexpected run-time exception has occurred!")
        case Some("emptyBatch") =>
          EitherT[IO, BatchChangeErrorResponse, BatchChange](
            IO.pure(Left(InvalidBatchChangeInput(List(BatchChangeIsEmpty(batchChangeLimit)))))
          )
        case Some("validChangeWithOwnerGroup") =>
          EitherT[IO, BatchChangeErrorResponse, BatchChange](
            IO.pure(Right(validResponseWithOwnerGroupId)))
        case Some(_) =>
          EitherT[IO, BatchChangeErrorResponse, BatchChange](IO.pure(Right(genericValidResponse)))
      }

    def getBatchChange(
        id: String,
        auth: AuthPrincipal): EitherT[IO, BatchChangeErrorResponse, BatchChangeInfo] =
      id match {
        case "batchId" =>
          EitherT(
            IO.pure(createBatchChangeInfoResponse(genericValidResponse).asRight)
          )
        case "nonexistentID" =>
          EitherT(IO.pure(BatchChangeNotFound("nonexistentID").asLeft))
        case "notAuthedID" =>
          EitherT(IO.pure(UserNotAuthorizedError("notAuthedID").asLeft))
        case backwardsCompatable.id =>
          EitherT(IO.pure(createBatchChangeInfoResponse(backwardsCompatable).asRight))
      }

    def listBatchChangeSummaries(
        auth: AuthPrincipal,
        startFrom: Option[Int],
        maxItems: Int,
        listAll: Boolean = false): EitherT[IO, BatchChangeErrorResponse, BatchChangeSummaryList] =
      if (auth.userId == okAuth.userId)
        (auth, startFrom, maxItems, listAll) match {
          case (_, None, 100, false) =>
            EitherT.rightT(
              BatchChangeSummaryList(
                batchChanges =
                  List(batchChangeSummaryInfo1, batchChangeSummaryInfo2, batchChangeSummaryInfo3),
                startFrom = None,
                nextId = None
              )
            )

          case (_, None, 1, false) =>
            EitherT.rightT(
              BatchChangeSummaryList(
                batchChanges = List(batchChangeSummaryInfo1),
                startFrom = None,
                nextId = Some(1),
                maxItems = 1
              )
            )

          case (_, Some(1), 100, false) =>
            EitherT.rightT(
              BatchChangeSummaryList(
                batchChanges = List(batchChangeSummaryInfo2),
                startFrom = Some(1),
                nextId = None
              )
            )

          case (_, Some(1), 1, false) =>
            EitherT.rightT(
              BatchChangeSummaryList(
                batchChanges = List(batchChangeSummaryInfo2),
                startFrom = Some(1),
                nextId = Some(2),
                maxItems = 1
              )
            )

          case (_) => EitherT.rightT(BatchChangeSummaryList(List()))
        } else if (auth.userId == superUserAuth.userId)
        (auth, startFrom, maxItems, listAll) match {
          case (_, None, 100, true) =>
            EitherT.rightT(
              BatchChangeSummaryList(
                batchChanges = List(
                  batchChangeSummaryInfo1,
                  batchChangeSummaryInfo2,
                  batchChangeSummaryInfo3,
                  batchChangeSummaryInfo4),
                startFrom = None,
                nextId = None,
                listAll = true
              )
            )
          case (_) => EitherT.rightT(BatchChangeSummaryList(List()))
        } else
        EitherT.rightT(BatchChangeSummaryList(List()))

    def rejectBatchChange(
        batchChangeId: String,
        authPrincipal: AuthPrincipal,
        rejectionComment: Option[RejectBatchChangeInput])
      : EitherT[IO, BatchChangeErrorResponse, BatchChange] =
      (batchChangeId, authPrincipal.isSystemAdmin) match {
        case ("pendingBatchId", true) => EitherT(IO.pure(genericValidResponse.asRight))
        case ("pendingBatchId", false) =>
          EitherT(IO.pure(UserNotAuthorizedError("notAuthedID").asLeft))
        case (_, _) => EitherT(IO.pure(BatchChangeNotPendingApproval("batchId").asLeft))
      }
  }

  "POST batch change" should {
    "return a 202 Accepted for valid add and delete request with comments" in {
      val validRequestWithComments: String =
        buildValidBatchChangeInputJson("validChangeWithComments")

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, validRequestWithComments)) ~>
        batchChangeRoute(okAuth) ~> check {

        status shouldBe Accepted

        val change = responseAs[JValue]
        compact(change) shouldBe compact(Extraction.decompose(validResponseWithComments))
      }
    }

    "return a 202 Accepted for valid add and delete request without comments" in {
      val validRequestWithoutComments: String = compact(render(changeList))

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, validRequestWithoutComments)) ~>
        batchChangeRoute(okAuth) ~> check {

        status shouldBe Accepted

        val change = responseAs[JValue]
        compact(change) shouldBe compact(Extraction.decompose(validResponseWithoutComments))
      }
    }

    "return a 202 Accepted for valid add and delete request with owner group ID" in {
      val validRequestWithOwnerGroupId: String =
        compact(
          render(("comments" -> "validChangeWithOwnerGroup") ~~ changeList ~~
            ("ownerGroupId" -> "some-group-id")))

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, validRequestWithOwnerGroupId)) ~>
        batchChangeRoute(sharedAuth) ~> check {
        status shouldBe Accepted

        val change = responseAs[JValue]
        compact(change) shouldBe compact(Extraction.decompose(validResponseWithOwnerGroupId))
      }
    }

    "return a 400 BadRequest for empty batch" in {
      val emptyBatchRequest: String =
        """{"comments": "emptyBatch"
          |}""".stripMargin

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, emptyBatchRequest)) ~>
        Route.seal(batchChangeRoute(okAuth)) ~> check {

        status shouldBe BadRequest
      }
    }

    "return a 400 BadRequest for invalid requests" in {
      val invalidRequestChangeType: String =
        """{"comments": "hey",
          | "changes": [{
          |   "changeType": "UnknownChangeType"
          | }]
          |}""".stripMargin

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, invalidRequestChangeType)) ~>
        Route.seal(batchChangeRoute(okAuth)) ~> check {

        status shouldBe BadRequest
      }
    }

    "return a 500 InternalServerError if an unhandled error is encountered" in {
      val runtimeError: String = buildValidBatchChangeInputJson("runtimeException")

      Post("/zones/batchrecordchanges").withEntity(
        HttpEntity(ContentTypes.`application/json`, runtimeError)) ~>
        Route.seal(batchChangeRoute(okAuth)) ~> check {

        status shouldBe InternalServerError
      }
    }
  }

  "GET Batch Change Info" should {
    "return the batch change info given a valid batch change id" in {
      Get(s"/zones/batchrecordchanges/${genericValidResponse.id}") ~> batchChangeRoute(okAuth) ~> check {

        status shouldBe OK

        val resp = responseAs[JValue]
        compact(resp) shouldBe compact(
          Extraction.decompose(createBatchChangeInfoResponse(genericValidResponse)))
      }
    }

    "maintain backwards compatability for zoneName/recordName/zoneId" in {
      Get(s"/zones/batchrecordchanges/${backwardsCompatable.id}") ~> batchChangeRoute(okAuth) ~> check {

        status shouldBe OK

        val resp = responseAs[JValue]
        val asString = compact(resp)

        asString should include("zoneName\":\"\"")
        asString should include("recordName\":\"\"")
        asString should include("zoneId\":\"\"")
      }
    }

    "return a NotFound error given a nonexistent batch change id" in {
      Get("/zones/batchrecordchanges/nonexistentID") ~> batchChangeRoute(okAuth) ~> check {

        status shouldBe NotFound
      }
    }

    "return a Forbidden error if user did not create the batch change" in {
      Get("/zones/batchrecordchanges/notAuthedID") ~> batchChangeRoute(notAuth) ~> check {

        status shouldBe Forbidden
      }
    }
  }

  "GET batchChangesSummaries" should {
    "return the list of batch change summaries for the user that called it" in {
      Get("/zones/batchrecordchanges") ~> batchChangeRoute(okAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[BatchChangeSummaryList]

        resp.batchChanges.length shouldBe 3
        resp.maxItems shouldBe 100
        resp.startFrom shouldBe None
        resp.nextId shouldBe None
      }
    }

    "return the first batch change summary for the user that called it" in {
      Get("/zones/batchrecordchanges?maxItems=1") ~> batchChangeRoute(okAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[BatchChangeSummaryList]

        resp.batchChanges.length shouldBe 1
        resp.maxItems shouldBe 1
        resp.startFrom shouldBe None
        resp.nextId shouldBe Some(1)
      }
    }

    "return an offset of the batch change summaries for the user that called it" in {
      Get("/zones/batchrecordchanges?startFrom=1") ~> batchChangeRoute(okAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[BatchChangeSummaryList]

        resp.batchChanges.length shouldBe 1
        resp.maxItems shouldBe 100
        resp.startFrom shouldBe Some(1)
        resp.nextId shouldBe None
      }
    }

    "return only the second batch change summary for the user that called it" in {
      Get("/zones/batchrecordchanges?startFrom=1&maxItems=1") ~> batchChangeRoute(okAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[BatchChangeSummaryList]

        resp.batchChanges.length shouldBe 1
        resp.maxItems shouldBe 1
        resp.startFrom shouldBe Some(1)
        resp.nextId shouldBe Some(2)
      }
    }

    "return an error if maxItems is out of range" in {
      Get("/zones/batchrecordchanges?startFrom=1&maxItems=101") ~> batchChangeRoute(okAuth) ~> check {
        status shouldBe BadRequest

        responseEntity.toString should include(
          "maxItems was 101, maxItems must be between 1 and 100, inclusive.")
      }
    }

    "return empty list of batch change summaries for the user that called it" in {
      Get("/zones/batchrecordchanges") ~> batchChangeRoute(notAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[JValue]
        compact(resp) shouldBe compact(
          Extraction.decompose(BatchChangeSummaryList(List(), maxItems = 100)))
      }
    }

    "return all batch changes if listAll is true" in {
      Get("/zones/batchrecordchanges?listAll=true") ~> batchChangeRoute(superUserAuth) ~> check {
        status shouldBe OK

        val resp = responseAs[BatchChangeSummaryList]

        resp.batchChanges.length shouldBe 4
        resp.maxItems shouldBe 100
        resp.startFrom shouldBe None
        resp.nextId shouldBe None
      }
    }
  }

  "POST reject batch change" should {
    "return OK if review comment is provided, batch change is PendingApproval, and reviewer is authorized" in {
      Post("/zones/batchrecordchanges/pendingBatchId/reject").withEntity(HttpEntity(
        ContentTypes.`application/json`,
        compact(render("comments" -> "some comments")))) ~>
        batchChangeRoute(supportUserAuth) ~> check {
        status shouldBe OK
      }
    }

    "return OK if comments are not provided, batch change is PendingApproval, and reviewer is authorized" in {
      Post("/zones/batchrecordchanges/pendingBatchId/reject").withEntity(
        HttpEntity(ContentTypes.`application/json`, compact(render("")))) ~>
        batchChangeRoute(supportUserAuth) ~> check {
        status shouldBe OK
      }
    }

    "return Forbidden if user is not a super or support admin" in {
      Post("/zones/batchrecordchanges/pendingBatchId/reject").withEntity(
        HttpEntity(ContentTypes.`application/json`, compact(render("")))) ~>
        batchChangeRoute(okAuth) ~> check {
        status shouldBe Forbidden
      }
    }

    "return BadRequest if comments exceed 1024 characters" in {
      Post("/zones/batchrecordchanges/pendingBatchId/reject").withEntity(HttpEntity(
        ContentTypes.`application/json`,
        compact(render("reviewComment" -> "a" * 1025)))) ~> Route.seal(batchChangeRoute(okAuth)) ~> check {
        status shouldBe BadRequest

        responseEntity.toString should include("Comment length must not exceed 1024 characters.")
      }
    }

    "return OK no request entity is provided" in {
      Post("/zones/batchrecordchanges/pendingBatchId/reject") ~> batchChangeRoute(supportUserAuth) ~> check {
        status shouldBe OK
      }
    }

    "return BadRequest if batch change is not pending approval" in {
      Post("/zones/batchrecordchanges/batchId/reject").withEntity(
        HttpEntity(ContentTypes.`application/json`, compact(render("")))) ~>
        batchChangeRoute(supportUserAuth) ~> check {
        status shouldBe BadRequest
      }
    }
  }
}
