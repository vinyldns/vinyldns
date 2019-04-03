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

package vinyldns.client.router

// The AppRouter must route to a Page from this file
// Routes are dynamic, and can pull things like ints and strings from the route to build a case class
sealed trait Page

// home
object ToHomePage extends Page

// api credentials
object ToApiCredentialsPage extends Page

// 404
object ToNotFound extends Page

// group list
object ToGroupListPage extends Page

// group view
final case class ToGroupViewPage(id: String) extends Page

// zone list
object ToZoneListPage extends Page

// zone view
sealed trait ToZoneViewPage extends Page { def id: String }
final case class ToZoneViewRecordsTab(id: String) extends ToZoneViewPage
final case class ToZoneViewAccessTab(id: String) extends ToZoneViewPage
final case class ToZoneViewZoneTab(id: String) extends ToZoneViewPage
final case class ToZoneViewChangesTab(id: String) extends ToZoneViewPage
