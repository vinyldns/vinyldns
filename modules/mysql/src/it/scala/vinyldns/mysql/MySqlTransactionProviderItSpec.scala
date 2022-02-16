package vinyldns.mysql

import cats.effect.IO
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc._
import vinyldns.mysql.repository.MySqlMembershipRepository

class MySqlTransactionProviderItSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with TransactionProvider {

  private val repo: MySqlMembershipRepository = TestMySqlInstance.membershipRepository
  private val RETRIES = 3

  def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM membership")
      ()
    }

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  private def getAllRecords: List[(String, String)] =
    DB.localTx { implicit s =>
      sql"SELECT user_id, group_id FROM membership"
        .map(res => Tuple2[String, String](res.string(1), res.string(2)))
        .list()
        .apply()
    }

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

  "retry should add a member successfully when there's no exception" in {
    val groupId = "group-id-1"
    val userIds = Set("user-id-1")
    retry(RETRIES) {
      val addResult = saveMembersData(repo, groupId, userIds, isAdmin = false).unsafeRunSync()
      addResult should contain theSameElementsAs userIds
    }

    val getAllResult = getAllRecords
    val expectedGetAllResult = Set(Tuple2(userIds.head, groupId))

    getAllResult should contain theSameElementsAs expectedGetAllResult
  }

}
