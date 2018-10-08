package vinyldns.mysql.repository

import org.scalatest.WordSpec
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import vinyldns.core.domain.zone.{Zone, ZoneRepository}

class MySqlZoneRepositorySpec
  extends WordSpec
  with MockitoSugar {
  private var repo: MySqlZoneRepository = mock[MySqlZoneRepository]

  "MySqlZoneRepository.save" should {
    "only call retryIOWithBackoff once if save is successful" in {
      val saveResponse = mock[Zone]
      doReturn(saveResponse)
        .when(repo).saveTx()
    }
  }

}
