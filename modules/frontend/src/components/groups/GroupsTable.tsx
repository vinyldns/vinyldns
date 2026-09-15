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
import type { Group } from '../../types/group';

type SortDir = 'asc' | 'desc' | null;
function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === 'asc')  return <i className="bi bi-arrow-up"      style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  if (dir === 'desc') return <i className="bi bi-arrow-down"    style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  return                     <i className="bi bi-arrow-down-up" style={{ fontSize: '0.65rem', color: '#898a8b', marginLeft: 3, opacity: 0.7 }} />;
}

interface GroupsTableProps {
  groups: Group[];
  onDelete: (group: Group) => void;
  onEdit: (group: Group) => void;
  isDeleting: boolean;
  isGroupAdmin: (group: Group) => boolean;
  currentUserId?: string;
  emptyMessage?: string;
  emptySubtitle?: string;
}

export function GroupsTable({ groups, onDelete, onEdit, isDeleting, isGroupAdmin, currentUserId, emptyMessage, emptySubtitle }: GroupsTableProps) {
  const [nameSort, setNameSort] = useState<SortDir>(null);

  const sortedGroups = nameSort
    ? [...groups].sort((a, b) => (nameSort === 'asc' ? 1 : -1) * a.name.localeCompare(b.name))
    : groups;

  if (groups.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-people fs-1 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">{emptyMessage ?? 'No groups found'}</p>
        <small className="text-muted">{emptySubtitle ?? 'Create a group to get started.'}</small>
      </div>
    );
  }

  return (
    <div className="vds-groups-table-wrap">
      <table className="vds-groups-table">
        <thead>
          <tr>
            <th
              onClick={() => setNameSort((d) => d === 'asc' ? 'desc' : 'asc')}
              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
            >Group Name <SortArrow dir={nameSort} /></th>
            <th>Email</th>
            <th>Description</th>
            <th>Your Role</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {sortedGroups.map((group) => (
            <tr key={group.id}>
              <td>
                <div className="d-flex align-items-center">
                  <Link
                    to={`/groups/${group.id}`}
                    className="fw-semibold text-decoration-none vds-table-primary"
                  >
                    {group.name}
                  </Link>
                </div>
              </td>
              <td className="vds-table-secondary">{group.email}</td>
              <td className={group.description?.trim() ? 'vds-table-secondary' : 'vds-table-placeholder'}>
                {group.description?.trim() || 'No description'}
              </td>
              <td>
                {(() => {
                  const isAdmin = currentUserId && group.admins.some((a) => a.id === currentUserId);
                  const isMember = currentUserId && group.members.some((m) => m.id === currentUserId);
                  if (isAdmin) return (
                    <span className="vds-role-badge vds-role-badge--admin">
                      <i className="bi bi-shield-fill" />Admin
                    </span>
                  );
                  if (isMember) return (
                    <span className="vds-role-badge vds-role-badge--member">
                      <i className="bi bi-person-fill" />Member
                    </span>
                  );
                  return (
                    <span className="vds-role-badge vds-role-badge--none">
                      <i className="bi bi-person-dash" />No Role
                    </span>
                  );
                })()}
              </td>
              <td>
                <div className="d-flex gap-2 align-items-center vds-table-actions">
                  <Link
                    to={`/groups/${group.id}`}
                    className="vds-action-btn vds-action-btn--view vds-action-btn--text"
                    title="View group"
                  >
                    View
                  </Link>
                  {isGroupAdmin(group) && (
                    <button
                      className="vds-action-btn vds-action-btn--edit vds-action-btn--text"
                      onClick={() => onEdit(group)}
                      title="Edit group"
                    >
                      Edit
                    </button>
                  )}
                  {isGroupAdmin(group) && (
                    <button
                      id={`delete-group-${group.name}`}
                      className="vds-action-btn vds-action-btn--delete vds-action-btn--text"
                      onClick={() => onDelete(group)}
                      disabled={isDeleting}
                      title="Delete group"
                    >
                      Delete
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
