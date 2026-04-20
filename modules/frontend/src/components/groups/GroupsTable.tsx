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

import React from 'react';
import { Link } from 'react-router-dom';
import type { Group } from '../../types/group';

interface GroupsTableProps {
  groups: Group[];
  onDelete: (id: string) => void;
  onEdit: (group: Group) => void;
  isDeleting: boolean;
}

export function GroupsTable({ groups, onDelete, onEdit, isDeleting }: GroupsTableProps) {
  if (groups.length === 0) {
    return (
      <div className="alert alert-info">
        No groups found. Create a group to get started.
      </div>
    );
  }

  return (
    <div className="table-responsive">
      <table className="table table-hover table-striped align-middle">
        <thead className="table-dark">
          <tr>
            <th>Group Name</th>
            <th>Email</th>
            <th>Description</th>
            <th>Members</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => (
            <tr key={group.id}>
              <td>
                <Link
                  to={`/groups/${group.id}`}
                  className="fw-semibold text-decoration-none"
                >
                  {group.name}
                </Link>
              </td>
              <td>{group.email}</td>
              <td className="text-muted">{group.description ?? '—'}</td>
              <td>
                <span className="badge bg-secondary">
                  {group.members?.length ?? 0}
                </span>
              </td>
              <td>
                <div className="d-flex gap-2">
                  <button
                    className="btn btn-sm btn-outline-primary"
                    onClick={() => onEdit(group)}
                    title="Edit group"
                  >
                    <i className="bi bi-pencil" />
                  </button>
                  <button
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => onDelete(group.id)}
                    disabled={isDeleting}
                    title="Delete group"
                  >
                    <i className="bi bi-trash" />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
