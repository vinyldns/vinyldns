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

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import play.api.Configuration

class ConfigSpec extends Specification {
  "Dynamo config" should {
    "contain the correct settings" in {
      // found in application-test.conf
      val configuration = Configuration.apply(ConfigFactory.load())
      val dynamoAKID = configuration.get[String]("dynamo.key")
      val dynamoSecret = configuration.get[String]("dynamo.secret")
      val dynamoEndpoint = configuration.get[String]("dynamo.endpoint")
      val region = configuration.get[String]("dynamo.region")

      dynamoAKID must beEqualTo("akid goes here")
      dynamoSecret must beEqualTo("secret key goes here")
      dynamoEndpoint must beEqualTo("http://127.0.0.1:19000")
      region must beEqualTo("us-east-1")
    }
  }
}
