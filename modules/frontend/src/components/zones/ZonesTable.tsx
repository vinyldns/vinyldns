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
import type { Zone } from '../../types/zone';
import { formatDateTime } from '../../utils/dateUtils';

interface ZonesTableProps {
  zones: Zone[];
  onDelete: (id: string) => void;
  onEdit: (zone: Zone) => void;
  isDeleting: boolean;
}

export function ZonesTable({ zones, onDelete, onEdit, isDeleting }: ZonesTableProps) {
  if (zones.length === 0) {
    return (
      <div className="alert alert-info">
        No zones found. Create a zone to get started.
      </div>
    );
  }

  return (
    <div className="table-responsive">
      <table className="table table-hover table-striped align-middle">
        <thead className="table-dark">
          <tr>
            <th>Zone Name</th>
            <th>Email</th>
            <th>Status</th>
            <th>Admin Group</th>
            <th>Last Sync</th>
            <th>Shared</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {zones.map((zone) => (
            <tr key={zone.id}>
              <td>
                <Link to={`/zones/${zone.id}`} className="fw-semibold text-decoration-none">
                  {zone.name}
                </Link>
              </td>
              <td>{zone.email}</td>
              <td>
                <span
                  className={`badge ${
                    zone.status === 'Active'
                      ? 'bg-success'
                      : zone.status === 'Deleted'
                      ? 'bg-danger'
                      : 'bg-warning text-dark'
                  }`}
                >
                  {zone.status}
                </span>
              </td>
              <td>{zone.adminGroupName ?? zone.adminGroupId}</td>
              <td>{zone.latestSync ? formatDateTime(zone.latestSync) : '—'}</td>
              <td>
                {zone.shared ? (
                  <i className="bi bi-check-circle-fill text-success" />
                ) : (
                  <i className="bi bi-x-circle text-muted" />
                )}
              </td>
              <td>
                <div className="d-flex gap-2">
                  <button
                    className="btn btn-sm btn-outline-primary"
                    onClick={() => onEdit(zone)}
                    title="Edit zone"
                  >
                    <i className="bi bi-pencil" />
                  </button>
                  <button
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => onDelete(zone.id)}
                    disabled={isDeleting}
                    title="Delete zone"
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
