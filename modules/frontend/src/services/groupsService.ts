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
import type { Group, GroupListResponse, GroupMember, GroupChangesResponse } from '../types/group';

export const groupsService = {
  createGroup(data: Partial<Group>) {
    return api.post<Group>('/groups', data);
  },

  getGroup(id: string) {
    return api.get<Group>(`/groups/${id}`);
  },

  listEmailDomains() {
    return api.get<string[]>('/groups/valid/domains');
  },

  deleteGroup(id: string) {
    return api.delete(`/groups/${id}`);
  },

  updateGroup(id: string, data: Partial<Group>) {
    return api.put<Group>(`/groups/${id}`, data);
  },

  getGroupMemberList(groupId: string) {
    const url = urlBuilder(`/groups/${groupId}/members`, { maxItems: 1000 });
    return api.get<{ members: GroupMember[] }>(url);
  },

  addGroupMember(groupId: string, memberId: string, data: Partial<GroupMember>) {
    return api.put(`/groups/${groupId}/members/${memberId}`, data);
  },

  deleteGroupMember(groupId: string, memberId: string) {
    return api.delete(`/groups/${groupId}/members/${memberId}`);
  },

  getGroups(
    ignoreAccess?: boolean,
    query?: string,
    maxItems?: number
  ) {
    const params = {
      maxItems,
      groupNameFilter: query || undefined,
      ignoreAccess,
    };
    return api.get<GroupListResponse>(urlBuilder('/groups', params));
  },

  getGroupsAbridged(
    limit?: number,
    startFrom?: string,
    ignoreAccess?: boolean,
    query?: string
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      groupNameFilter: query || undefined,
      ignoreAccess,
      abridged: true,
    };
    return api.get<GroupListResponse>(urlBuilder('/groups', params));
  },

  getGroupChanges(groupId: string, count?: number, startFrom?: string) {
    const params = { startFrom, maxItems: count };
    return api.get<GroupChangesResponse>(
      urlBuilder(`/groups/${groupId}/groupchanges`, params)
    );
  },
};
