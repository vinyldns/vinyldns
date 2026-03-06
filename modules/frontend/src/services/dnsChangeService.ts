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
import type {
  DnsChange,
  DnsChangeListResponse,
  CreateDnsChangeRequest,
} from '../types/dnsChange';

const BASE = '/zones/batchrecordchanges';

export const dnsChangeService = {
  getBatchChange(id: string) {
    return api.get<DnsChange>(`${BASE}/${id}`);
  },

  createBatchChange(data: CreateDnsChangeRequest, allowManualReview?: boolean) {
    const url = urlBuilder(BASE, {
      allowManualReview: allowManualReview,
    });
    return api.post<DnsChange>(url, data);
  },

  getBatchChanges(
    maxItems?: number,
    startFrom?: number,
    ignoreAccess?: boolean,
    approvalStatus?: string,
    userName?: string,
    dateTimeRangeStart?: string,
    dateTimeRangeEnd?: string
  ) {
    const params = {
      maxItems,
      startFrom,
      ignoreAccess,
      approvalStatus: approvalStatus || undefined,
      userName: userName || undefined,
      dateTimeRangeStart: dateTimeRangeStart || undefined,
      dateTimeRangeEnd: dateTimeRangeEnd || undefined,
    };
    return api.get<DnsChangeListResponse>(urlBuilder(BASE, params));
  },

  cancelBatchChange(id: string) {
    return api.post(`${BASE}/${id}/cancel`, {});
  },

  approveBatchChange(id: string, reviewComment?: string) {
    const data = reviewComment ? { reviewComment } : {};
    return api.post(`${BASE}/${id}/approve`, data);
  },

  rejectBatchChange(id: string, reviewComment?: string) {
    const data = reviewComment ? { reviewComment } : {};
    return api.post(`${BASE}/${id}/reject`, data);
  },
};
