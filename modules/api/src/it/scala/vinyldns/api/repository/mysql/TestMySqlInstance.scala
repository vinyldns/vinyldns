package vinyldns.api.repository.mysql

import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.zone.ZoneRepository
import vinyldns.api.repository.DataStore
import vinyldns.api.repository.RepositoryName._

object TestMySqlInstance {
  lazy val instance: DataStore =
    new MySqlDataStoreProvider().load(VinylDNSConfig.mySqlConfig).unsafeRunSync()

  lazy val zoneRepository: ZoneRepository = instance.get[ZoneRepository](zone).get
  lazy val batchChangeRepository: BatchChangeRepository =
    instance.get[BatchChangeRepository](batchChange).get
}
