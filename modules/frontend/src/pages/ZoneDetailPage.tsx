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
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { zonesService } from '../services/zonesService';
import { RecordsTable } from '../components/records/RecordsTable';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useZoneRecords } from '../hooks/useRecords';
import { formatDateTime } from '../utils/dateUtils';

export function ZoneDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [activeTab, setActiveTab] = useState<'records' | 'changes'>('records');

  const { data: zoneData, isLoading: zoneLoading } = useQuery({
    queryKey: ['zone', id],
    queryFn: async () => {
      const res = await zonesService.getZone(id);
      return res.data.zone;
    },
    enabled: Boolean(id),
  });

  const {
    records,
    isLoading: recordsLoading,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    search,
  } = useZoneRecords(id);

  const [nameFilter, setNameFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  if (zoneLoading) return <LoadingSpinner />;
  if (!zoneData) return <div className="alert alert-danger">Zone not found.</div>;

  return (
    <div>
      {/* Breadcrumb */}
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/zones">Zones</Link>
          </li>
          <li className="breadcrumb-item active">{zoneData.name}</li>
        </ol>
      </nav>

      {/* Zone info card */}
      <div className="card mb-4 shadow-sm">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-3">
              <div className="text-muted small">Zone Name</div>
              <div className="fw-semibold">{zoneData.name}</div>
            </div>
            <div className="col-md-3">
              <div className="text-muted small">Email</div>
              <div>{zoneData.email}</div>
            </div>
            <div className="col-md-2">
              <div className="text-muted small">Status</div>
              <span
                className={`badge ${
                  zoneData.status === 'Active' ? 'bg-success' : 'bg-warning text-dark'
                }`}
              >
                {zoneData.status}
              </span>
            </div>
            <div className="col-md-2">
              <div className="text-muted small">Shared</div>
              <div>{zoneData.shared ? 'Yes' : 'No'}</div>
            </div>
            <div className="col-md-2">
              <div className="text-muted small">Last Sync</div>
              <div className="small">{zoneData.latestSync ? formatDateTime(zoneData.latestSync) : '—'}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'records' ? 'active' : ''}`}
            onClick={() => setActiveTab('records')}
          >
            <i className="bi bi-file-earmark-text me-1" />
            Records
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'changes' ? 'active' : ''}`}
            onClick={() => setActiveTab('changes')}
          >
            <i className="bi bi-clock-history me-1" />
            Changes
          </button>
        </li>
      </ul>

      {activeTab === 'records' && (
        <>
          {/* Search bar */}
          <div className="row g-2 mb-3">
            <div className="col-md-4">
              <input
                type="text"
                className="form-control"
                placeholder="Filter by name…"
                value={nameFilter}
                onChange={(e) => setNameFilter(e.target.value)}
                onKeyUp={(e) => e.key === 'Enter' && search({ name: nameFilter, type: typeFilter })}
              />
            </div>
            <div className="col-md-3">
              <select
                className="form-select"
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
              >
                <option value="">All Types</option>
                {['A','AAAA','CNAME','MX','NS','PTR','SOA','SPF','SRV','TXT','NAPTR','DS','CAA'].map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="col-md-2">
              <button
                className="btn btn-outline-secondary"
                onClick={() => search({ name: nameFilter, type: typeFilter })}
              >
                <i className="bi bi-search" />
              </button>
            </div>
          </div>

          {recordsLoading ? (
            <LoadingSpinner />
          ) : (
            <>
              <RecordsTable records={records} />
              <Pagination
                onPrev={prevPage}
                onNext={nextPage}
                prevEnabled={prevPageEnabled}
                nextEnabled={nextPageEnabled}
                panelTitle={getPanelTitle()}
              />
            </>
          )}
        </>
      )}

      {activeTab === 'changes' && (
        <div className="alert alert-info">
          Zone change history is displayed here.
        </div>
      )}
    </div>
  );
}
