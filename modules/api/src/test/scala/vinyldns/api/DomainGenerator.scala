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

import org.scalacheck.Gen.{alphaNumChar, choose, frequency, listOfN}
import org.scalacheck._
import vinyldns.api.domain.DomainValidations

object DomainGenerator {
  val alphaNumComponent: Gen[String] = for {
    charCount <- choose(1, 63)
    component <- listOfN(charCount, alphaNumChar).map(_.mkString)
  } yield component

  val dashSlashAlphaNumGen: Gen[Char] = frequency((6, alphaNumChar), (2, '-'), (2, '/'))

  val dashSlashAlphaNumComponent: Gen[String] = for {
    firstChar <- listOfN(1, alphaNumChar).map(_.mkString)
    middleCharsCount <- choose(0, 61)
    middleChars <- listOfN(middleCharsCount, dashSlashAlphaNumGen).map(_.mkString)
    lastChar <- listOfN(1, alphaNumChar).map(_.mkString)
  } yield firstChar + middleChars + lastChar

  val domainComponentGenerator: Gen[String] = for {
    component <- frequency((5, alphaNumComponent), (5, dashSlashAlphaNumComponent))
  } yield component

  val domainGenerator: Gen[String] = for {
    domainComponentCount <- choose(1, 50)
    domainName <- listOfN(domainComponentCount, domainComponentGenerator).map(_.mkString("."))
    validDomainName <- shortenDomainToValidLength(domainName + ".")
  } yield validDomainName

  def shortenDomainToValidLength(domain: String): String = {
    val shortEnough = domain.take(DomainValidations.HOST_MAX_LENGTH)
    val lastDot = shortEnough.lastIndexOf(".") + 1
    domain.take(lastDot)
  }
}
