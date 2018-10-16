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

package vinyldns.benchmark
import scala.util.Random

object NameGenerators {

  // Random words that we will use for record names
  val rList1 =
    Seq("fresh", "wrong", "nippy", "dusty", "loose", "gabby", "picky", "flaky", "weary", "curly")
  val rList2 = Seq("sofa", "boat", "baby", "skin", "bell", "army", "drug", "fish", "duck", "food")

  // Random words that we will use for zone names
  val zoneName = Seq(
    "floaty",
    "mythos",
    "uproot",
    "alcade",
    "octroi",
    "hosier",
    "pixies",
    "helice",
    "stager",
    "logier")
  val zoneTLD = Seq("net", "com", "foo", "bar", "pop", "mom", "you", "can", "dan", "she")

  val rnd = new Random
  def generateRecordName(idx: Int): String = {
    val r1 = rnd.nextInt(10)
    val r2 = rnd.nextInt(10)

    "r" + idx + "-" + rList1(r1) + "-" + rList2(r2)
  }

  val zoneRnd = new Random
  def generateZoneName(zoneSize: ZoneSize, idx: Int): String = {
    val r1 = zoneRnd.nextInt(10)
    val r2 = rnd.nextInt(10)
    val sb = new StringBuilder

    // Important, zone name follows z.xl.100.floaty.net., as subsequent processing depends on this zone name
    sb.append("z.")
      .append(zoneSize.value)
      .append(".")
      .append(idx)
      .append(".")
      .append(zoneName(r1))
      .append(".")
      .append(zoneTLD(r2))
      .append(".")
    sb.toString
  }

}
