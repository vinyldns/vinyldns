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

package vinyldns.api

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import org.slf4j.LoggerFactory
import vinyldns.dynamodb.repository.{DynamoDBHelper, DynamoDBIntegrationSpec}

trait DynamoDBApiIntegrationSpec extends DynamoDBIntegrationSpec {

  override val dynamoClient: AmazonDynamoDBClient = getDynamoClient(19000)

  override val dynamoDBHelper: DynamoDBHelper =
    new DynamoDBHelper(dynamoClient, LoggerFactory.getLogger("DynamoDBApiIntegrationSpec"))

}
