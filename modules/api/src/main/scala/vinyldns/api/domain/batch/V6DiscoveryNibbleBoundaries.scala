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

package vinyldns.api.domain.batch

import vinyldns.api.VinylDNSConfig.VinylDNSConfigLoadError

final case class V6DiscoveryNibbleBoundaries(min: Int, max: Int)

object V6DiscoveryNibbleBoundaries {
  def apply(min: Int, max: Int): Either[VinylDNSConfigLoadError, V6DiscoveryNibbleBoundaries] =
    if (min < 1) {
      Left(VinylDNSConfigLoadError(s"v6zoneNibbleMin ($min) cannot be less than 1"))
    } else if (max > 32) {
      Left(VinylDNSConfigLoadError(s"v6zoneNibbleMax ($max) cannot be greater than 32"))
    } else if (min > max) {
      Left(
        VinylDNSConfigLoadError(
          s"v6zoneNibbleMin ($min) cannot be greater than v6zoneNibbleMax ($max)"))
    } else {
      Right(new V6DiscoveryNibbleBoundaries(min, max))
    }
}
