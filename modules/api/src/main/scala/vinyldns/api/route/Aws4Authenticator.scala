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

import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Mac, SecretKey}

import akka.http.scaladsl.model.HttpRequest
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

import scala.collection.SortedSet
import scala.collection.immutable.TreeMap
import scala.collection.mutable.MutableList
import scala.util.Try
import scala.util.matching.Regex

// $COVERAGE-OFF$
object Aws4Authenticator {

  val aws4AuthScheme = "AWS4-HMAC-SHA256"

  // seems okay to accept empty strings as elements of credentials
  val aws4CredsRegex = "([^/]*)/([0-9]{8}/[^/]*/[^/]*/aws4_request),"
  val aws4AuthRegex = new Regex(
    aws4AuthScheme +
      " *Credential=" + aws4CredsRegex +
      " *SignedHeaders=([^,]+)," +
      " *Signature=([a-f0-9]{64})$",
    "akid",
    "creds",
    "shs",
    "sig"
  )

  def parseAuthHeader(auth: String): Option[Regex.Match] = aws4AuthRegex.findPrefixMatchOf(auth)
}

case class MissingAuthenticationTokenException(msg: String) extends NoSuchElementException(msg)

class Aws4Authenticator {
  import vinyldns.api.route.Aws4Authenticator._

  type Credentials = String

  val iso8601Format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssz").withZone(ZoneId.of("UTC"))

  val rfc822Format = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z").withZone(ZoneId.of("UTC"))

  val logger = LoggerFactory.getLogger("Aws4Authenticator")

  def getDate(req: HttpRequest): Option[Instant] = {
    val xAmzDate = getHeader(req, "X-Amz-Date")
    val dateHeader = getHeader(req, "Date")

    if (xAmzDate.isDefined) {
      Try(ZonedDateTime.parse(xAmzDate.get, iso8601Format).toInstant).toOption
    } else {
      Try(ZonedDateTime.parse(dateHeader.get, iso8601Format).toInstant)
        .orElse(Try(ZonedDateTime.parse(dateHeader.get, rfc822Format).toInstant))
        .toOption
    }
  }

  def getAuthHeader(req: HttpRequest): String =
    req.headers.find(_.name.toLowerCase == "authorization").get.value
  def getHeader(req: HttpRequest, name: String): Option[String] =
    req.headers.find(_.name.toLowerCase == name.toLowerCase).map(_.value)

  def extractAccessKey(header: String): String = {
    val authorization = parseAuthHeader(header)
    authorization match {
      case Some(auth) if !auth.group("akid").isEmpty => auth.group("akid")
      case Some(_) => throw new MissingAuthenticationTokenException("accessKey not found")
      case None => throw new IllegalArgumentException("Invalid authorization header")
    }
  }

  // actual authentication
  def authenticateReq(
      req: HttpRequest,
      authorization: List[String],
      secret: String,
      content: String
  ): Boolean = {
    val List(
      _,
      signatureScope, // signature scope
      signatureHeaders, // signed headers
      signatureReceived
    ) = authorization
    val signedHeaders = Set() ++ signatureHeaders.split(';')
    // convert Date header to canonical form required by AWS
    val dateTime = iso8601Format.format(getDate(req).get).replace("UTC", "Z")
    // get canonical headers, but only those that were in the signed set
    val headers = canonicalHeaders(req, signedHeaders).toSeq
    // create a canonical representation of the request
    val canonicalRequest = canonicalReq(req, headers, signatureHeaders, content)
    // calculate the sig using the generated signing key
    val signature = calculateSig(canonicalRequest, dateTime, signatureScope, secret)

    if (logger.isDebugEnabled) {
      logger.debug(
        s"""SIGNATURE_SCOPE: $signatureScope
           |SIGNATURE_HEADERS: $signatureHeaders
           |SIGNED_HEADERS: $signedHeaders
           |DATE_TIME: $dateTime
           |HEADERS: $headers
           |CANONICAL_REQUEST: $canonicalRequest
           |SIGNATURE_RECEIVED: $signatureReceived
           |CALCULATED_SIGNATURE: $signature"""
        .stripMargin
      )
    }

    signature.equals(signatureReceived)
  }

