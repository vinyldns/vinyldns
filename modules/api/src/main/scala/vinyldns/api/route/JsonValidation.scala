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

import java.util.NoSuchElementException

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import com.fasterxml.jackson.core.JsonParseException
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import java.util.Date
import java.time.Instant
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.ext._
import org.json4s.jackson.JsonMethods._
import vinyldns.core.Messages._

import scala.reflect.ClassTag

case class JsonErrors(errors: List[String])

case object VinylDateTimeSerializer
    extends CustomSerializer[Instant](
      format =>
        (
          {
            case JInt(s) => Instant.ofEpochMilli(s.longValue())
            case JString(s) => Instant.ofEpochMilli(format.dateFormat.parse(s).map(_.getTime).getOrElse(0L))
            case JNull => null
          }, {
            case d: Instant => JString(format.dateFormat.format(Date.from(d)))
          }
        )
    )

trait JsonValidationSupport extends Json4sSupport {

  import scala.collection._

  // this is where you define all serializers, custom and validating serializers
  val serializers: Iterable[Serializer[_]]

  // TODO: needed in order to stay backward compatible for date time formatting,
  // should be removed when we upgrade json libs
  val dtSerializers = List(
    DurationSerializer,
    InstantSerializer,
    VinylDateTimeSerializer,
    IntervalSerializer(),
    LocalDateSerializer(),
    LocalTimeSerializer(),
    PeriodSerializer
  )

  /**
    * Returns an adjusted set of serializers excluding the serializer passed in.  This is needed otherwise
    * we will get a StackOverflow
    *
    * @param ser The serializer to be removed from the formats
    * @return An adjusted Formats without the serializer passed in
    */
  private[route] def adjustedFormats(ser: Serializer[_]) =
    DefaultFormats ++ JavaTimeSerializers.all ++ serializers.filterNot(_.equals(ser))

  implicit def json4sJacksonFormats: Formats = DefaultFormats ++ dtSerializers ++ serializers
}

trait JsonValidation extends JsonValidationSupport {

  /**
    * Simplifies creation of a ValidationSerializer
    */
  def JsonV[A: Manifest]: ValidationSerializer[A] = new ValidationSerializer[A] {}

  def JsonV[A: Manifest](validator: JValue => ValidatedNel[String, A]): ValidationSerializer[A] =
    new ValidationSerializer[A] {
      override def fromJson(jv: JValue) = validator(jv)
    }

  def JsonV[A: Manifest](
      validator: JValue => ValidatedNel[String, A],
      serializer: A => JValue
  ): ValidationSerializer[A] =
    new ValidationSerializer[A] {
      override def fromJson(jv: JValue) = validator(jv)
      override def toJson(a: A) = serializer(a)
    }

  /**
    * Simplifies creation of an Enum Serializer
    */
  def JsonEnumV[E <: Enumeration: ClassTag](enum: E): EnumNameSerializer[E] =
    new EnumNameSerializer[E](enum)

  /**
    * Main guy to support validating serialization.  Extends the json4s [[Serializer]] interface.
    *
    * Has to implement BOTH the serialize AND deserialize methods.
    *
    * Here are some challenges:
    * You cannot simply do something like def serialize(implicit format: Formats) = Extraction.decompose(a)(formats)
    * and try and delegate back to Json4s
    *
    * If you do this, then you will StackOverflow as Extraction.decompose will enter back into its "hunt for the
    * right serializer" and call the same serialize function again.
    *
    * The workaround is that either you MUST implement the serialize method (which stinks), or you have to dynamically
    * remove the current serializer when you delegate (see the toJson method below).
    *
    * Otherwise, you can override the fromJson method and provide a function that takes a JValue and returns
    * a ValidatedNel[String, T] (aliased by the type ValidatedNel[String,T])
    */
  abstract class ValidationSerializer[A: Manifest] extends Serializer[A] {

    val Class: Class[_] = implicitly[Manifest[A]].runtimeClass

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case a: A => toJson(a)
    }

    /**
      * A PartialFunction that takes a scala reflect [[TypeInfo]].  The way this is run in Json4s land
      * is that Json4s tests this serializer by saying PartialFunction.isDefinedAt[XXXXType].  So, if the
      * partial function does not match, then Json4s keeps trying other things
      *
      * @param format passed in by Json4s
      * @return A deserialized T
      */
    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = {
      case (TypeInfo(Class, _), json) =>
        fromJson(json) match {
          case Valid(a: A) => a
          case Invalid(err) =>
            throw new MappingException(compact(render("errors" -> err.toList.toSet)(format)))
        }
    }

    // delegates to Extraction.decompose using a subset of all of the serializers (which is
    // all serializers minus this one; this is how we avoid the StackOverflow
    def toJson(a: A): JValue = Extraction.decompose(a)(subsetFormats)

    /**
      * Override this to define your own custom validations
      *
      * @param js A [[JValue]] to be validated and deserialized
      * @return A ValidatedNel[String, T] that will contain either the deserialized type T
      *         or a list of String that contain errors
      */
    def fromJson(js: JValue): ValidatedNel[String, A] =
      try {
        Extraction.extract(js, TypeInfo(Class, None))(subsetFormats) match {
          case a: A => a.validNel[String]
          case _ => UnexpectedExtractionErrorMsg.invalidNel[A]
        }
      } catch {
        case _: MappingException | _: JsonParseException =>
          JsonParseErrorMsg.format(Class.getSimpleName.replace("$", "")).invalidNel[A]
      }

