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

package vinyldns.api.domain.access

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RegexValidatorSpec extends AnyWordSpec with Matchers {

  "RegexValidator.safeMatches" should {
    "match valid patterns correctly" in {
      RegexValidator.safeMatches("test.example.com", "test.*") shouldBe true
      RegexValidator.safeMatches("prod-api", "prod-.*") shouldBe true
      RegexValidator.safeMatches("dev-api", "dev-.*") shouldBe true
      RegexValidator.safeMatches("a", "a|b|c") shouldBe true
      RegexValidator.safeMatches("xyz.com", "example.*") shouldBe false
    }

    "handle timeout on ReDoS patterns" in {
      // At the end of the input string and not match:  "a"*60 doesn't end in 'x', so requires backtracking
      val result = RegexValidator.safeMatches("a" * 60, "(a+)+x")
      result shouldBe false
    }

    "handle invalid patterns gracefully" in {
      RegexValidator.safeMatches("test", "[invalid") shouldBe false
      RegexValidator.safeMatches("test", "(unclosed") shouldBe false
    }
  }

  "RegexValidator.validatePattern" should {
    "accept legitimate regex patterns" in {
      RegexValidator.validatePattern("test.*")
      RegexValidator.validatePattern("^[a-z0-9]+$")
      RegexValidator.validatePattern("a|b|c")
      RegexValidator.validatePattern("^(test|prod)-[a-z0-9]+$")
      RegexValidator.validatePattern(".*\\.example\\.com$")
    }

    "reject nested quantifier patterns (ReDoS protection)" in {
      val ex1 = the[IllegalArgumentException] thrownBy {
        RegexValidator.validatePattern("(a+)+")
      }
      ex1.getMessage should include("potentially dangerous")

      val ex2 = the[IllegalArgumentException] thrownBy {
        RegexValidator.validatePattern("(a*)*")
      }
      ex2.getMessage should include("potentially dangerous")
    }

    "reject patterns exceeding max length" in {
      val ex = the[IllegalArgumentException] thrownBy {
        RegexValidator.validatePattern("a" * 600)
      }
      ex.getMessage should include("exceeds maximum length")
    }

    "reject invalid regex syntax" in {
      val ex = the[IllegalArgumentException] thrownBy {
        RegexValidator.validatePattern("[invalid")
      }
      ex.getMessage should include("Invalid regex")
    }
  }

  "ReDoS protection" should {
    "prevent storage of dangerous patterns" in {
      the[IllegalArgumentException] thrownBy {
        RegexValidator.validatePattern("(a+)+")
      }
    }

    "timeout on dangerous patterns at runtime" in {
      val dangerousPattern = "(a+)+b"
      val adversarialInput = "a" * 60 + "x"
      RegexValidator.safeMatches(adversarialInput, dangerousPattern) shouldBe false
    }
  }
}
