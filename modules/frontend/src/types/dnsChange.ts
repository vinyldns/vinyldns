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

export type DnsChangeStatus =
  | 'Pending'
  | 'PendingReview'
  | 'Complete'
  | 'Failed'
  | 'Cancelled'
  | 'PartialFailure'
  | 'Scheduled';

export interface SingleChange {
  changeType: 'Add' | 'DeleteRecordSet' | 'DeleteRecord';
  inputName: string;
  type: string;
  ttl?: number;
  record?: Record<string, unknown>;
  status: string;
  recordName?: string;
  zoneName?: string;
  zoneId?: string;
  recordSetId?: string;
  errors?: string[];
  id: string;
  systemMessage?: string;
}

export interface DnsChange {
  id: string;
  userId: string;
  userName: string;
  comments?: string;
  createdTimestamp: string;
  changes: SingleChange[];
  status: DnsChangeStatus;
  ownerGroupId?: string;
  ownerGroupName?: string;
  approvalStatus?: string;
  reviewerId?: string;
  reviewerUserName?: string;
  reviewComment?: string;
  reviewTimestamp?: string;
  scheduledTime?: string;
}

export interface DnsChangeSummary {
  id: string;
  userId: string;
  userName: string;
  comments?: string;
  createdTimestamp: string;
  totalChanges: number;
  status: DnsChangeStatus;
  ownerGroupId?: string;
  ownerGroupName?: string;
  approvalStatus?: string;
  reviewerId?: string;
  reviewerUserName?: string;
  reviewComment?: string;
  reviewTimestamp?: string;
  scheduledTime?: string;
}

export interface DnsChangeListResponse {
  batchChanges: DnsChangeSummary[];
  startFrom?: number;
  nextId?: number;
  maxItems: number;
  ignoreAccess?: boolean;
}

export interface CreateDnsChangeRequest {
  comments?: string;
  changes: Omit<SingleChange, 'id' | 'status' | 'recordName' | 'zoneName' | 'zoneId' | 'recordSetId' | 'errors' | 'systemMessage'>[];
  ownerGroupId?: string;
  scheduledTime?: string;
}