    // use a subset of formats that does not include this class or we will StackOverflow
    private def subsetFormats = adjustedFormats(this)
  }

  /**
    * Pimps json4s JValue types to provide the opportunity to add validations
    *
    * @param json The [[JValue]] being pimped
    */
  implicit class JsonValidationImprovements(json: JValue) {

    def extractType[T: Manifest](default: => ValidatedNel[String, T]): ValidatedNel[String, T] =
      json match {
        case JNothing | JNull => default

        case j =>
          try {
            // Call the Json4s extractor and wrap it in a Success NEL
            j.extract[T].validNel[String]
          } catch {
            case MappingException(err, _) =>
              try {
                // Retrieves nested errors and adds to JSON structure
                (parse(err) \ "errors")
                  .extractOpt[List[String]]
                  .flatMap(_.toNel)
                  .map(_.invalid[T])
                  .getOrElse(default)
              } catch {
                case _: JsonParseException =>
                  err.invalidNel[T]
              }
            case e: JsonParseException =>
              UnexpectedJsonErrorMsg.format(json, e.getMessage).invalidNel[T]
          }
      }

    def extractEnum[E <: Enumeration](
        enum: E
    )(default: => ValidatedNel[String, E#Value]): ValidatedNel[String, E#Value] = {
      lazy val invalidMsg =
        InvalidMsg.format(enum.getClass.getSimpleName.replace("$", "")).invalidNel[E#Value]

      json match {
        case JNothing | JNull => default
        case JString(s) =>
          try {
            enum.withName(s).validNel[String]
          } catch {
            case _: NoSuchElementException => invalidMsg
          }
        case _ => invalidMsg
      }
    }

    /**
      * Indicates that the value needs to be present
      *
      * @param msg The message to return if the value is not present
      * @return The type extracted from JSON, or a failure with the message specified if not present
      */
    def required[T: Manifest](msg: => String): ValidatedNel[String, T] =
      extractType(msg.invalidNel[T])

    /**
      * Indicates that the value is optional
      *
      * @return The value parsed, or None if the value was not present
      */
    def optional[T: Manifest]: ValidatedNel[String, Option[T]] =
      extractType[Option[T]](None.validNel[String])

    /**
      * Sets a default value if the type could not be extracted from Json
      *
      * @param default The default value to set when the value is not present
      * @return The value that was parsed, or the default
      */
    def default[T: Manifest](default: => T): ValidatedNel[String, T] =
      extractType[T](default.validNel[String])

    /**
      * Indicates that the value needs to be present
      *
      * @param msg The message to return if the value is not present
      * @return The type extracted from JSON, or a failure with the message specified if not present
      */
    def required[E <: Enumeration](enum: E, msg: => String): ValidatedNel[String, E#Value] =
      extractEnum(enum)(msg.invalidNel[E#Value])

    /**
      * Indicates that the value is optional
      *
      * @return The value parsed, or None if the value was not present
      */
    def optional[E <: Enumeration](enum: E): ValidatedNel[String, Option[E#Value]] =
      extractEnum(enum)(Valid(null))
        .map(Option(_))

    /**
      * Sets a default value if the type could not be extracted from Json
      *
      * @param default The default value to set when the value is not present
      * @return The value that was parsed, or the default
      */
    def default[E <: Enumeration](enum: E, default: => E#Value): ValidatedNel[String, E#Value] =
      extractEnum(enum)(default.validNel[String])
  }

  implicit class ValidationNelImprovements[A](base: ValidatedNel[String, A]) {

    /**
      * Aggregates validations on contained success by checking boolean conditions
      *
      * Takes a map from E (usually strings) to a boolean function on the success type. If the boolean function
      * evaluates false, the left side of the map (again, the string) is returned in a failureNel. These are
      * aggregated to show all failed validations, or the success is returned if all checks passed.
      *
      * @param validations mapping of some orderable class (usually strings) to boolean
      *                    functions on the success type.
      * @return The aggregated failure messages or the successfully validated type
      */
    def check(validations: (String, A => Boolean)*): ValidatedNel[String, A] =
      validations
        .map({ case (err, func) => base.ensure(NonEmptyList.one(err))(func) })
        .fold(base)(_.findFailure(_))
        .leftMap(_.distinct)

    def checkIf(b: Boolean)(validations: (String, A => Boolean)*): ValidatedNel[String, A] =
      if (b) base.check(validations: _*) else base

    /**
      * Modeled off of `findSuccess` to combine failures and favor failures over successes, returning only the first
      * if both are successful
      */
    def findFailure[AA >: A](that: => ValidatedNel[String, AA]): ValidatedNel[String, AA] =
      (base, that) match {
        case (Invalid(a), Invalid(b)) => Invalid(a.combine(b))
        case (Invalid(_), _) => base
        case (_, Invalid(_)) => that
        case _ => base
      }
  }
}
