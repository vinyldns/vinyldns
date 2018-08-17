package vinyldns.api.repository.mysql

import vinyldns.api.VinylDNSConfig

object VinylDNSJDBCTestDb {

  val settings = VinylDNSConfig.dataStoreConfig
    .find(_.getString("type") == "vinyldns.api.repository.mysql.MySqlDataStore")
    .map(_.getConfig("settings"))

  lazy val instance: VinylDNSJDBC = new VinylDNSJDBC(settings.get)
}
