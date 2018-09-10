package vinyldns.dynamodb.repository
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.membership.{User, UserChange, UserChangeType}

class DynamoDBUserChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private val USER_CHANGE_TABLE = "groups-live"

  private val tableConfig = ConfigFactory.parseString(s"""
                                                         | dynamo {
                                                         |   tableName = "$USER_CHANGE_TABLE"
                                                         |   provisionedReads=30
                                                         |   provisionedWrites=30
                                                         | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private val testUser = User(
    id = "test-user",
    userName = "testUser",
    firstName = Some("Test"),
    lastName = Some("User"),
    email = Some("test@user.com"),
    created = DateTime.now,
    isSuper = false,
    accessKey = "test",
    secretKey = "user"
  )

  private var repo: DynamoDBUserChangeRepository = _

  def setup(): Unit = {
    repo = new DynamoDBUserChangeRepository(tableConfig, dynamoDBHelper, new NoOpCrypto(tableConfig))
    waitForRepo(repo.get("any"))
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(USER_CHANGE_TABLE)
    val deleteTables = dynamoDBHelper.deleteTable(request)
    deleteTables.unsafeRunSync()
  }

  "DynamoDBUserChangeRepository" should {
    "save a user change" in {
      val c = UserChange(
        newUser = testUser,
        changeType = UserChangeType.Create,
        madeByUserId = "me",
        oldUser = None,
        created = DateTime.now,
        id = "test-create"
      )

      val t = for {
        _ <- repo.save(c)
        retrieved <- repo.get(c.id)
      } yield retrieved

      t.unsafeRunSync() shouldBe Some(c)
    }
  }
}
