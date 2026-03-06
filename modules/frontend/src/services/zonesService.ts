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
  Zone,
  ZoneListResponse,
  ZoneChangesResponse,
  DeletedZonesResponse,
} from '../types/zone';

/** Convert a display date back to API ISO format (drops milliseconds) */
function toApiIso(date: string): string {
  return new Date(date).toISOString().slice(0, 19) + 'Z';
}

function sanitize(obj: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(obj)) {
    if (value !== '' && value !== undefined && value !== null) {
      result[key] = value;
    }
  }
  return result;
}

function sanitizeConnections(payload: Zone): Partial<Zone> {
  const sanitized: Partial<Zone> = { ...payload };
  if (payload.connection) {
    const conn = sanitize(payload.connection as unknown as Record<string, unknown>);
    if (
      Object.keys(conn).length === 0 ||
      (Object.keys(conn).length === 1 && 'name' in conn)
    ) {
      delete sanitized.connection;
    } else {
      sanitized.connection = { ...conn, name: payload.name } as Zone['connection'];
    }
  }
  if (payload.transferConnection) {
    const conn = sanitize(payload.transferConnection as unknown as Record<string, unknown>);
    if (
      Object.keys(conn).length === 0 ||
      (Object.keys(conn).length === 1 && 'name' in conn)
    ) {
      delete sanitized.transferConnection;
    } else {
      sanitized.transferConnection = { ...conn, name: payload.name } as Zone['transferConnection'];
    }
  }
  return sanitized;
}

export const zonesService = {
  getZones(
    limit: number,
    startFrom?: string,
    query?: string,
    searchByAdminGroup?: boolean,
    ignoreAccess?: boolean,
    includeReverse?: boolean
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      nameFilter: query || undefined,
      searchByAdminGroup,
      ignoreAccess,
      includeReverse,
    };
    return api.get<ZoneListResponse>(urlBuilder('/zones', params));
  },

  getZone(id: string) {
    return api.get<{ zone: Zone }>(`/zones/${id}`);
  },

  getZoneByName(name: string) {
    return api.get<{ zone: Zone }>(`/zones/name/${name}`);
  },

  getZoneChanges(limit: number, startFrom: string | undefined, zoneId: string) {
    const params = { maxItems: limit, startFrom };
    return api.get<ZoneChangesResponse>(urlBuilder(`/zones/${zoneId}/changes`, params));
  },

  getDeletedZones(
    limit: number,
    startFrom?: string,
    query?: string,
    ignoreAccess?: boolean
  ) {
    const params = {
      maxItems: limit,
      startFrom,
      nameFilter: query || undefined,
      ignoreAccess,
    };
    return api.get<DeletedZonesResponse>(urlBuilder('/zones/deleted/changes', params));
  },

  getBackendIds() {
    return api.get<string[]>('/zones/backendids');
  },

  createZone(payload: Zone) {
    return api.post<{ zone: Zone }>('/zones', sanitizeConnections(payload));
  },

  updateZone(id: string, payload: Zone) {
    return api.put<{ zone: Zone }>(`/zones/${id}`, sanitizeConnections(payload));
  },

  deleteZone(id: string) {
    return api.delete(`/zones/${id}`);
  },

  normalizeZoneDates(zone: Zone): Zone {
    if (zone.created) zone.created = toApiIso(zone.created);
    if (zone.updated) zone.updated = toApiIso(zone.updated);
    if (zone.latestSync) zone.latestSync = toApiIso(zone.latestSync);
    return zone;
  },

  checkBackendId(zone: Zone): Zone {
    if (zone.backendId === '') zone.backendId = undefined;
    return zone;
  },

  checkSharedStatus(zone: Zone): Zone {
    zone.shared = String(zone.shared).toLowerCase() === 'true';
    return zone;
  },
};
