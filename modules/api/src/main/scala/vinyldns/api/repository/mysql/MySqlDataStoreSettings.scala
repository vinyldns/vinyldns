package vinyldns.api.repository.mysql

case class MySqlDataStoreSettings(
                                   name: String,
                                   driver: String,
                                   migrationUrl: String,
                                   url: String,
                                   user: String,
                                   password: String,
                                   poolMaxSize: Int,
                                   connectionTimeoutMillis: Long,
                                   maxLifeTime: Long,
                                   migrationSchemaTable: Option[String])
