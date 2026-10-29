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

/** Values from the Play configuration passed into the React app HTML. */

/// <reference types="vite/client" />

export interface PortalLink {
  displayOnSidebar: boolean;
  displayOnLoginScreen: boolean;
  title: string;
  href: string;
  icon: string;
}

export const portalConfig = {
  version: __APP_VERSION__,
  get links(): PortalLink[] {
    return window.__VINYLDNS_PORTAL_LINKS__ ?? [];
  },
} as const;
