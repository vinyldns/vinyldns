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

/** Tests for the build version and Play-injected portal links. */

import { describe, it, expect } from 'vitest';

describe('portalConfig', () => {
  describe('version', () => {
    it('exports a non-empty version string', async () => {
      const { portalConfig } = await import('../../config/portalConfig');
      expect(typeof portalConfig.version).toBe('string');
      expect(portalConfig.version.length).toBeGreaterThan(0);
    });
  });

  describe('links', () => {
    it('reads link data injected by the Play server', async () => {
      window.__VINYLDNS_PORTAL_LINKS__ = [
        {
          displayOnSidebar: true,
          displayOnLoginScreen: false,
          title: 'Portal Guide',
          href: 'https://docs.example.com/portal',
          icon: 'fa fa-book',
        },
      ];

      const { portalConfig } = await import('../../config/portalConfig');
      expect(portalConfig.links).toEqual(window.__VINYLDNS_PORTAL_LINKS__);
    });
  });
});
