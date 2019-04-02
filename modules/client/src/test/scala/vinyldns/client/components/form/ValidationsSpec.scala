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

package vinyldns.client.components.form

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData

class ValidationsSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val defaultValidations = Validations()

  "Validations.validateRequired" should {
    val required = Validations(required = true)

    "fail if input is empty" in {
      Validations.validateRequired("", required).isLeft shouldBe true
    }

    "pass if input is not empty" in {
      Validations.validateRequired("value", required).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateRequired("", defaultValidations).isRight shouldBe true
      Validations.validateRequired("value", defaultValidations).isRight shouldBe true
    }
  }

  "Validations.validateNoSpaces" should {
    val noSpaces = Validations(noSpaces = true)

    "fail if input has spaces" in {
      Validations.validateNoSpaces("val ue", noSpaces).isLeft shouldBe true
      Validations.validateNoSpaces(" value", noSpaces).isLeft shouldBe true
      Validations.validateNoSpaces("value ", noSpaces).isLeft shouldBe true
    }

    "pass if input has no spaces" in {
      Validations.validateNoSpaces("value", noSpaces).isRight shouldBe true
      Validations.validateNoSpaces("", noSpaces).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateNoSpaces("", defaultValidations).isRight shouldBe true
      Validations.validateNoSpaces("value", defaultValidations).isRight shouldBe true
      Validations.validateNoSpaces("val ue", defaultValidations).isRight shouldBe true
    }
  }

  "Validations.validateMaxSize" should {
    val maxSize = Validations(maxSize = Some(5))

    "fail if input length is greater than max size" in {
      Validations.validateMaxSize("123456", maxSize).isLeft shouldBe true
    }

    "pass if input length is less than or equal to max size" in {
      Validations.validateMaxSize("12345", maxSize).isRight shouldBe true
      Validations.validateMaxSize("1234", maxSize).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateMaxSize("", defaultValidations).isRight shouldBe true
      Validations.validateMaxSize("value", defaultValidations).isRight shouldBe true
      Validations.validateMaxSize("1234567890", defaultValidations).isRight shouldBe true
    }
  }

  "Validations.validateIsOption" should {
    val matchDatalist = Validations(matchOptions = true)
    val options = List("value1" -> "display1", "value2" -> "display2")

    "fail if input is not in options" in {
      Validations.validateIsOption("no-existo", matchDatalist, options).isLeft shouldBe true
    }

    "pass if input is an option" in {
      Validations.validateIsOption("value1", matchDatalist, options).isRight shouldBe true
      Validations.validateIsOption("value2", matchDatalist, options).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateIsOption("", defaultValidations, options).isRight shouldBe true
      Validations.validateIsOption("value", defaultValidations, options).isRight shouldBe true
      Validations.validateIsOption("1234567890", defaultValidations, options).isRight shouldBe true
    }
  }

  "Validations.validateUUID" should {
    val uuid = Validations(uuid = true)

    "fail if input is not a uuid" in {
      Validations.validateUUID("not-uuid", uuid).isLeft shouldBe true
    }

    "pass if input is a uuid" in {
      Validations.validateUUID(testUUID, uuid).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateUUID("", defaultValidations).isRight shouldBe true
      Validations.validateUUID("value", defaultValidations).isRight shouldBe true
      Validations.validateUUID("1234567890", defaultValidations).isRight shouldBe true
    }
  }

  "Validations.validateNoEmptyLines" should {
    val noEmptyLines = Validations(noEmptyLines = true)

    "fail if input has empty lines" in {
      Validations.validateNoEmptyLines("", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("  ", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("\n", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("\ntest", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("test\n", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("test\n  ", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("test\n\ntest", noEmptyLines).isLeft shouldBe true
      Validations.validateNoEmptyLines("test\n  \ntest", noEmptyLines).isLeft shouldBe true
    }

    "pass if input has no empty lines" in {
      Validations.validateNoEmptyLines("test", noEmptyLines).isRight shouldBe true
      Validations.validateNoEmptyLines("test1\ntest2", noEmptyLines).isRight shouldBe true
    }

    "pass if validation is not set" in {
      Validations.validateNoEmptyLines("", defaultValidations).isRight shouldBe true
      Validations.validateNoEmptyLines("test\n  ", defaultValidations).isRight shouldBe true
      Validations.validateNoEmptyLines("test1\ntest2", defaultValidations).isRight shouldBe true
    }
  }
}
