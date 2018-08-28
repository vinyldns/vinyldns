package vinyldns.api.repository.mysql

import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.zone.ZoneRepository
import vinyldns.api.repository.DataStore

object TestMySqlInstance {
  lazy val instance: DataStore =
    new MySqlDataStoreProvider().load(VinylDNSConfig.mySqlConfig).unsafeRunSync()
  lazy val zoneRepository: ZoneRepository = instance.zoneRepository.get
  lazy val batchChangeRepository: BatchChangeRepository = instance.batchChangeRepository.get
}