  // XXX - need to canonicalize non-trimmed white space
  def canonicalHeaders(
      req: HttpRequest,
      signedHeaderNames: Set[String]
  ): TreeMap[String, Seq[String]] = {
    def getHeaderValue(name: String): Option[String] = name match {
      case "content-type" => Some(req.entity.contentType.value)
      case "content-length" => req.entity.contentLengthOption.map(_.toString)
      case _ => req.headers.find(_.name.toLowerCase == name).map(_.value)
    }

    val filteredHeaders =
      signedHeaderNames.map(_.toLowerCase).foldLeft(Seq.empty[(String, Seq[String])]) {
        (acc, cur) =>
          getHeaderValue(cur) match {
            case Some(found) => acc :+ (cur -> Seq(found))
            case None =>
              // This is a problem, so log loudly, and just continue
              logger.error(s"Unable to find signed header value: '$cur'")
              acc
          }
      }

    // TreeMap ensures headers are sorted by key
    new TreeMap[String, Seq[String]]() ++ filteredHeaders.toMap
  }

  def calculateSig(creq: String, dateTime: String, scope: String, secret: String): String = {
    val stringToSign = joinStrings('\n', aws4AuthScheme, dateTime, scope, hashString(creq))

    hexString(
      runHmac(Mac.getInstance(hmacAlgorithm))(signingKey(secret, scope).getEncoded, stringToSign)
    )
  }

  /**
    * Following the example Java signing key derivation code:
    *
    * http://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html
    */
  private val hmacAlgorithm = "HmacSHA256"

  private def runHmac(hmac: Mac)(key: Array[Byte], data: String) = {
    val k = new SecretKeySpec(key, hmacAlgorithm)
    hmac.init(k)
    hmac.doFinal(data.getBytes("UTF8"))
  }

  private def signingKey(secret: String, scope: String): SecretKey = {
    val key0 = ("AWS4" + secret).getBytes("UTF8")
    val inputs = scope.split("/")
    val nextHmac = runHmac(Mac.getInstance(hmacAlgorithm)) _
    new SecretKeySpec(inputs.foldLeft(key0)(nextHmac), hmacAlgorithm)
  }

  private def sortQueryParams(qstr: Map[String, Seq[String]]): String =
    if (qstr.nonEmpty) {
      val queryParams = SortedSet[(String, String)]() ++ (for {
        k <- qstr.keys
        v <- qstr(k).sorted
      } yield (encode(k), encode(v)))

      queryParams.map({ case (k, v) => joinStrings('=')(k, v) }).reduce(joinStrings('&'))
    } else {
      ""
    }

  private def canonicalURI(req: HttpRequest) = {
    val path = new java.net.URI("http", "localhost", req.uri.path.toString(), null)
    val queryString = sortQueryParams(req.uri.query().toMultiMap)

    List(path.normalize.getPath, queryString)
  }

  type CanonicalHeaders = Seq[(String, Seq[String])]

  def canonicalReq(
      req: HttpRequest,
      hdrs: CanonicalHeaders,
      signedHeaders: String,
      content: String
  ): String = {
    val lines = MutableList[String](req.method.value) ++ canonicalURI(req)

    // add canonical headers
    lines ++= {
      for (kv <- hdrs;
        (k, vs) = kv) yield k + ":" + vs.reduceLeft(joinStrings(','))
    }
    // need an empty item after list of headers
    lines += ""

    // add (sorted) list of headers that are signed
    lines += signedHeaders

    // add hash of body
    lines += hashBytes(content.getBytes("UTF-8"))

    lines.reduceLeft(joinStrings('\n'))
  }

  // useful helper functions
  private def encode(s: String) =
    // AWS signature spec treats space and tilde differently than URLEncoder
    URLEncoder
      .encode(s, "UTF-8")
      .
      // AWS encodes space as '%20' rather than '+'
      replaceAllLiterally("+", "%20")
      .
      // and doesn't encode '~' at all
      replaceAllLiterally("%7E", "~")
      .replaceAllLiterally("*", "%2A") // aws encodes the asterisk specially

  private def hexString(bs: Array[Byte]) =
    bs.foldLeft("")((out, b) => f"$out%s${b & 0x0ff}%02x")

  private def hashString(s: String) = hashBytes(s.getBytes("UTF-8"))

  private def hashBytes(bs: Array[Byte]) =
    hexString(MessageDigest.getInstance("SHA-256").digest(bs))

  private def joinStrings(c: Char)(s1: String, s2: String): String =
    s1 + c + s2

  private def joinStrings(c: Char, ss: String*): String =
    ss.reduceLeft(joinStrings(c))
}
// $COVERAGE-ON$
