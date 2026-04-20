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
  RecordSet,
  RecordSetListResponse,
  RecordSetChangesResponse,
} from '../types/record';

export const recordsService = {
  listRecordSetData(
    limit: number,
    startFrom?: string,
    nameFilter?: string,
    typeFilter?: string,
    nameSort?: string,
    ownerGroupFilter?: string
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      recordNameFilter: nameFilter || undefined,
      recordTypeFilter: typeFilter || undefined,
      nameSort: nameSort || undefined,
      recordOwnerGroupFilter: ownerGroupFilter || undefined,
    };
    return api.get<RecordSetListResponse>(urlBuilder('/recordsets', params));
  },

  listRecordSetsByZone(
    zoneId: string,
    limit: number,
    startFrom?: string,
    nameFilter?: string,
    typeFilter?: string,
    nameSort?: string,
    recordTypeSort?: string
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      recordNameFilter: nameFilter || undefined,
      recordTypeFilter: typeFilter || undefined,
      nameSort: nameSort || undefined,
      recordTypeSort: recordTypeSort || undefined,
    };
    return api.get<RecordSetListResponse>(
      urlBuilder(`/zones/${zoneId}/recordsets`, params)
    );
  },

  getRecordSet(zoneId: string, recordSetId: string) {
    return api.get<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets/${recordSetId}`
    );
  },

  createRecordSet(zoneId: string, data: Partial<RecordSet>) {
    return api.post<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets`,
      data
    );
  },

  updateRecordSet(zoneId: string, recordSetId: string, data: Partial<RecordSet>) {
    return api.put<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets/${recordSetId}`,
      data
    );
  },

  deleteRecordSet(zoneId: string, recordSetId: string) {
    return api.delete(`/zones/${zoneId}/recordsets/${recordSetId}`);
  },

  getRecordSetChanges(
    zoneId: string,
    limit: number,
    startFrom?: string
  ) {
    const params = { maxItems: limit, startFrom };
    return api.get<RecordSetChangesResponse>(
      urlBuilder(`/zones/${zoneId}/recordsetchanges`, params)
    );
  },
};
