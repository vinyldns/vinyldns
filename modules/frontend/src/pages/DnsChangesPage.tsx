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
import { DnsChangesTable } from '../components/dnsChanges/DnsChangesTable';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useDnsChanges } from '../hooks/useDnsChanges';
import { useProfile } from '../contexts/ProfileContext';

export function DnsChangesPage() {
  const [ignoreAccess, setIgnoreAccess] = useState(false);
  const { profile } = useProfile();

  const {
    dnsChanges,
    isLoading,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    cancelBatchChange,
  } = useDnsChanges(ignoreAccess);

  const handleCancel = (id: string) => {
    if (window.confirm('Cancel this batch change?')) {
      cancelBatchChange(id);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 fw-bold">DNS Changes</h1>
        <Link to="/dnschanges/new" className="btn btn-primary">
          <i className="bi bi-plus-circle me-1" />
          New Batch Change
        </Link>
      </div>

      {/* Access toggle */}
      {profile?.isSuper && (
        <div className="card mb-3 shadow-sm">
          <div className="card-body py-2">
            <div className="btn-group btn-group-sm">
              <button
                type="button"
                className={`btn ${!ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(false)}
              >
                My Changes
              </button>
              <button
                type="button"
                className={`btn ${ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(true)}
              >
                All Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <DnsChangesTable changes={dnsChanges} onCancel={handleCancel} />
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
