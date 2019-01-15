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

package models
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

class MetaSpec extends Specification with Mockito {
  "Meta" should {
    "load from config" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).version must beEqualTo("foo-bar")
    }
    "default to false if shared_display_enabled is not found" in {
      val config = Map("vinyldns.version" -> "foo-bar")
      Meta(Configuration.from(config)).shared_display_enabled must beFalse
    }
    "set to true if shared_display_enabled is true in config" in {
      val config = Map("shared_display_enabled" -> true)
      Meta(Configuration.from(config)).shared_display_enabled must beTrue
    }
  }
}
