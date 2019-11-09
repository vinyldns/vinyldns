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
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import org.slf4j.Logger

import cats.effect._

/* Overrides the send method so that it is synchronous, avoids goofy future timing issues in unit tests */
class TestDynamoDBHelper(dynamoDB: AmazonDynamoDBClient, log: Logger)
    extends DynamoDBHelper(dynamoDB: AmazonDynamoDBClient, log: Logger) {

  override private[repository] def send[In <: AmazonWebServiceRequest, Out](
      aws: In,
      func: (In) => Out
  )(implicit d: Describe[_ >: In]): IO[Out] =
    IO(func(aws))
}
