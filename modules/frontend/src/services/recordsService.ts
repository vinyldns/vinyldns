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

import api, { urlBuilder } from "./api";
import type {
  RecordSet,
  RecordSetListResponse,
  RecordSetChangesResponse,
} from "../types/record";

export const recordsService = {
  /**
   * Global record set search — returns records across all zones.
   * Used by the RecordSet Search page; `nameFilter` drives the FQDN search
   * and `nameSort` controls ASC/DESC ordering of results.
   */
  listRecordSetData(
    limit: number,
    startFrom?: string,
    nameFilter?: string,
    typeFilter?: string,
    nameSort?: string,
    ownerGroupFilter?: string,
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      recordNameFilter: nameFilter || undefined,
      recordTypeFilter: typeFilter || undefined,
      nameSort: nameSort || undefined,
      recordOwnerGroupFilter: ownerGroupFilter || undefined,
    };
    return api.get<RecordSetListResponse>(urlBuilder("/recordsets", params));
  },

  /**
   * Zone-scoped record listing used by the Zone Detail page.
   * Supports both name and type filtering independently; `recordTypeSort`
   * is a secondary sort key that the global search endpoint does not expose.
   */
  listRecordSetsByZone(
    zoneId: string,
    limit: number,
    startFrom?: string,
    nameFilter?: string,
    typeFilter?: string,
    nameSort?: string,
    recordTypeSort?: string,
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
      urlBuilder(`/zones/${zoneId}/recordsets`, params),
      {
        headers: {
          'Cache-Control': 'no-cache',
          Pragma: 'no-cache',
        },
      },
    );
  },

  getRecordSet(zoneId: string, recordSetId: string) {
    return api.get<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets/${recordSetId}`,
    );
  },

  createRecordSet(zoneId: string, data: Partial<RecordSet>) {
    return api.post<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets`,
      data,
    );
  },

  updateRecordSet(
    zoneId: string,
    recordSetId: string,
    data: Partial<RecordSet>,
  ) {
    return api.put<{ recordSet: RecordSet }>(
      `/zones/${zoneId}/recordsets/${recordSetId}`,
      data,
    );
  },

  deleteRecordSet(zoneId: string, recordSetId: string) {
    return api.delete(`/zones/${zoneId}/recordsets/${recordSetId}`);
  },

  getRecordSetChanges(zoneId: string, limit: number, startFrom?: string) {
    const params = { maxItems: limit, startFrom };
    return api.get<RecordSetChangesResponse>(
      urlBuilder(`/zones/${zoneId}/recordsetchanges`, params),
    );
  },

  getRecordSuggestions(term: string) {
    // `/recordsets` doubles as the typeahead source; `maxItems: 10` keeps
    // the dropdown snappy without pulling an unbounded result set.
    const params = { recordNameFilter: term, maxItems: 10 };
    return api.get<RecordSetListResponse>(urlBuilder("/recordsets", params));
  },

  /**
   * Fetches paginated change history for a specific record set.
   * The API endpoint uses a global `/recordsetchange/history` path rather
   * than a zone-scoped one, so `zoneId` is passed as a query param instead
   * of a path segment. `fqdn` and `recordType` narrow results to a single
   * record set when provided.
   */
  listRecordSetChangeHistory(
    zoneId: string,
    limit: number,
    startFrom?: string,
    fqdn?: string,
    recordType?: string,
  ) {
    const params = {
      zoneId: zoneId || undefined,
      maxItems: limit,
      startFrom,
      fqdn: fqdn || undefined,
      recordType: recordType || undefined,
    };
    return api.get<{ recordSetChanges: any[]; nextId?: number }>(
      urlBuilder(`/recordsetchange/history`, params),
    );
  },
};
