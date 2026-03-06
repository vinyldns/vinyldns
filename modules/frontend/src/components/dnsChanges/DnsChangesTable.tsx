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
import type { DnsChangeSummary } from '../../types/dnsChange';
import { formatDateTime } from '../../utils/dateUtils';

interface DnsChangesTableProps {
  changes: DnsChangeSummary[];
  onCancel?: (id: string) => void;
}

const STATUS_CLASS: Record<string, string> = {
  Complete: 'bg-success',
  Failed: 'bg-danger',
  PartialFailure: 'bg-warning text-dark',
  Pending: 'bg-info text-dark',
  PendingReview: 'bg-warning text-dark',
  Cancelled: 'bg-secondary',
  Scheduled: 'bg-primary',
};

export function DnsChangesTable({ changes, onCancel }: DnsChangesTableProps) {
  if (changes.length === 0) {
    return <div className="alert alert-info">No DNS changes found.</div>;
  }

  return (
    <div className="table-responsive">
      <table className="table table-hover table-striped align-middle">
        <thead className="table-dark">
          <tr>
            <th>ID</th>
            <th>Created By</th>
            <th>Created</th>
            <th>Status</th>
            <th>Changes</th>
            <th>Comments</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {changes.map((change) => (
            <tr key={change.id}>
              <td>
                <Link to={`/dnschanges/${change.id}`} className="font-monospace text-decoration-none small">
                  {change.id.substring(0, 8)}…
                </Link>
              </td>
              <td>{change.userName}</td>
              <td className="small">{formatDateTime(change.createdTimestamp)}</td>
              <td>
                <span className={`badge ${STATUS_CLASS[change.status] ?? 'bg-secondary'}`}>
                  {change.status}
                </span>
              </td>
              <td>
                <span className="badge bg-light text-dark border">
                  {change.totalChanges}
                </span>
              </td>
              <td className="text-muted small">{change.comments ?? '—'}</td>
              <td>
                {onCancel && (change.status === 'Pending' || change.status === 'PendingReview') && (
                  <button
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => onCancel(change.id)}
                  >
                    Cancel
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
