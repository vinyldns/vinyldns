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

import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import type { DeletedZoneChange } from '../../types/zone';
import { formatDateTime } from '../../utils/dateUtils';

type SortDir = 'asc' | 'desc' | null;
function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === 'asc')  return <i className="bi bi-arrow-up"      style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  if (dir === 'desc') return <i className="bi bi-arrow-down"    style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  return                     <i className="bi bi-arrow-down-up" style={{ fontSize: '0.65rem', color: '#898a8b', marginLeft: 3, opacity: 0.7 }} />;
}

interface AbandonedZonesTableProps {
  zones: DeletedZoneChange[];
  emptyMessage?: string;
  emptySubtitle?: string;
}

export function AbandonedZonesTable({ zones, emptyMessage, emptySubtitle }: AbandonedZonesTableProps) {
  const [createdSort, setCreatedSort] = useState<SortDir>(null);
  const [abandonedSort, setAbandonedSort] = useState<SortDir>(null);

  if (zones.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-trash3 fs-1 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">{emptyMessage ?? 'No abandoned zones found'}</p>
        <small className="text-muted">{emptySubtitle ?? 'Deleted zones will appear here.'}</small>
      </div>
    );
  }

  const sortedZones = (() => {
    if (createdSort) return [...zones].sort((a, b) =>
      (createdSort === 'asc' ? 1 : -1) * (new Date(a.zoneChange.zone.created ?? '').getTime() - new Date(b.zoneChange.zone.created ?? '').getTime()));
    if (abandonedSort) return [...zones].sort((a, b) =>
      (abandonedSort === 'asc' ? 1 : -1) * (new Date(a.zoneChange.zone.updated ?? '').getTime() - new Date(b.zoneChange.zone.updated ?? '').getTime()));
    return zones;
  })();

  return (
    <div className="vds-zones-table-wrap">
      <table className="vds-zones-table">
        <thead>
          <tr>
            <th>Zone Name</th>
            <th>Email</th>
            <th>Admin Group</th>
            <th
              onClick={() => { setCreatedSort((d) => d === 'asc' ? 'desc' : 'asc'); setAbandonedSort(null); }}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Created <SortArrow dir={createdSort} /></th>
            <th
              onClick={() => { setAbandonedSort((d) => d === 'asc' ? 'desc' : 'asc'); setCreatedSort(null); }}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Abandoned <SortArrow dir={abandonedSort} /></th>
            <th>Status</th>
            <th>Abandoned By</th>
            <th>Access</th>
          </tr>
        </thead>
        <tbody>
          {sortedZones.map((item) => {
            const zone = item.zoneChange.zone;
            return (
              <tr key={item.zoneChange.id}>
                <td>
                  <span className="fw-semibold vds-table-secondary">{zone.name}</span>
                </td>
                <td className="vds-table-secondary">{zone.email}</td>
                <td>
                  {zone.adminGroupId ? (
                    <Link
                      to={`/groups/${zone.adminGroupId}`}
                      className="vds-table-primary text-decoration-none"
                    >
                      {item.adminGroupName ?? zone.adminGroupId}
                    </Link>
                  ) : (
                    <span className={item.adminGroupName ? 'vds-table-secondary' : 'vds-table-placeholder'}>
                      {item.adminGroupName ?? '—'}
                    </span>
                  )}
                </td>
                <td className="vds-table-secondary vds-table-nowrap vds-date-wrap">
                  {zone.created ? formatDateTime(zone.created) : '—'}
                </td>
                <td className="vds-table-secondary vds-table-nowrap vds-date-wrap">
                  {zone.updated ? formatDateTime(zone.updated) : '—'}
                </td>
                <td>
                  <span className="vds-zone-status-badge vds-zone-status-badge--deleted">
                    {zone.status}
                  </span>
                </td>
                <td className="vds-table-secondary">{item.userName ?? item.zoneChange.userId ?? '—'}</td>
                <td>
                  <span className={`vds-access-badge ${zone.shared ? 'vds-access-badge--shared' : 'vds-access-badge--private'}`}>
                    {zone.shared ? 'Shared' : 'Private'}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
