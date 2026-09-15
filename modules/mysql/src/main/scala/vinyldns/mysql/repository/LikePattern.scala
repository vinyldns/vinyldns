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

package vinyldns.mysql.repository

/**
 * Builds SQL LIKE patterns from user-supplied name filters.
 *
 * '*' is the only user-facing wildcard and is mapped to SQL '%'. Any literal
 * LIKE metacharacters in the input ('%', '_') and the escape character ('\')
 * are backslash-escaped so they match literally. The resulting pattern must be
 * bound as a parameter and paired with `LIKE ... ESCAPE '\\'` at the call site.
 */
object LikePattern {

  private val Escape = "\\"

  /**
   * Escapes literal LIKE metacharacters in `input` and maps the user-facing
   * wildcard '*' to SQL '%'. The escape character is doubled first so the
   * backslashes introduced for '%'/'_' are not themselves re-escaped.
   */
  def escape(input: String): String =
    input
      .replace(Escape, Escape + Escape)
      .replace("%", Escape + "%")
      .replace("_", Escape + "_")
      .replace('*', '%')

  /**
   * Pattern for name searches: when the user supplies a '*' wildcard the
   * translated pattern is used as-is; otherwise a trailing '%' is appended for
   * a prefix match.
   */
  def prefix(input: String): String =
    if (input.contains('*')) escape(input) else escape(input) + "%"
}
