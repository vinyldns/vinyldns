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

package vinyldns.api.route

import cats.data._, cats.implicits._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.scalatest.Matchers
import org.scalatest.WordSpec

import cats.implicits._

// Test classes
object PetType extends Enumeration {
  type PetType = Value
  val Dog, Cat = Value
}
object HomeType extends Enumeration {
  type HomeType = Value
  val Apartment, RowHome, Freestanding = Value
}
object UserType extends Enumeration {
  type UserType = Value
  val Free, Upgraded, Premium = Value
}
import PetType._
import HomeType._
import UserType._

case class Address(street: String, number: BigInt)
case class Home(typ: Option[HomeType], address: Address)
case class Pet(typ: PetType, name: String)
case class User(name: String, pet: Option[Pet], home: Home, typ: UserType)

class JsonValidationSpec
    extends WordSpec
    with JsonValidation
    with ScalatestRouteTest
    with Matchers {

  val serializers: Seq[Serializer[_]] = Seq(
    AddressSerializer,
    HomeSerializer,
    UserSerializer,
    PetSerializer,
    JsonEnumV(PetType),
    JsonEnumV(HomeType),
    JsonEnumV(UserType)
  )

  case object AddressSerializer extends ValidationSerializer[Address] // default serialization
  case object HomeSerializer extends ValidationSerializer[Home] {
    override def fromJson(js: JValue): ValidatedNel[String, Home] =
      (
        (js \ "typ").optional(HomeType),
        (js \ "address").required[Address]("Missing Home.address")
      ).mapN(Home.apply)
  }
  case object PetSerializer extends ValidationSerializer[Pet] {
    override def fromJson(js: JValue): ValidatedNel[String, Pet] =
      (
        (js \ "typ").required(PetType, "Missing Pet.type"),
        (js \ "name")
          .required[String]("Missing Pet.name")
          .check(
            "Pet.name is too long" -> (_.length < 10),
            "Pet.name is too short" -> (_.length > 5),
            "Pet.name must end with y" -> (_.endsWith("y"))
          )
      ).mapN(Pet.apply)

    override def toJson(pet: Pet): JValue =
      ("name" -> pet.name) ~
        ("typ" -> pet.typ.toString)
  }
  case object UserSerializer extends ValidationSerializer[User] {
    override def fromJson(js: JValue): ValidatedNel[String, User] =
      (
        (js \ "name").default[String]("Anonymous"),
        (js \ "pet").optional[Pet],
        (js \ "home").required[Home]("Missing User.home"),
        (js \ "typ").default(UserType, UserType.Free)
      ).mapN(User.apply)
  }

  "Deserialization" should {
    "work using the default deserialization" in {
      val street: String = "main st"
      val number: Int = 5
      val addr: JValue = ("street" -> street) ~ ("number" -> number)
      addr.extractOpt[Address] should not be None
      val extracted = addr.extract[Address]
      extracted.street shouldBe street
      extracted.number shouldBe number
    }

    "work using explicit deserialization" in {
      val name: String = "Scruffy"
      val typ: PetType = PetType.Dog
      val pet: JValue = ("name" -> name) ~ ("typ" -> typ.toString)
      pet.extractOpt[Pet] should not be None
      val extracted = pet.extract[Pet]
      extracted.name shouldBe name
      extracted.typ shouldBe typ
    }

    "correctly handle default and optional values" in {
      val js: JValue = "home" -> Extraction.decompose(Home(None, Address("main st", 5)))
      val user = js.extract[User]
      user.name shouldBe "Anonymous"
      user.pet shouldBe None
      user.typ shouldBe UserType.Free
      user.home.typ shouldBe None
      user.home.address.street shouldBe "main st"
      user.home.address.number shouldBe 5
    }

    "throw errors for requried values" in {
      val pet: JValue = ("key" -> "val") ~ ("other" -> "whatever")
      val extracted = try {
        pet.extract[Pet].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[Pet]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.toSet shouldBe Set("Missing Pet.type", "Missing Pet.name")
    }

    "throw errors for checked conditions" in {
      val pet: JValue = ("name" -> "fido") ~ ("other" -> "whatever")
      val extracted = try {
        pet.extract[Pet].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[Pet]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.toSet shouldBe Set(
        "Missing Pet.type",
        "Pet.name must end with y",
        "Pet.name is too short"
      )
    }

    "throw errors for invalid Enumerations" in {
      val pet: JValue = ("name" -> "Scruffy") ~ ("typ" -> "Fish")
      val extracted = try {
        pet.extract[Pet].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[Pet]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.toSet shouldBe Set("Invalid PetType")
    }

    "throw errors from nested classes" in {
      val user: JValue =
        ("name" -> "timmy") ~
          ("pet" -> (
            ("name" -> "fido") ~
              ("other" -> "invalid")
          )) ~
          ("home" -> (
            ("typ" -> HomeType.Apartment.toString) ~
              ("random" -> "other")
          ))
      val extracted = try {
        user.extract[User].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[User]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.toSet shouldBe Set(
        "Missing Pet.type",
        "Pet.name must end with y",
        "Pet.name is too short",
        "Missing Home.address"
      )
    }

    "throw reasonable errors using default deserialization" in {
      val addr: JValue = ("key" -> "val") ~ ("other" -> "whatever")
      val extracted = try {
        addr.extract[Address].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[Address]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.toSet shouldBe Set("Failed to parse Address")
    }

    "catch errors from the default parsers" in {
      val pet: JValue = ("name" -> ("unnecessary" -> "json")) ~ ("typ" -> "Dog")
      val extracted = try {
        pet.extract[Pet].valid[Throwable]
      } catch {
        case e: Throwable => e.invalid[Pet]
      }
      extracted.isInvalid shouldBe true
      val str = extracted.swap.map(_.getMessage).getOrElse("")
      str should not be ""
      val err = (parse(str) \ "errors").extract[List[String]]
      err.nonEmpty shouldBe true
    }
  }
  "Serialization" should {
    "work using the default serialization" in {
      val street: String = "main st"
      val number: Int = 5
      val addr: JValue = Extraction.decompose(Address(street, number))
      (addr \ "street").extract[String] shouldBe street
      (addr \ "number").extract[Int] shouldBe number
    }
    "work using explicit serialization" in {
      val street: String = "main st"
      val number: Int = 5
      val addr: JValue = Extraction.decompose(Address(street, number))
      (addr \ "street").extract[String] shouldBe street
      (addr \ "number").extract[Int] shouldBe number
    }
  }
}
