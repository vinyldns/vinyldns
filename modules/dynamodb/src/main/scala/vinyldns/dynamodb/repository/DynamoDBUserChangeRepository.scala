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

import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import com.typesafe.config.Config
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{UserChange, UserChangeRepository, UserChangeType}
import vinyldns.core.route.Monitored

class DynamoDBUserChangeRepository(
    config: Config,
    dynamoDBHelper: DynamoDBHelper,
    crypto: CryptoAlgebra)
    extends UserChangeRepository
    with Monitored {
  import DynamoDBUserRepository._

  private val logger = LoggerFactory.getLogger(classOf[DynamoDBUserChangeRepository])
  private[repository] val USER_CHANGE_TABLE = "user-change"
  private[repository] val USER_CHANGE_ID = "change_id"
  private[repository] val USER_ID = "user_id"
  private[repository] val MADE_BY_ID = "made_by_id"
  private[repository] val TIMESTAMP = "created"
  private[repository] val USER_NAME = "username"
  private[repository] val CHANGE_TYPE = "change_type"
  private[repository] val NEW_USER = "new_user"
  private[repository] val OLD_USER = "old_user"

  private[repository] val tableAttributes = Seq(
    new AttributeDefinition(USER_CHANGE_ID, "S")
  )
  private val dynamoReads = config.getLong("dynamo.provisionedReads")
  private val dynamoWrites = config.getLong("dynamo.provisionedWrites")

  dynamoDBHelper.setupTable(
    new CreateTableRequest()
      .withTableName(USER_CHANGE_TABLE)
      .withAttributeDefinitions(tableAttributes: _*)
      .withKeySchema(new KeySchemaElement(USER_CHANGE_ID, KeyType.HASH))
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
  )

  def save(change: UserChange): IO[UserChange] =
    monitor("repo.UserChange.save") {
      logger.info(s"Saving user change ${change.id}")
      val item = toItem(change)
      val request = new PutItemRequest().withTableName(USER_CHANGE_TABLE).withItem(item)
      dynamoDBHelper.putItem(request).as(change)
    }

  def get(changeId: String): IO[Option[UserChange]] =
    monitor("repo.UserChange.get") {
      val key = new HashMap[String, AttributeValue]()
      key.put(USER_CHANGE_ID, new AttributeValue(changeId))
      val request = new GetItemRequest().withTableName(USER_CHANGE_TABLE).withKey(key)

      dynamoDBHelper.getItem(request).map { result =>
        Option(result.getItem).map(attrs => fromItem(attrs))
      }
    }

  def fromItem(item: java.util.Map[String, AttributeValue]): UserChange =
    UserChange(
      newUser = DynamoDBUserRepository.fromItem(item.get(NEW_USER).getM),
      changeType = UserChangeType.withName(item.get(CHANGE_TYPE).getS),
      madeByUserId = item.get(MADE_BY_ID).getS,
      oldUser = Option(item.get(OLD_USER))
        .map(av => av.getM)
        .map(attrs => DynamoDBUserRepository.fromItem(attrs)),
      id = item.get(USER_CHANGE_ID).getS,
      created = new DateTime(item.get(CREATED).getN.toLong)
    )

  def toItem(change: UserChange): java.util.Map[String, AttributeValue] = {
    val item = new util.HashMap[String, AttributeValue]()
    item.put(USER_CHANGE_ID, new AttributeValue(change.id))
    item.put(USER_ID, new AttributeValue(change.newUser.id))
    item.put(USER_NAME, new AttributeValue(change.newUser.userName))
    item.put(CHANGE_TYPE, new AttributeValue(change.changeType.toString))
    item.put(CREATED, new AttributeValue().withN(change.created.getMillis.toString))
    item.put(
      NEW_USER,
      new AttributeValue().withM(DynamoDBUserRepository.toItem(crypto, change.newUser)))
    change.oldUser.foreach { user =>
      item.put(OLD_USER, new AttributeValue().withM(DynamoDBUserRepository.toItem(crypto, user)))
    }
    item
  }
}
