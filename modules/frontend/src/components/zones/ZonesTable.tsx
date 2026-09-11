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
import type { Zone } from '../../types/zone';
import { formatDateTime } from '../../utils/dateUtils';

type SortDir = 'asc' | 'desc' | null;
type SortCol = 'name' | 'adminGroup' | 'lastSync';

function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === 'asc')  return <i className="bi bi-arrow-up"      style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  if (dir === 'desc') return <i className="bi bi-arrow-down"    style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  return                     <i className="bi bi-arrow-down-up" style={{ fontSize: '0.65rem', color: '#898a8b', marginLeft: 3, opacity: 0.7 }} />;
}

interface ZonesTableProps {
  zones: Zone[];
  showAllZones?: boolean;
  emptyMessage?: string;
  emptySubtitle?: string;
}

export function ZonesTable({ zones, showAllZones, emptyMessage, emptySubtitle }: ZonesTableProps) {
  const [sortState, setSortState] = useState<{ col: SortCol; dir: 'asc' | 'desc' } | null>(null);

  const toggleSort = (col: SortCol) =>
    setSortState((prev) =>
      prev?.col === col
        ? { col, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { col, dir: 'asc' }
    );

  const sortDir = (col: SortCol): SortDir => sortState?.col === col ? sortState.dir : null;

  if (zones.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-diagram-3 fs-1 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">{emptyMessage ?? 'No zones found'}</p>
        <small className="text-muted">
          {emptySubtitle ?? (showAllZones
            ? 'No zones match the search criteria.'
            : 'You do not own any zones. You can manage records in shared zones through DNS Changes.')}
        </small>
      </div>
    );
  }

  const statusClass = (status: string) => {
    if (status === 'Active') return 'vds-zone-status-badge--active';
    if (status === 'Deleted') return 'vds-zone-status-badge--deleted';
    if (status === 'Syncing') return 'vds-zone-status-badge--syncing';
    return 'vds-zone-status-badge--pending';
  };

  const sortedZones = sortState
    ? [...zones].sort((a, b) => {
        const dir = sortState.dir === 'asc' ? 1 : -1;
        if (sortState.col === 'name')       return dir * a.name.localeCompare(b.name);
        if (sortState.col === 'adminGroup') return dir * (a.adminGroupName ?? '').localeCompare(b.adminGroupName ?? '');
        if (sortState.col === 'lastSync')   return dir * (new Date(a.latestSync ?? '').getTime() - new Date(b.latestSync ?? '').getTime());
        return 0;
      })
    : zones;

  return (
    <div className="vds-zones-table-wrap">
      <table className="vds-zones-table">
        <thead>
          <tr>
            <th
              onClick={() => toggleSort('name')}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Zone Name <SortArrow dir={sortDir('name')} /></th>
            <th>Email</th>
            <th
              onClick={() => toggleSort('adminGroup')}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Admin Group <SortArrow dir={sortDir('adminGroup')} /></th>
            <th>Status</th>
            <th
              onClick={() => toggleSort('lastSync')}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Last Sync <SortArrow dir={sortDir('lastSync')} /></th>
            <th>Access</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {sortedZones.map((zone) => (
            <tr key={zone.id}>
              <td>
                <div className="d-flex align-items-center">
                  {zone.accessLevel && zone.accessLevel !== 'NoAccess' ? (
                    <Link
                      to={`/zones/${zone.id}`}
                      className="fw-semibold text-decoration-none vds-table-primary"
                    >
                      {zone.name}
                    </Link>
                  ) : (
                    <span className="fw-semibold vds-table-secondary" title="You do not have access to this zone">
                      {zone.name}
                    </span>
                  )}
                </div>
              </td>
              <td className="vds-table-secondary">{zone.email}</td>
              <td>
                {zone.adminGroupId ? (
                  <Link
                    to={`/groups/${zone.adminGroupId}`}
                    className="vds-table-primary text-decoration-none"
                  >
                    {zone.adminGroupName ?? zone.adminGroupId}
                  </Link>
                ) : (
                  <span className={zone.adminGroupName ? 'vds-table-secondary' : 'vds-table-placeholder'}>
                    {zone.adminGroupName ?? '—'}
                  </span>
                )}
              </td>
              <td>
                <span className={`vds-zone-status-badge ${statusClass(zone.status)}`}>
                  {zone.status}
                </span>
              </td>
              <td className="vds-table-secondary vds-table-nowrap vds-date-wrap">
                {zone.latestSync ? formatDateTime(zone.latestSync) : '—'}
              </td>
              <td>
                <span className={`vds-access-badge ${zone.shared ? 'vds-access-badge--shared' : 'vds-access-badge--private'}`}>
                  <i className={`bi ${zone.shared ? 'bi-share-fill' : 'bi-lock-fill'} me-1`} style={{ fontSize: '0.65rem' }} />
                  {zone.shared ? 'Shared' : 'Private'}
                </span>
              </td>
              <td>
                {zone.accessLevel && zone.accessLevel !== 'NoAccess' ? (
                  <Link
                    to={`/zones/${zone.id}`}
                    className="vds-action-btn vds-action-btn--view vds-action-btn--text"
                    title="View zone"
                  >
                    View
                  </Link>
                ) : (
                  <span className="vds-table-muted small" title="No access"><i className="bi bi-lock" /></span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
