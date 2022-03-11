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

package vinyldns.mysql

import cats.effect.IO
import org.mockito.Matchers.any
import org.mockito.Mockito.doReturn
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import scalikejdbc._
import vinyldns.mysql.repository.MySqlMembershipRepository

class MySqlTransactionProviderUnitSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with TransactionProvider {

  private val membershipRepo = mock[MySqlMembershipRepository]
  private val RETRIES = 3

  def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM membership")
      ()
    }

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  def saveMembersData(
   repo: MySqlMembershipRepository,
   groupId: String,
   userIds: Set[String],
   isAdmin: Boolean
  ): IO[Set[String]] = {
    executeWithinTransaction { db: DB =>
      repo.saveMembers(db, groupId, userIds, isAdmin)
    }
  }

  // Add connection to run test
  ConnectionPool.add('default, "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'","sa","")

  "retry should execute any code inside successfully if there's no exception" in {
    retry(RETRIES){ 1 + 1 } shouldBe 2
  }

  "retry throws exception when the function inside has exception" in {
    val groupId = "group-id-1"
    val userIds = Set("user-id-1")
    doReturn(IO.raiseError(new RuntimeException()))
      .when(membershipRepo)
      .saveMembers(any[DB],any[String],any[Set[String]],any[Boolean])

    val execute = retry(RETRIES) {
      saveMembersData(membershipRepo, groupId, userIds, isAdmin = false)
    }
    a[RuntimeException] shouldBe thrownBy(execute.unsafeRunSync())
  }

  "retry executes the function inside successfully if it has no exception" in {
    val groupId = "group-id-1"
    val userIds = Set("user-id-1")
    doReturn(IO.pure(userIds))
      .when(membershipRepo)
      .saveMembers(any[DB],any[String],any[Set[String]],any[Boolean])

    val execute = retry(RETRIES) {
      saveMembersData(membershipRepo, groupId, userIds, isAdmin = false)
    }
    execute.unsafeRunSync() shouldBe userIds
  }

}

