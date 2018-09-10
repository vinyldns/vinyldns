package vinyldns.dynamodb.repository
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.membership.{User, UserChange, UserChangeType}

class DynamoDBUserChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private val USER_CHANGE_TABLE = "user-changes"

  private val tableConfig = ConfigFactory.parseString(s"""
                                                         | dynamo {
                                                         |   table-name = "$USER_CHANGE_TABLE"
                                                         |   provisioned-reads=30
                                                         |   provisioned-writes=30
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

  private val repo: DynamoDBUserChangeRepository =
    DynamoDBUserChangeRepository(dynamoDBHelper, tableConfig, new NoOpCrypto(tableConfig)).unsafeRunSync()

  def setup(): Unit = ()

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
