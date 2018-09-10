package vinyldns.api.repository.mysql

import org.scalatest._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.zone.ZoneChangeRepository

class MySqlZoneChangeRepositoryIntegrationSpec
  extends WordSpec
    with BeforeAndAfterAll
    with DnsConversions
    with VinylDNSTestData
    with GroupTestData
    with ResultHelpers
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with Inspectors
    with OptionValues {

  private var repo: ZoneChangeRepository = _
  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  object TestData {

  }
}
