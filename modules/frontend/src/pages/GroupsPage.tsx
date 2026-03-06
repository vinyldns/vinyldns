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
import { GroupsTable } from '../components/groups/GroupsTable';
import { GroupForm } from '../components/groups/GroupForm';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useGroups } from '../hooks/useGroups';
import { groupsService } from '../services/groupsService';
import { useProfile } from '../contexts/ProfileContext';
import type { Group } from '../types/group';

export function GroupsPage() {
  const { profile } = useProfile();
  const [ignoreAccess, setIgnoreAccess] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editGroup, setEditGroup] = useState<Group | null>(null);
  const [searchInput, setSearchInput] = useState('');

  const {
    groups,
    isLoading,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    createGroup,
    updateGroup,
    deleteGroup,
    isCreating,
    isUpdating,
    isDeleting,
  } = useGroups(ignoreAccess);

  const { data: validEmailDomains } = useQuery({
    queryKey: ['email-domains'],
    queryFn: async () => {
      const res = await groupsService.listEmailDomains();
      return Array.isArray(res.data) ? res.data : [];
    },
  });

  const handleSearch = useCallback(() => {
    search(searchInput);
  }, [search, searchInput]);

  const handleCreate = (data: { name: string; email: string; description?: string }) => {
    createGroup(
      {
        ...data,
        members: profile ? [{ id: profile.id }] : [],
        admins: profile ? [{ id: profile.id }] : [],
      },
      { onSuccess: () => setShowForm(false) }
    );
  };

  const handleUpdate = (data: { name: string; email: string; description?: string }) => {
    if (!editGroup) return;
    updateGroup({ id: editGroup.id, group: { ...editGroup, ...data } }, {
      onSuccess: () => setEditGroup(null),
    });
  };

  const handleDelete = (id: string) => {
    if (window.confirm('Delete this group?')) deleteGroup(id);
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 fw-bold">Groups</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          <i className="bi bi-plus-circle me-1" />
          New Group
        </button>
      </div>

      {showForm && (
        <div className="card mb-4 shadow-sm">
          <div className="card-header fw-semibold">Create New Group</div>
          <div className="card-body">
            <GroupForm
              onSubmit={handleCreate}
              onCancel={() => setShowForm(false)}
              isSubmitting={isCreating}
              mode="create"
              validEmailDomains={validEmailDomains ?? []}
            />
          </div>
        </div>
      )}

      {editGroup && (
        <div className="card mb-4 shadow-sm border-warning">
          <div className="card-header fw-semibold bg-warning text-dark">
            Edit Group: {editGroup.name}
          </div>
          <div className="card-body">
            <GroupForm
              initialData={editGroup}
              onSubmit={handleUpdate}
              onCancel={() => setEditGroup(null)}
              isSubmitting={isUpdating}
              mode="edit"
              validEmailDomains={validEmailDomains ?? []}
            />
          </div>
        </div>
      )}

      {/* Access toggle and search */}
      <div className="card mb-3 shadow-sm">
        <div className="card-body py-2">
          <div className="d-flex gap-3 flex-wrap align-items-center">
            <div className="btn-group btn-group-sm">
              <button
                type="button"
                className={`btn ${!ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(false)}
              >
                My Groups
              </button>
              <button
                type="button"
                className={`btn ${ignoreAccess ? 'btn-primary' : 'btn-outline-primary'}`}
                onClick={() => setIgnoreAccess(true)}
              >
                All Groups
              </button>
            </div>
            <div className="input-group ms-auto" style={{ maxWidth: 400 }}>
              <input
                type="text"
                className="form-control"
                placeholder="Search groups…"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyUp={(e) => e.key === 'Enter' && handleSearch()}
              />
              <button className="btn btn-outline-secondary" onClick={handleSearch}>
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
          <GroupsTable
            groups={groups}
            onDelete={handleDelete}
            onEdit={(g) => setEditGroup(g)}
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
