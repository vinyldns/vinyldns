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

import java.util.regex.Pattern
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * Exception thrown when regex matching exceeds deadline.
  */
class RegexTimeoutException(message: String = "Regex matching exceeded deadline")
    extends Exception(message)

/**
  * Wraps a CharSequence to enforce deadline checks during regex matching.
  * Throws RegexTimeoutException if deadline exceeded during charAt(), length(), or subSequence().
  * This is a practical mitigation against catastrophic backtracking without thread overhead.
  * Not a strict guarantee (some operations may occur between CharSequence calls) but very effective in practice.
  */
private final class DeadlineCharSequence(inner: CharSequence, deadlineNanos: Long) extends CharSequence {
  override def charAt(i: Int): Char = {
    if (System.nanoTime() >= deadlineNanos) {
      throw new RegexTimeoutException("Regex matching exceeded deadline")
    }
    inner.charAt(i)
  }

  override def length(): Int = {
    if (System.nanoTime() >= deadlineNanos) {
      throw new RegexTimeoutException("Regex matching exceeded deadline")
    }
    inner.length
  }

  override def subSequence(start: Int, end: Int): CharSequence = {
    if (System.nanoTime() >= deadlineNanos) {
      throw new RegexTimeoutException("Regex matching exceeded deadline")
    }
    new DeadlineCharSequence(inner.subSequence(start, end), deadlineNanos)
  }

  override def toString: String = inner.toString
}

/**
  * Safe regex matching with ReDoS timeout protection (CWE-400, CWE-1333).
  * Layered defense: storage-time validation (lint), 512-char limit, runtime deadline enforcement.
  * Runtime deadline via DeadlineCharSequence checks on charAt/length/subSequence calls.
  * Strong practical protection for real-world attacks without thread overhead or leakage.
  */
object RegexValidator {

  // Timeout for regex matching operations - balances security vs usability
  // Most legitimate regexes complete in milliseconds; 100ms is very generous
  private final val REGEX_MATCH_TIMEOUT = 100.millis

  // Maximum length for regex patterns to prevent excessively complex patterns
  private final val MAX_PATTERN_LENGTH = 512

  // Lint-level heuristics for obvious patterns; easily bypassed (overlapping alt, brace quantifiers, nested groups).
  // Runtime deadline is the actual protection.
  private final val DANGEROUS_PATTERN_INDICATORS = Seq(
    // Nested quantifiers: (a+)+, (a*)*. (a+)*, etc.
    """\(\?:[^)]*[*+]{1,2}\)[*+]""".r,        // (?:...)*+ or (?:...)+* etc
    """\([^)]*[*+]{1,2}\)[*+]""".r             // (...)*+ or (...)+* etc
  )

  /**
    * Match with timeout protection via DeadlineCharSequence deadline checks.
    * Returns false on timeout or error. Pattern.compile() not subject to timeout (512-char limit protects).
    */
  def safeMatches(subject: String, pattern: String): Boolean = {
    try {
      val deadlineNanos = System.nanoTime() + REGEX_MATCH_TIMEOUT.toNanos
      matchWithDeadlineNanos(subject, pattern, deadlineNanos)
    } catch {
      case _: RegexTimeoutException =>
        false
      case NonFatal(_) =>
        false
    }
  }

  /**
    * Test-only: Match with custom deadline; throws RegexTimeoutException on timeout (doesn't catch).
    * Allows tests to verify deadline enforcement actually triggers on pathological patterns.
    */
  private[access] def matchWithDeadlineNanos(
      subject: String,
      pattern: String,
      deadlineNanos: Long
  ): Boolean = {
    val deadlineSubject = new DeadlineCharSequence(subject, deadlineNanos)
    Pattern.compile(pattern).matcher(deadlineSubject).matches()
  }

  /**
    * Lint-level storage-time validation: rejects obvious patterns and oversized patterns.
    * Runtime deadline checks help limit excessive regex matching time.
    * This is a practical mitigation, not a strict guarantee for every regex operation.
    */
  def validatePattern(pattern: String): Unit = {
    if (pattern != null && pattern.nonEmpty) {
      if (pattern.length > MAX_PATTERN_LENGTH) {
        throw new IllegalArgumentException(
          s"Regex pattern exceeds maximum length of $MAX_PATTERN_LENGTH characters (length: ${pattern.length}). " +
          "This restriction prevents excessively complex patterns."
        )
      }

      // Check for dangerous pattern indicators
      DANGEROUS_PATTERN_INDICATORS.foreach { indicator =>
        if (indicator.findFirstIn(pattern).isDefined) {
          throw new IllegalArgumentException(
            s"Regex pattern contains potentially dangerous construct that could cause excessive backtracking: $pattern. " +
            "Nested quantifiers, excessive alternation, and complex combinations are not allowed."
          )
        }
      }

      try {
        // This will throw PatternSyntaxException if invalid
        java.util.regex.Pattern.compile(pattern)
      } catch {
        case ex: java.util.regex.PatternSyntaxException =>
          throw new IllegalArgumentException(
            s"Invalid regex pattern: ${ex.getMessage}",
            ex
          )
      }
    }
  }
}

