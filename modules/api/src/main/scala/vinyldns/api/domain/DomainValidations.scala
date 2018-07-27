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

package vinyldns.api.domain

import scalaz.Scalaz._
import scalaz._
import vinyldns.api.domain.ValidationImprovements._
import vinyldns.api.domain.record.RecordType.{RecordType, _}

import scala.util.Try
import scala.util.matching.Regex

/*
  Object to house common domain validations
 */
object DomainValidations {
  val validEmailRegex: Regex = """^([0-9a-zA-Z_\-\.]+)@([0-9a-zA-Z_\-\.]+)\.([a-zA-Z]{2,5})$""".r
  val validFQDNRegex: Regex =
    """^(?:([0-9a-zA-Z]{1,63}|[0-9a-zA-Z]{1}[0-9a-zA-Z\-\/]{0,61}[0-9a-zA-Z]{1})\.)*$""".r
  val validIpv4Regex: Regex =
    """^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".r
  val validIpv6Regex: Regex =
    """^(
      #([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|
      #([0-9a-fA-F]{1,4}:){1,7}:|
      #([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|
      #([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|
      #([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|
      #([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|
      #([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|
      #[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|
      #:((:[0-9a-fA-F]{1,4}){1,7}|:)|
      #fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|
      #::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|
      #(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|
      #([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]
      #|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])
      #)$""".stripMargin('#').replaceAll("\n", "").r
  val PORT_MIN_VALUE: Int = 0
  val PORT_MAX_VALUE: Int = 65535
  val HOST_MIN_LENGTH: Int = 2
  val HOST_MAX_LENGTH: Int = 255
  val TTL_MAX_LENGTH: Int = 2147483647
  val TTL_MIN_LENGTH: Int = 30
  val TXT_TEXT_MIN_LENGTH: Int = 1
  val TXT_TEXT_MAX_LENGTH: Int = 64764
  val MX_PREFERENCE_MIN_VALUE: Int = 0
  val MX_PREFERENCE_MAX_VALUE: Int = 65535

  def validateEmail(email: String): ValidationNel[DomainValidationError, String] =
    /*
     Basic e-mail checking that also blocks some positive e-mails (by RFC standards)
     (eg. e-mails containing hex and special characters.)
     */
    if (validEmailRegex.findFirstIn(email).isDefined) email.successNel
    else InvalidEmail(email).failureNel

  def validateHostName(name: String): ValidationNel[DomainValidationError, String] = {
    /*
      Label rules are as follows (from RFC 952; detailed in RFC 1034):
        - Starts with a letter, OR digit (as of RFC 1123)
        - Interior contains letter, digit or hyphen
        - Ends with a letter or digit
      All possible labels permutations:
        - A single letter/digit: [0-9a-zA-Z]{1}
        - A combination of 1-63 letters/digits: [0-9a-zA-Z]{1,63}
        - A single letter/digit followed by up to 61 letters, digits, hyphens or slashes
        and ending with a letter/digit:[0-9a-zA-Z]{1}[0-9a-zA-Z\-]{0,61}[0-9a-zA-Z]{1}
      A valid domain name is a series of one or more <label>s,
      joined by dots/slashes and terminating on a zero-length <label> (ie. dot)
     */
    val checkRegex = validFQDNRegex
      .findFirstIn(name)
      .map(_.successNel)
      .getOrElse(InvalidDomainName(name).failureNel)
    val checkLength = validateStringLength(name, Some(HOST_MIN_LENGTH), HOST_MAX_LENGTH)

    (checkRegex +++ checkLength).map(_ => name)
  }

  def validateIpv4Address(address: String): ValidationNel[DomainValidationError, String] =
    validIpv4Regex
      .findFirstIn(address)
      .map(_.successNel)
      .getOrElse(InvalidIpv4Address(address).failureNel)

  def validateIpv6Address(address: String): ValidationNel[DomainValidationError, String] =
    validIpv6Regex
      .findFirstIn(address)
      .map(_.successNel)
      .getOrElse(InvalidIpv6Address(address).failureNel)

  def validatePort(port: String): ValidationNel[DomainValidationError, String] =
    Try(port.toInt)
      .map {
        case ok if ok >= PORT_MIN_VALUE && ok <= PORT_MAX_VALUE => port.successNel
        case outOfRange =>
          InvalidPortNumber(outOfRange.toString, PORT_MIN_VALUE, PORT_MAX_VALUE).failureNel
      }
      .getOrElse(InvalidPortNumber(port, PORT_MIN_VALUE, PORT_MAX_VALUE).failureNel)

  def validateStringLength(
      value: Option[String],
      minInclusive: Option[Int],
      maxInclusive: Int): ValidationNel[DomainValidationError, Option[String]] =
    validateIfDefined(value) { d =>
      validateStringLength(d, minInclusive, maxInclusive)
    }

  def validateStringLength(
      value: String,
      minInclusive: Option[Int],
      maxInclusive: Int): ValidationNel[DomainValidationError, String] =
    if (minInclusive.forall(m => value.length >= m) && value.length <= maxInclusive)
      value.successNel
    else InvalidLength(value, minInclusive.getOrElse(0), maxInclusive).failureNel

  def validateKnownRecordTypes(
      types: Set[RecordType]): ValidationNel[DomainValidationError, Set[RecordType]] =
    types.toList.traverseU(r => validateKnownRecordType(r)).map(x => x.toSet[RecordType])

  def validateKnownRecordType(rType: RecordType): ValidationNel[DomainValidationError, RecordType] =
    rType match {
      case UNKNOWN => InvalidRecordType(rType.toString).failureNel
      case _ => rType.successNel
    }

  def validateTrailingDot(value: String): ValidationNel[DomainValidationError, String] =
    if (value.endsWith(".")) value.successNel else InvalidDomainName(value).failureNel

  def validateTTL(ttl: Long): ValidationNel[DomainValidationError, Long] =
    if (ttl >= TTL_MIN_LENGTH && ttl <= TTL_MAX_LENGTH) ttl.successNel
    else InvalidTTL(ttl).failureNel[Long]

  def validateTxtTextLength(value: String): ValidationNel[DomainValidationError, String] =
    validateStringLength(value, Some(TXT_TEXT_MIN_LENGTH), TXT_TEXT_MAX_LENGTH)

  def validateMxPreference(pref: Int): ValidationNel[DomainValidationError, Int] =
    if (pref >= MX_PREFERENCE_MIN_VALUE && pref <= MX_PREFERENCE_MAX_VALUE) pref.successNel
    else InvalidMxPreference(pref).failureNel[Int]
}
