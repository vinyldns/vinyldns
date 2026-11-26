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

import api, { urlBuilder } from './api';
import type { UserProfile, UserSearchResult } from '../types/profile';

export const profileService = {
  getAuthenticatedUserData() {
    return api.get<UserProfile>('/users/currentuser');
  },

  getUserDataByUsername(username: string) {
    return api.get<UserProfile>(`/users/lookupuser/${username}`);
  },

  getUserDataById(userId: string) {
    return api.get<UserProfile>(`/users/${userId}`);
  },

  searchUsersByName(pattern: string) {
    return api.get<UserSearchResult[]>(urlBuilder('/users/search', { pattern }));
  },

  // /regenerate-creds has no /api/ prefix in Play's routes — use fetch with absolute path
  async regenerateCredentials() {
    const res = await fetch('/regenerate-creds', { method: 'POST', credentials: 'include' });
    if (!res.ok) throw new Error('Failed to regenerate credentials');
    return res;
  },
};
