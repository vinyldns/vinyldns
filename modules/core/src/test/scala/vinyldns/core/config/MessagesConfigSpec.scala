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

package vinyldns.core.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import vinyldns.core.Messages

class MessagesConfigSpec extends AnyWordSpec with Matchers {

  "Message class" should {
    "construct with text and Some override text" in {
      val msg = Message("Original text", Some("Override text"))
      msg.text shouldBe "Original text"
      msg.overrideText shouldBe Some("Override text")
    }

    "construct with text and None override text" in {
      val msg = Message("Original text", None)
      msg.text shouldBe "Original text"
      msg.overrideText shouldBe None
    }
  }

  "Message override lookup logic" should {
    "return override text when message exists with Some override" in {
      val originalMsg = "Cannot create group. A group, %s, is already associated with the email address %s. Please contact %s to be added to the group."
      val overrideMsg = "Cannot create group. A group, %s, is already associated with the email address %s. Please contact %s to be added to the group. Visit FAQ."
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(overrideMsg))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      result shouldBe overrideMsg
    }

    "return original message when key does not exist in config" in {
      val unknownMessage = "This message does not exist in the system."
      val testConfig: Map[String, Message] = Map()
      
      val result = Messages.applyOverride(testConfig, unknownMessage)
      result shouldBe unknownMessage
    }

    "return original message when override is None (absent)" in {
      val originalMsg = "User with ID %s was not found"
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, None)
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      result shouldBe originalMsg
    }

    "return original message when override is empty string" in {
      val originalMsg = "Test message with empty override"
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(""))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      result shouldBe originalMsg
    }

    "preserve format placeholders in override text" in {
      val originalMsg = "User %s cannot access zone '%s'"
      val overrideMsg = "User %s has no permission for zone '%s'. Contact admin."
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(overrideMsg))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      result shouldBe overrideMsg
      result should include("%s")
    }

    "reject override with mismatched format placeholder count" in {
      val originalMsg = "Invalid TTL: \"%s\", must be a number between %d and %d."
      val overrideMsgWithoutPlaceholders = "TTL is invalid and must be a positive number."
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(overrideMsgWithoutPlaceholders))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      // Should return original because format doesn't match
      result shouldBe originalMsg
    }

    "reject override with different placeholder types" in {
      val originalMsg = "Invalid port: %s, must be between %d and %d"
      val overrideMsgWrongType = "Invalid port: %d, must be between %s and %s"
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(overrideMsgWrongType))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      // Should return original because format types differ
      result shouldBe originalMsg
    }

    "handle multiline override text correctly" in {
      val originalMsg = "Record with fqdn '%s.%s' cannot be created."
      val overrideMsg = ("Record with fqdn '%s.%s' cannot be created. " +
        "Please check if a record with the same FQDN and type already exist and make the change there.")
      
      val testConfig: Map[String, Message] = Map(
        originalMsg -> Message(originalMsg, Some(overrideMsg))
      )
      
      val result = Messages.applyOverride(testConfig, originalMsg)
      result shouldBe overrideMsg
      result should include("cannot be created")
      result should include("already exist")
    }
  }

  "MessagesConfig loading" should {
    "load successfully (config loading tested in integration tests)" in {
      val testConfig = MessagesConfig(List(
        Message("Test message", Some("Override")),
        Message("Another message", None)
      ))
      testConfig.messages should have length 2
      testConfig.messages(0).text shouldBe "Test message"
      testConfig.messages(0).overrideText shouldBe Some("Override")
      testConfig.messages(1).overrideText shouldBe None
    }
  }

  "Mixed override scenarios" should {
    "demonstrate override-present vs override-absent difference" in {
      val msgWithOverride = "Cannot create group. A group, %s, is already associated."
      val msgWithoutOverride = "User with ID %s was not found"
      val nonExistentMsg = "This message does not exist anywhere"
      
      val testConfig: Map[String, Message] = Map(
        msgWithOverride -> Message(msgWithOverride, Some("Unable to create group: %s already in use.")),
        msgWithoutOverride -> Message(msgWithoutOverride, None)
      )
      
      // Message WITH override (matching format)
      val result1 = Messages.applyOverride(testConfig, msgWithOverride)
      result1 shouldBe "Unable to create group: %s already in use."
      
      // Message WITHOUT override (None)
      val result2 = Messages.applyOverride(testConfig, msgWithoutOverride)
      result2 shouldBe msgWithoutOverride
      
      // Non-existent message
      val result3 = Messages.applyOverride(testConfig, nonExistentMsg)
      result3 shouldBe nonExistentMsg
    }
  }

  "Config file reader integration" should {
    "populate overrideText from application.conf override-text field" in {
      val configContent =
        """
          |messages = [
          |  {
          |    text = "Cannot create group. A group, %s, is already associated with the email address %s."
          |    override-text = "Unable to create group: name %s already used. Contact %s for access."
          |  }
          |]
        """.stripMargin

      val config = ConfigFactory.parseString(configContent)
      val result = ConfigSource.fromConfig(config).load[MessagesConfig]

      result shouldBe a[Right[_, _]]
      val messagesConfig = result.asInstanceOf[Right[_, MessagesConfig]].value
      messagesConfig.messages should have length 1
      messagesConfig.messages(0).text shouldBe 
        "Cannot create group. A group, %s, is already associated with the email address %s."
      messagesConfig.messages(0).overrideText shouldBe 
        Some("Unable to create group: name %s already used. Contact %s for access.")
    }

    "handle missing override-text field correctly" in {
      val configContent =
        """
          |messages = [
          |  {
          |    text = "User with ID %s was not found"
          |  }
          |]
        """.stripMargin

      val config = ConfigFactory.parseString(configContent)
      val result = ConfigSource.fromConfig(config).load[MessagesConfig]

      result shouldBe a[Right[_, _]]
      val messagesConfig = result.asInstanceOf[Right[_, MessagesConfig]].value
      messagesConfig.messages should have length 1
      messagesConfig.messages(0).text shouldBe "User with ID %s was not found"
      messagesConfig.messages(0).overrideText shouldBe None
    }
  }
}
