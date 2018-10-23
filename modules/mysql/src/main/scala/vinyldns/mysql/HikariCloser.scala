package vinyldns.mysql

import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.DataSourceCloser

class HikariCloser(dataSource: HikariDataSource) extends DataSourceCloser {
  override def close(): Unit = dataSource.close()
}
