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

package vinyldns.api.domain.record

import cats.effect.IO
import org.slf4j.LoggerFactory
import scalikejdbc.DB
import vinyldns.core.domain.record.{ListRecordSetResults, NameSort, RecordSetCacheRepository, RecordSetRepository, RecordTypeSort}
import vinyldns.mysql.TransactionProvider


class RecordSetCacheService(recordSetRepository: RecordSetRepository,
                            recordSetCacheRepository: RecordSetCacheRepository) extends TransactionProvider {
  private val logger = LoggerFactory.getLogger(classOf[RecordSetCacheService])

  final def populateRecordSetCache(nextId: Option[String] = None): IO[ListRecordSetResults] = {
    logger.info(s"Populating recordset data. Starting at $nextId")
    for {
      result <- recordSetRepository.listRecordSets(None, nextId, Some(1000), None, None, None, NameSort.ASC, RecordTypeSort.ASC)

      _ <- executeWithinTransaction { db: DB =>
        IO {
          result.recordSets.par.foreach(recordSet => {
            recordSetCacheRepository.updateRecordDataList(db, recordSet.id, recordSet.records, recordSet.typ, recordSet.zoneId, recordSet.fqdn.get)
          })
        }
      }
      _ <- populateRecordSetCache(result.nextId)
    } yield {
      result
    }
  }
}
