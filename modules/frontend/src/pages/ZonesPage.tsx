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

import React, { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ZonesTable } from '../components/zones/ZonesTable';
import { ZoneForm } from '../components/zones/ZoneForm';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useZones } from '../hooks/useZones';
import { groupsService } from '../services/groupsService';
import { zonesService } from '../services/zonesService';
import type { Zone } from '../types/zone';

export function ZonesPage() {
  const [ignoreAccess, setIgnoreAccess] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editZone, setEditZone] = useState<Zone | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [searchByAdminGroup, setSearchByAdminGroup] = useState(false);

  const {
    zones,
    isLoading,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    createZone,
    updateZone,
    deleteZone,
    isCreating,
    isUpdating,
    isDeleting,
  } = useZones(ignoreAccess);

  const { data: groupsData } = useQuery({
    queryKey: ['all-groups'],
    queryFn: async () => {
      const res = await groupsService.getGroups(true, '');
      return res.data.groups ?? [];
    },
  });

  const { data: backendIds } = useQuery({
    queryKey: ['backend-ids'],
    queryFn: async () => {
      const res = await zonesService.getBackendIds();
      return res.data ?? [];
    },
  });

  const handleSearch = useCallback(() => {
    search(searchInput, searchByAdminGroup);
  }, [search, searchInput, searchByAdminGroup]);

  const handleCreate = (data: Zone) => {
    createZone(data, {
      onSuccess: () => {
        setShowForm(false);
      },
    });
  };

  const handleUpdate = (data: Zone) => {
    if (!editZone) return;
    updateZone(
      { id: editZone.id, zone: data },
      { onSuccess: () => setEditZone(null) }
    );
  };

  const handleDelete = (id: string) => {
    if (window.confirm('Delete this zone? This cannot be undone.')) {
      deleteZone(id);
    }
  };

  return (
    <div>
      {/* Page header */}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 fw-bold">Zones</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          <i className="bi bi-plus-circle me-1" />
          New Zone
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="card mb-4 shadow-sm">
          <div className="card-header fw-semibold">Create New Zone</div>
          <div className="card-body">
            <ZoneForm
              groups={groupsData ?? []}
              backendIds={backendIds ?? []}
              onSubmit={handleCreate}
              onCancel={() => setShowForm(false)}
              isSubmitting={isCreating}
              mode="create"
            />
          </div>
        </div>
      )}

      {/* Edit modal */}
      {editZone && (
        <div className="card mb-4 shadow-sm border-warning">
          <div className="card-header fw-semibold bg-warning text-dark">
            Edit Zone: {editZone.name}
          </div>
          <div className="card-body">
            <ZoneForm
              initialData={editZone}
              groups={groupsData ?? []}
              backendIds={backendIds ?? []}
              onSubmit={handleUpdate}
              onCancel={() => setEditZone(null)}
              isSubmitting={isUpdating}
              mode="edit"
            />
          </div>
        </div>
      )}

      {/* Access toggle */}
      <div className="card mb-3 shadow-sm">
        <div className="card-body py-2">
          <div className="d-flex gap-3 flex-wrap align-items-center">
            <div className="btn-group btn-group-sm" role="group">
              <button
                type="button"
                className={`btn ${!ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(false)}
              >
                My Zones
              </button>
              <button
                type="button"
                className={`btn ${ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(true)}
              >
                All Zones
              </button>
            </div>
            <div className="d-flex gap-2 flex-grow-1 ms-auto" style={{ maxWidth: 500 }}>
              <div className="input-group">
                <input
                  type="text"
                  className="form-control"
                  placeholder="Search zones…"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  onKeyUp={(e) => e.key === 'Enter' && handleSearch()}
                />
                <div className="input-group-text">
                  <input
                    type="checkbox"
                    id="byAdminGroup"
                    className="form-check-input mt-0 me-1"
                    checked={searchByAdminGroup}
                    onChange={(e) => setSearchByAdminGroup(e.target.checked)}
                  />
                  <label htmlFor="byAdminGroup" className="small mb-0">By group</label>
                </div>
                <button className="btn btn-outline-secondary" onClick={handleSearch}>
                  <i className="bi bi-search" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <ZonesTable
            zones={zones}
            onDelete={handleDelete}
            onEdit={(z) => setEditZone(z)}
            isDeleting={isDeleting}
          />
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
