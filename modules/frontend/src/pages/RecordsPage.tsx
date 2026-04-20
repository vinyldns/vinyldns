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
import { RecordsTable } from '../components/records/RecordsTable';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useRecords } from '../hooks/useRecords';

const RECORD_TYPES = ['A','AAAA','CNAME','MX','NS','PTR','SOA','SPF','SRV','TXT','NAPTR','DS','CAA'];

export function RecordsPage() {
  const [nameFilter, setNameFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [nameSort, setNameSort] = useState('');
  const [ownerGroupFilter, setOwnerGroupFilter] = useState('');

  const {
    records,
    isLoading,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
  } = useRecords();

  const handleSearch = () => {
    search({ name: nameFilter, type: typeFilter, sort: nameSort, ownerGroup: ownerGroupFilter });
  };

  return (
    <div>
      <h1 className="h3 fw-bold mb-4">RecordSet Search</h1>

      {/* Search filters */}
      <div className="card mb-4 shadow-sm">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <label className="form-label fw-semibold">Record Name</label>
              <input
                type="text"
                className="form-control"
                placeholder="Filter by name…"
                value={nameFilter}
                onChange={(e) => setNameFilter(e.target.value)}
                onKeyUp={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="col-md-2">
              <label className="form-label fw-semibold">Type</label>
              <select
                className="form-select"
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
              >
                <option value="">All Types</option>
                {RECORD_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label fw-semibold">Sort</label>
              <select
                className="form-select"
                value={nameSort}
                onChange={(e) => setNameSort(e.target.value)}
              >
                <option value="">Default</option>
                <option value="ASC">A → Z</option>
                <option value="DESC">Z → A</option>
              </select>
            </div>
            <div className="col-md-3">
              <label className="form-label fw-semibold">Owner Group Filter</label>
              <input
                type="text"
                className="form-control"
                placeholder="Owner group ID…"
                value={ownerGroupFilter}
                onChange={(e) => setOwnerGroupFilter(e.target.value)}
              />
            </div>
            <div className="col-md-1 d-flex align-items-end">
              <button className="btn btn-primary w-100" onClick={handleSearch}>
                <i className="bi bi-search" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <RecordsTable records={records} showZone />
          <Pagination
            onPrev={prevPage}
            onNext={nextPage}
            prevEnabled={prevPageEnabled}
            nextEnabled={nextPageEnabled}
            panelTitle={getPanelTitle()}
          />
        </>
      )}
    </div>
  );
}
