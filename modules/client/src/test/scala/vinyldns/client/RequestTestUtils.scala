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

package vinyldns.client

import org.scalamock.scalatest.MockFactory

trait RequestTestUtils extends MockFactory {
  val XHR_COMPLETE = 4

//  def groupListAjaxResponse(groupList: GroupList): Step2 = {
//    val testXhr = mock[XMLHttpRequest]
//    (testXhr.readyState _).expects().returns(XHR_COMPLETE)
//    (testXhr.status _).expects().returns(202)
//    (testXhr.statusText _).expects().returns("OK")
//    (testXhr.responseText _).expects().returns(write(groupList))
//
//
//    (response.onComplete _).expects(*).onCall { f: (XMLHttpRequest => Callback) =>
//      f.apply(testXhr)
//      response
//    }
//    response
//  }
}
