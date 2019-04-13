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

package vinyldns.client.models.zone

import org.scalatest.{Matchers, WordSpec}

class BasicZoneInfoSpec extends WordSpec with Matchers {
  val noConnections = ZoneCreateInfo("name.", "test@email.com", "adminGroupId", false, None, None)
  val connection = ZoneConnection("keyName", "keyName", "key", "1.1.1.1")
  val transfer = ZoneConnection("tKeyName", "tKeyName", "tkey", "2.2.2.2")
  val withConnections = noConnections
    .copy(connection = Some(connection), transferConnection = Some(transfer))

  "BasicZoneInfo.newConnectionKeyName" should {
    "work when changing existing key name" in {
      val expected =
        withConnections.copy(connection = Some(connection.copy(name = "new", keyName = "new")))
      val actual = withConnections.copy(connection = withConnections.newConnectionKeyName("new"))

      actual shouldBe expected
    }

    "work when adding key name" in {
      val expected =
        noConnections.copy(connection = Some(ZoneConnection(name = "new", keyName = "new")))
      val actual = noConnections.copy(connection = noConnections.newConnectionKeyName("new"))

      actual shouldBe expected
    }
  }

  "BasicZoneInfo.newConnectionKey" should {
    "work when changing existing key" in {
      val expected =
        withConnections.copy(connection = Some(connection.copy(key = "new")))
      val actual = withConnections.copy(connection = withConnections.newConnectionKey("new"))

      actual shouldBe expected
    }

    "work when adding key" in {
      val expected =
        noConnections.copy(connection = Some(ZoneConnection(key = "new")))
      val actual = noConnections.copy(connection = noConnections.newConnectionKey("new"))

      actual shouldBe expected
    }
  }

  "BasicZoneInfo.newConnectionServer" should {
    "work when changing existing server" in {
      val expected =
        withConnections.copy(connection = Some(connection.copy(primaryServer = "new")))
      val actual = withConnections.copy(connection = withConnections.newConnectionServer("new"))

      actual shouldBe expected
    }

    "work when adding server" in {
      val expected =
        noConnections.copy(connection = Some(ZoneConnection(primaryServer = "new")))
      val actual = noConnections.copy(connection = noConnections.newConnectionServer("new"))

      actual shouldBe expected
    }
  }

  "BasicZoneInfo.newTransferKeyName" should {
    "work when changing existing transfer key name" in {
      val expected =
        withConnections.copy(
          transferConnection = Some(transfer.copy(name = "new", keyName = "new")))
      val actual =
        withConnections.copy(transferConnection = withConnections.newTransferKeyName("new"))

      actual shouldBe expected
    }

    "work when adding transfer key name" in {
      val expected =
        noConnections.copy(transferConnection = Some(ZoneConnection(name = "new", keyName = "new")))
      val actual = noConnections.copy(transferConnection = noConnections.newTransferKeyName("new"))

      actual shouldBe expected
    }
  }

  "BasicZoneInfo.newTransferKey" should {
    "work when changing existing transfer key" in {
      val expected =
        withConnections.copy(transferConnection = Some(transfer.copy(key = "new")))
      val actual = withConnections.copy(transferConnection = withConnections.newTransferKey("new"))

      actual shouldBe expected
    }

    "work when adding transfer key" in {
      val expected =
        noConnections.copy(transferConnection = Some(ZoneConnection(key = "new")))
      val actual = noConnections.copy(transferConnection = noConnections.newTransferKey("new"))

      actual shouldBe expected
    }
  }

  "BasicZoneInfo.newTransferServer" should {
    "work when changing existing transfer server" in {
      val expected =
        withConnections.copy(transferConnection = Some(transfer.copy(primaryServer = "new")))
      val actual =
        withConnections.copy(transferConnection = withConnections.newTransferServer("new"))

      actual shouldBe expected
    }

    "work when adding transfer server" in {
      val expected =
        noConnections.copy(transferConnection = Some(ZoneConnection(primaryServer = "new")))
      val actual = noConnections.copy(transferConnection = noConnections.newTransferServer("new"))

      actual shouldBe expected
    }
  }
}
