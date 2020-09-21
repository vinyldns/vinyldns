package vinyldns.route53.backend

import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._
import org.scalatest.OptionValues._
import vinyldns.core.domain.{Fqdn, record}
import vinyldns.core.domain.record.{RecordSet, RecordType}

class Route53IntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  import vinyldns.core.TestRecordSetData._

  private val testZone = Zone("example.com.", "test@test.com", backendId = Some("test"))

  override def beforeAll(): Unit = {
    deleteZone()
    createZone()
  }

  private def testConnection: Route53BackendConnection =
    Route53BackendConnection
      .load(
        Route53ConnectionConfig("test", "access", "secret", "http://127.0.0.1:19009", "us-east-1")
      )
      .unsafeRunSync()

  private def deleteZone(): Unit = {
    val zoneIds = testConnection.client.listHostedZones().getHostedZones.asScala.map(_.getId).toList
    zoneIds.foreach { id =>
      testConnection.client.deleteHostedZone(new DeleteHostedZoneRequest().withId(id))
    }
  }

  private def createZone(): Unit =
    testConnection.createZone(testZone).unsafeRunSync()

  private def checkRecordExists(rs: RecordSet, zone: Zone): Unit = {
    val resolveResult =
      testConnection.resolve(rs.name, zone.name, rs.typ).unsafeRunSync().headOption.value
    resolveResult.records should contain theSameElementsAs rs.records
    resolveResult.name shouldBe rs.name
    resolveResult.ttl shouldBe rs.ttl
    resolveResult.typ shouldBe rs.typ
  }

  private def checkRecordNotExists(rs: RecordSet, zone: Zone): Unit =
    testConnection.resolve(rs.name, zone.name, rs.typ).unsafeRunSync() shouldBe empty

  private def testRecordSet(rs: RecordSet, zone: Zone): Unit = {
    val conn = testConnection
    val testRecord = rs.copy(zoneId = zone.id)
    val change = makeTestAddChange(testRecord, zone, "test-user")
    val result = conn.applyChange(change).unsafeRunSync()
    result shouldBe Route53Response.NoError

    // We should be able to resolve now
    checkRecordExists(testRecord, zone)

    val del = makePendingTestDeleteChange(testRecord, zone, "test-user")
    conn.applyChange(del).unsafeRunSync()

    // Record should not be found
    checkRecordNotExists(testRecord, zone)
  }

  "Route53 Connections" should {
    "return nothing if the zone does not exist" in {
      testConnection.resolve("foo", "bar", RecordType.A).unsafeRunSync() shouldBe empty
    }
    "work for a" in {
      testRecordSet(rsOk, testZone)
    }
    "work for aaaa" in {
      testRecordSet(aaaa, testZone)
    }
    "work for cname" in {
      testRecordSet(cname, testZone)
    }
    "work for naptr" in {
      testRecordSet(naptr, testZone)
    }
    "work for mx" in {
      val testMxData = record.MXData(10, Fqdn("mx.example.com."))
      val testMx = mx.copy(records = List(testMxData))
      testRecordSet(testMx, testZone)
    }
    "work for txt" in {
      testRecordSet(txt, testZone)
    }
    "check if zone exists" in {
      val notFound = Zone("blah.foo.", "test@test.com", backendId = Some("test"))
      testConnection.zoneExists(notFound).unsafeRunSync() shouldBe false
      testConnection.zoneExists(testZone).unsafeRunSync() shouldBe true
    }
  }
}
