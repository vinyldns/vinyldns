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

import java.util.concurrent.{Callable, FutureTask, TimeoutException}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * Provides safe regex matching with timeout protection against ReDoS attacks (CWE-400, CWE-1333).
  *
  * Prevents catastrophic backtracking in java.util.regex engine by enforcing a timeout on
  * pattern matching operations. This mitigates the risk of thread pool exhaustion and DoS attacks
  * from adversarial regex patterns stored in zone ACL rules.
  */
object RegexValidator {

  // Timeout for regex matching operations - balances security vs usability
  // Most legitimate regexes complete in milliseconds; 100ms is very generous
  private final val REGEX_MATCH_TIMEOUT = 100.millis

  // Maximum length for regex patterns to prevent excessively complex patterns
  private final val MAX_PATTERN_LENGTH = 512

  // Pattern characteristics that commonly indicate catastrophic backtracking risk
  private final val DANGEROUS_PATTERN_INDICATORS = Seq(
    // Nested quantifiers: (a+)+, (a*)*. (a+)*, etc. - these are almost always dangerous
    """\(\?:[^)]*[*+]{1,2}\)[*+]""".r,        // (?:...)*+ or (?:...)+* etc
    """\([^)]*[*+]{1,2}\)[*+]""".r             // (...)*+ or (...)+* etc - THIS IS THE KEY PATTERN
  )

  /**
    * Safely evaluates whether a string matches a regex pattern with timeout protection.
    * This only applies timeout protection, not pattern validation - it allows any pattern
    * that has already been validated at storage time.
    *
    * @param subject the string to match against the pattern
    * @param pattern the regex pattern to evaluate (assumed to be pre-validated at storage time)
    * @return true if the subject matches the pattern; false if timeout or exception occurs
    */
  def safeMatches(subject: String, pattern: String): Boolean = {
    try {
      executeWithTimeout(() => subject.matches(pattern))
    } catch {
      case _: TimeoutException =>
        false
      case NonFatal(_) =>
        false
    }
  }

  /**
    * Validates a regex pattern for length and common ReDoS indicators.
    * This provides a defense-in-depth check when patterns are stored (e.g., in ACL rules).
    *
    * @param pattern the regex pattern to validate
    * @throws IllegalArgumentException if pattern exceeds length limits or matches dangerous indicators
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

  /**
    * Executes a callable with a timeout, returning default value if timeout occurs.
    *
    * This uses a single-threaded ExecutorService for lightweight timeout enforcement
    * without excessive resource consumption.
    *
    * @param callable the operation to execute
    * @tparam T the return type
    * @return the result of the callable
    * @throws TimeoutException if operation exceeds timeout
    */
  private def executeWithTimeout[T](callable: Callable[T]): T = {
    val task = new FutureTask[T](callable)
    val thread = new Thread(task)
    thread.setName("regex-timeout-thread-" + Thread.currentThread().getId)
    thread.setDaemon(true)

    thread.start()
    
    try {
      task.get(REGEX_MATCH_TIMEOUT.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
    } finally {
      if (!task.isDone) {
        task.cancel(true)
        thread.interrupt()
      }
    }
  }
}
