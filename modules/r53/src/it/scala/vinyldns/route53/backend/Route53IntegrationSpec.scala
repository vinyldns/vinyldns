package vinyldns.route53.backend

import com.amazonaws.services.route53.model.DeleteHostedZoneRequest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._

class Route53IntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  import vinyldns.core.TestRecordSetData._

  override def beforeEach(): Unit =
    clear()

  private def testConnection: Route53BackendConnection =
    Route53BackendConnection
      .load(
        Route53ConnectionConfig("test", "access", "secret", "http://127.0.0.1:19009", "us-east-1")
      )
      .unsafeRunSync()

  private def clear(): Unit = {
    val zoneIds = testConnection.client.listHostedZones().getHostedZones.asScala.map(_.getId).toList
    println("\r\n!!! clearing hosted zones...")
    zoneIds.foreach { id =>
      println(id)
      testConnection.client.deleteHostedZone(new DeleteHostedZoneRequest().withId(id))
    }
  }

  "Route53 Connections" should {
    "resolve" in {
      val conn = testConnection
      val zone = Zone("example.com.", "test@test.com", backendId = Some("test"))

      println("\r\n!!! creating zone ...")
      conn.createZone(zone).unsafeRunSync()

      // Let's add a record to our zone
      val change = makeTestAddChange(aaaa.copy(zoneId = zone.id), zone, "test-user")
      val result = conn.applyChange(change).unsafeRunSync()
      println(result)
      result shouldBe Route53Response.NoError

      // We should be able to resolve now
      val resolveResult = conn.resolve(aaaa.name, zone.name, aaaa.typ).unsafeRunSync()
      println(resolveResult)
      resolveResult.headOption shouldBe defined

      // Can we look up the SOA?
      val soaResult = conn.resolve(zone.name, zone.name, RecordType.SOA).unsafeRunSync()
      println(soaResult)
    }
  }
}
