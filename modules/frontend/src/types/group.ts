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

export interface GroupMember {
  id: string;
  userName?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  created?: string;
  lockStatus?: string;
}

export interface Group {
  id: string;
  name: string;
  email: string;
  description?: string;
  status?: string;
  created?: string;
  members: GroupMember[];
  admins: GroupMember[];
}

export interface GroupListResponse {
  groups: Group[];
  startFrom?: string;
  nextId?: string;
  maxItems: number;
  ignoreAccess?: boolean;
}

export interface GroupChange {
  newGroup?: Group;
  oldGroup?: Group;
  changeType: string;
  userId: string;
  id: string;
  created: string;
}

export interface GroupChangesResponse {
  changes: GroupChange[];
  startFrom?: string;
  nextId?: string;
  maxItems: number;
}
