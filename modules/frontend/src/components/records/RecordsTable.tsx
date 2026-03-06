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
import type { RecordSet } from '../../types/record';
import { formatDateTime } from '../../utils/dateUtils';
import { copyToClipboard } from '../../utils/dateUtils';

interface RecordsTableProps {
  records: RecordSet[];
  onEdit?: (record: RecordSet) => void;
  onDelete?: (record: RecordSet) => void;
  showZone?: boolean;
}

export function RecordsTable({ records, onEdit, onDelete, showZone }: RecordsTableProps) {
  if (records.length === 0) {
    return <div className="alert alert-info">No records found.</div>;
  }

  return (
    <div className="table-responsive">
      <table className="table table-hover table-striped table-sm align-middle">
        <thead className="table-dark">
          <tr>
            {showZone && <th>Zone</th>}
            <th>Name</th>
            <th>Type</th>
            <th>TTL</th>
            <th>Status</th>
            <th>Last Updated</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {records.map((rec) => (
            <tr key={rec.id}>
              {showZone && <td className="text-muted small">{rec.zoneName}</td>}
              <td>
                <span
                  className="text-primary cursor-pointer"
                  style={{ cursor: 'pointer' }}
                  onClick={() => void copyToClipboard(rec.id)}
                  title="Click to copy ID"
                >
                  {rec.name}
                </span>
              </td>
              <td>
                <span className="badge bg-info text-dark">{rec.type}</span>
              </td>
              <td>{rec.ttl}</td>
              <td>
                <span
                  className={`badge ${
                    rec.status === 'Active'
                      ? 'bg-success'
                      : rec.status === 'Inactive'
                      ? 'bg-danger'
                      : 'bg-warning text-dark'
                  }`}
                >
                  {rec.status}
                </span>
              </td>
              <td className="small">{rec.updated ? formatDateTime(rec.updated) : '—'}</td>
              <td>
                <div className="d-flex gap-1">
                  {onEdit && (
                    <button
                      className="btn btn-sm btn-outline-primary"
                      onClick={() => onEdit(rec)}
                      title="Edit record"
                    >
                      <i className="bi bi-pencil" />
                    </button>
                  )}
                  {onDelete && (
                    <button
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => onDelete(rec)}
                      title="Delete record"
                    >
                      <i className="bi bi-trash" />
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
