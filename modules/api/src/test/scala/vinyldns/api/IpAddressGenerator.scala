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

import org.scalacheck.Gen
import org.scalacheck.Gen.{choose, frequency, listOfN}

object IpAddressGenerator {
  val ipNumber: Gen[String] = choose(0, 255).map(_.toString)
  val validIpv4Gen: Gen[String] = for {
    num1 <- ipNumber
    num2 <- ipNumber
    num3 <- ipNumber
    num4 <- ipNumber
  } yield num1 + "." + num2 + "." + num3 + "." + num4

  val ipv6AlphaNum = Gen.oneOf("abcdefABCDEF0123456789".toCharArray)

  val ipv6Hextet: Gen[String] = {
    for {
      num <- Gen.choose(1, 4)
      chars <- Gen.listOfN(num, ipv6AlphaNum)
    } yield chars.mkString
  }

  val validIpv6Gen: Gen[String] = listOfN(8, ipv6Hextet).map(_.mkString(":"))

  val outOfRangeNumber: Gen[String] = choose(256, 512).map(_.toString)
  val outOfRangeIp: Gen[String] = for {
    num1 <- outOfRangeNumber
    num2 <- outOfRangeNumber
    num3 <- outOfRangeNumber
    num4 <- outOfRangeNumber
  } yield num1 + "." + num2 + "." + num3 + "." + num4

  val varyingIpComponents: Gen[String] = listOfN(4, ipNumber).map(_.toString + ".")
  val invalidIpGen: Gen[String] = frequency((5, outOfRangeIp), (5, varyingIpComponents))
}
