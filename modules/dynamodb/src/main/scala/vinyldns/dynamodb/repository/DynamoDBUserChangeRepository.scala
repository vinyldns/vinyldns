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

package vinyldns.dynamodb.repository
import java.util
import java.util.HashMap

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership._
import vinyldns.core.route.Monitored

object DynamoDBUserChangeRepository {

  val USER_CHANGE_ID: String = "change_id"
  val USER_ID: String = "user_id"
  val MADE_BY_ID: String = "made_by_id"
  val CREATED: String = "created"
  val USER_NAME: String = "username"
  val CHANGE_TYPE: String = "change_type"
  val NEW_USER: String = "new_user"
  val OLD_USER: String = "old_user"

  val TABLE_ATTRIBUTES: Seq[AttributeDefinition] = Seq(
    new AttributeDefinition(USER_CHANGE_ID, "S")
  )

  // Note: This should be an Either; however pulling everything into an Either is a big refactoring
  def fromItem(item: java.util.Map[String, AttributeValue]): IO[UserChange] =
    for {
      c <- IO(item.get(CHANGE_TYPE).getS)
      changeType <- IO.fromEither(UserChangeType.fromString(c))
      newUser <- IO(item.get(NEW_USER).getM).flatMap(m => DynamoDBUserRepository.fromItem(m))
      oldUser <- OptionT(IO(Option(item.get(OLD_USER))))
        .subflatMap(av => Option(av.getM))
        .semiflatMap(DynamoDBUserRepository.fromItem)
        .value
      madeByUserId <- IO(item.get(MADE_BY_ID).getS)
      id <- IO(item.get(USER_CHANGE_ID).getS)
      created <- IO(new DateTime(item.get(CREATED).getN.toLong))
      change <- IO.fromEither(UserChange(id, newUser, madeByUserId, created, oldUser, changeType))
    } yield change

  def toItem(crypto: CryptoAlgebra, change: UserChange): java.util.Map[String, AttributeValue] = {
    val item = new util.HashMap[String, AttributeValue]()
    item.put(USER_CHANGE_ID, new AttributeValue(change.id))
    item.put(USER_ID, new AttributeValue(change.newUser.id))
    item.put(USER_NAME, new AttributeValue(change.newUser.userName))
    item.put(MADE_BY_ID, new AttributeValue(change.madeByUserId))
    item.put(CHANGE_TYPE, new AttributeValue(UserChangeType.fromChange(change).value))
    item.put(CREATED, new AttributeValue().withN(change.created.getMillis.toString))
    item.put(
      NEW_USER,
      new AttributeValue().withM(DynamoDBUserRepository.toItem(crypto, change.newUser))
    )

    change match {
      case UserChange.UpdateUser(_, _, _, oldUser, _) =>
        item.put(
          OLD_USER,
          new AttributeValue().withM(DynamoDBUserRepository.toItem(crypto, oldUser))
        )
      case _ => ()
    }
    item
  }

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings,
      crypto: CryptoAlgebra
  ): IO[DynamoDBUserChangeRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBUserChangeRepository")
    )

    val setup =
      dynamoDBHelper.setupTable(
        new CreateTableRequest()
          .withTableName(config.tableName)
          .withAttributeDefinitions(TABLE_ATTRIBUTES: _*)
          .withKeySchema(new KeySchemaElement(USER_CHANGE_ID, KeyType.HASH))
          .withProvisionedThroughput(
            new ProvisionedThroughput(config.provisionedReads, config.provisionedWrites)
          )
      )

    val serialize: UserChange => java.util.Map[String, AttributeValue] = toItem(crypto, _)
    val deserialize: java.util.Map[String, AttributeValue] => IO[UserChange] = fromItem
    setup.as(
      new DynamoDBUserChangeRepository(config.tableName, dynamoDBHelper, serialize, deserialize)
    )
  }
}

class DynamoDBUserChangeRepository private[repository] (
    tableName: String,
    val dynamoDBHelper: DynamoDBHelper,
    serialize: UserChange => java.util.Map[String, AttributeValue],
    deserialize: java.util.Map[String, AttributeValue] => IO[UserChange]
) extends UserChangeRepository
    with Monitored {
  import DynamoDBUserChangeRepository._

  private val logger = LoggerFactory.getLogger(classOf[DynamoDBUserChangeRepository])

  def save(change: UserChange): IO[UserChange] =
    monitor("repo.UserChange.save") {
      logger.info(s"Saving user change ${change.id}")
      val item = serialize(change)
      val request = new PutItemRequest().withTableName(tableName).withItem(item)
      dynamoDBHelper.putItem(request).as(change)
    }

  def get(changeId: String): IO[Option[UserChange]] =
    monitor("repo.UserChange.get") {
      val key = new HashMap[String, AttributeValue]()
      key.put(USER_CHANGE_ID, new AttributeValue(changeId))
      val request = new GetItemRequest().withTableName(tableName).withKey(key)

      // OptionT is a convenience wrapper around IO[Option[A]]
      OptionT
        .liftF(dynamoDBHelper.getItem(request))
        .subflatMap(r => Option(r.getItem))
        .semiflatMap(item => deserialize(item))
        .value
    }
}
