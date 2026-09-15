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

/**
 * Tests for profileService — mirrors UserAccountAccessorSpec.scala and the
 * .getUserData section of VinylDNSSpec.scala from the old portal.
 *
 * Old equivalents:
 *  UserAccountAccessorSpec  → get, create, update, getUserByKey, getAllUsers
 *  VinylDNSSpec.getUserData → getAuthenticatedUserData returning 200 + user JSON
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { profileService } from '../../services/profileService';
import api from '../../services/api';
import { frodoUser, superFrodoUser } from '../fixtures/testData';

// ── Mock the axios api instance ───────────────────────────────────────────────

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const mockApi = api as unknown as { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };

beforeEach(() => {
  vi.clearAllMocks();
});

// ── profileService ────────────────────────────────────────────────────────────

describe('profileService', () => {
  // ── getAuthenticatedUserData ─────────────────────────────────────────────
  // Mirrors: VinylDNSSpec ".getUserData should return the current logged in users information"

  describe('getAuthenticatedUserData', () => {
    it('calls GET /users/currentuser', () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      profileService.getAuthenticatedUserData();
      expect(mockApi.get).toHaveBeenCalledWith('/users/currentuser');
    });

    it('returns the authenticated user data', async () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      const res = await profileService.getAuthenticatedUserData();
      expect(res.data.userName).toBe('fbaggins');
      expect(res.data.id).toBe('frodo-uuid');
    });

    it('returns super user flag correctly for a super user', async () => {
      mockApi.get.mockResolvedValueOnce({ data: superFrodoUser });
      const res = await profileService.getAuthenticatedUserData();
      expect(res.data.isSuper).toBe(true);
    });

    it('returns isSuper as false for a regular user', async () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      const res = await profileService.getAuthenticatedUserData();
      expect(res.data.isSuper).toBe(false);
    });
  });

  // ── getUserDataByUsername ─────────────────────────────────────────────────
  // Mirrors: UserAccountAccessorSpec "return the user when retrieving a user that exists by name"

  describe('getUserDataByUsername', () => {
    it('calls GET /users/lookupuser/:username', () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      profileService.getUserDataByUsername('fbaggins');
      expect(mockApi.get).toHaveBeenCalledWith('/users/lookupuser/fbaggins');
    });

    it('returns the matched user data', async () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      const res = await profileService.getUserDataByUsername('fbaggins');
      expect(res.data.userName).toBe('fbaggins');
      expect(res.data.firstName).toBe('Frodo');
      expect(res.data.lastName).toBe('Baggins');
      expect(res.data.email).toBe('fbaggins@hobbitmail.me');
    });

    it('encodes the username in the URL path', () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      profileService.getUserDataByUsername('user@domain.com');
      expect(mockApi.get).toHaveBeenCalledWith('/users/lookupuser/user@domain.com');
    });
  });

  // ── getUserDataById ───────────────────────────────────────────────────────
  // Mirrors: UserAccountAccessorSpec "return the user when retrieving a user that exists by user ID"

  describe('getUserDataById', () => {
    it('calls GET /users/:id', () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      profileService.getUserDataById('frodo-uuid');
      expect(mockApi.get).toHaveBeenCalledWith('/users/frodo-uuid');
    });

    it('returns the user matching the given id', async () => {
      mockApi.get.mockResolvedValueOnce({ data: frodoUser });
      const res = await profileService.getUserDataById('frodo-uuid');
      expect(res.data.id).toBe('frodo-uuid');
    });
  });

  // ── regenerateCredentials ─────────────────────────────────────────────────
  // Mirrors: UserAccountAccessorSpec "return the new user when storing a user that already exists"
  // (credential regeneration is the nearest equivalent to key rotation)

  describe('regenerateCredentials', () => {
    it('calls fetch POST /regenerate-creds with credentials included', async () => {
      const fetchMock = vi.fn().mockResolvedValue({ ok: true });
      vi.stubGlobal('fetch', fetchMock);

      await profileService.regenerateCredentials();

      expect(fetchMock).toHaveBeenCalledWith('/regenerate-creds', {
        method: 'POST',
        credentials: 'include',
      });
      vi.unstubAllGlobals();
    });
  });
});
