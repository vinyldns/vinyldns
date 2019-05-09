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

package vinyldns.client.models.batch

import vinyldns.client.models.OptionRW
import vinyldns.client.models.record.RecordData
import upickle.default._
import vinyldns.core.domain.record.RecordType

import scala.util.{Failure, Success, Try}

case class SingleChangeCreateInfo(
    inputName: String,
    changeType: String,
    `type`: String,
    ttl: Option[Int] = None,
    record: Option[RecordData] = None,
    errors: Option[List[String]] = None
)

object SingleChangeCreateInfo extends OptionRW {
  def apply(): SingleChangeCreateInfo =
    new SingleChangeCreateInfo("", "Add", "A+PTR", Some(300), Some(RecordData()), None)

  val validRecordTypes = List(
    "A+PTR", // +PTR will be converted before posting the batch change
    "AAAA+PTR", // +PTR will be converted before posting the batch change
    RecordType.A.toString,
    RecordType.AAAA.toString,
    RecordType.CNAME.toString,
    RecordType.PTR.toString,
    RecordType.TXT.toString,
    RecordType.MX.toString
  )

  val csvHeaders = "Change Type,Record Type,Input Name,TTL,Record Data"

  def toRecordDataDisplay(change: SingleChangeCreateInfo): String = {
    val recordData = change.record match {
      case Some(r) => r
      case None => RecordData()
    }
    change.`type` match {
      case address
          if address == "A" || address == "A+PTR" || address == "AAAA" || address == "AAAA+PTR" =>
        recordData.addressToString
      case cname if cname == "CNAME" => recordData.cnameToString
      case ptrdname if ptrdname == "PTR" => recordData.ptrdnameToString
      case text if text == "TXT" => recordData.textToString
      case _ => ""
    }
  }

  def toCsv(changes: List[SingleChangeCreateInfo]): String = {
    val stringBuilder = new StringBuilder()
    stringBuilder.append(s"$csvHeaders\n")
    changes.filterNot(_.`type` == "MX").map { row =>
      val rowToString =
        s"""
           |${row.changeType},
           |${row.`type`},
           |${row.inputName},
           |${Try(row.ttl.get.toString).getOrElse("")},
           |${toRecordDataDisplay(row)}
           |""".stripMargin.replaceAll("\n", "")
      stringBuilder.append(s"$rowToString\n")
    }
    stringBuilder.toString()
  }

  /*
    Used when importing CSV files in batch create
   */
  def parseFromCsvRow(
      asString: String,
      rowNumber: Int): Either[Throwable, SingleChangeCreateInfo] = {
    //Headers: Change Type,Record Type,Input Name,TTL,Record Data
    val split = asString.split(",", -1)
    if (split.length != 5) {
      Left(new Throwable(s""""row $rowNumber: does not contain 5 columns""""))
    } else {
      val changeTypeRaw = split(0)
      val recordTypeRaw = split(1)
      val inputNameRaw = split(2)
      val ttlRaw = split(3)
      val recordDataRaw = split(4)

      for {
        changeType <- parseChangeType(changeTypeRaw, rowNumber)
        recordType <- parseRecordType(recordTypeRaw, rowNumber)
        inputName <- parseInputName(inputNameRaw)
        ttl <- parseTTL(ttlRaw, rowNumber)
        recordData <- parseRecordData(recordDataRaw, rowNumber, recordType)
      } yield SingleChangeCreateInfo(inputName, changeType, recordType, ttl, recordData, None)
    }
  }

  def parseChangeType(asString: String, rowNumber: Int): Either[Throwable, String] =
    asString.trim.stripLineEnd match {
      case add if add.toLowerCase == "add" =>
        Right("Add")
      case delete if delete.toLowerCase == "delete" || delete.toLowerCase == "deleterecordset" =>
        Right("DeleteRecordSet")
      case invalid =>
        Left(new Throwable(s""""row $rowNumber: invalid change type '$invalid'""""))
    }

  def parseRecordType(asString: String, rowNumber: Int): Either[Throwable, String] =
    asString.trim.stripLineEnd match {
      case _ if asString.toUpperCase == "MX" =>
        Left(new Throwable(s""""row $rowNumber: record type 'MX' cannot be done via csv""""))
      case valid if validRecordTypes.contains(valid.toUpperCase) =>
        Right(valid.toUpperCase)
      case invalid =>
        Left(new Throwable(s""""row $rowNumber: record type '$invalid' cannot be done via csv""""))
    }

  def parseInputName(asString: String): Either[Throwable, String] =
    Right(asString.trim.stripLineEnd)

  def parseTTL(asString: String, rowNumber: Int): Either[Throwable, Option[Int]] =
    asString.trim.stripLineEnd match {
      case empty if empty.isEmpty => Right(None)
      case filled =>
        Try(filled.toInt) match {
          case Success(i) => Right(Some(i))
          case Failure(_) =>
            Left(new Throwable(s""""row $rowNumber: TTL '$asString' must be an integer""""))
        }
    }

  def parseRecordData(
      asString: String,
      rowNumber: Int,
      recordTypeString: String): Either[Throwable, Option[RecordData]] = {
    val trimmed = asString.trim.stripLineEnd
    if (trimmed.isEmpty) Right(None)
    else {
      if (recordTypeString == "A+PTR" || recordTypeString == "AAAA+PTR")
        Right(Some(RecordData(address = Some(trimmed))))
      else
        Try(RecordType.withName(recordTypeString.toUpperCase.trim)) match {
          case Success(recordType) =>
            recordType match {
              case RecordType.AAAA => Right(Some(RecordData(address = Some(trimmed))))
              case RecordType.A => Right(Some(RecordData(address = Some(trimmed))))
              case RecordType.CNAME => Right(Some(RecordData(cname = Some(trimmed))))
              case RecordType.PTR => Right(Some(RecordData(ptrdname = Some(trimmed))))
              case RecordType.TXT => Right(Some(RecordData(text = Some(trimmed))))
              case _ =>
                Left(
                  new Throwable(
                    s""""row $rowNumber: recordType '$recordTypeString' cannot be done via CSV"""")
                )
            }
          case Failure(_) =>
            Left(new Throwable(s""""row $rowNumber: invalid record type '$recordTypeString'""""))
        }
    }
  }

  implicit val singleChangeCreateInfoRw: ReadWriter[SingleChangeCreateInfo] = macroRW
}
