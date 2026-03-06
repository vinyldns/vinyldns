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
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { dnsChangeService } from '../services/dnsChangeService';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { formatDateTime } from '../utils/dateUtils';
import { useDnsChanges } from '../hooks/useDnsChanges';

const STATUS_CLASS: Record<string, string> = {
  Complete: 'bg-success',
  Failed: 'bg-danger',
  PartialFailure: 'bg-warning text-dark',
  Pending: 'bg-info text-dark',
  PendingReview: 'bg-warning text-dark',
  Cancelled: 'bg-secondary',
  Scheduled: 'bg-primary',
};

export function DnsChangeDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { approveBatchChange, rejectBatchChange, cancelBatchChange } = useDnsChanges();

  const { data: change, isLoading } = useQuery({
    queryKey: ['dnschange', id],
    queryFn: async () => {
      const res = await dnsChangeService.getBatchChange(id);
      return res.data;
    },
    enabled: Boolean(id),
  });

  if (isLoading) return <LoadingSpinner />;
  if (!change) return <div className="alert alert-danger">Batch change not found.</div>;

  return (
    <div>
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item"><Link to="/dnschanges">DNS Changes</Link></li>
          <li className="breadcrumb-item active">{id.substring(0, 8)}…</li>
        </ol>
      </nav>

      {/* Summary */}
      <div className="card mb-4 shadow-sm">
        <div className="card-header fw-semibold d-flex align-items-center gap-2">
          Batch Change Details
          <span className={`badge ${STATUS_CLASS[change.status] ?? 'bg-secondary'}`}>
            {change.status}
          </span>
        </div>
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-3">
              <div className="text-muted small">ID</div>
              <div className="font-monospace small">{change.id}</div>
            </div>
            <div className="col-md-3">
              <div className="text-muted small">Created By</div>
              <div>{change.userName}</div>
            </div>
            <div className="col-md-3">
              <div className="text-muted small">Created</div>
              <div className="small">{formatDateTime(change.createdTimestamp)}</div>
            </div>
            <div className="col-md-3">
              <div className="text-muted small">Comments</div>
              <div>{change.comments ?? '—'}</div>
            </div>
          </div>
        </div>
        {(change.status === 'Pending' || change.status === 'PendingReview') && (
          <div className="card-footer d-flex gap-2">
            {change.status === 'PendingReview' && (
              <>
                <button
                  className="btn btn-success btn-sm"
                  onClick={() => {
                    const comment = window.prompt('Review comment (optional):') ?? undefined;
                    approveBatchChange({ id: change.id, comment }, { onSuccess: () => void navigate('/dnschanges') });
                  }}
                >
                  Approve
                </button>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() => {
                    const comment = window.prompt('Rejection reason:') ?? undefined;
                    rejectBatchChange({ id: change.id, comment }, { onSuccess: () => void navigate('/dnschanges') });
                  }}
                >
                  Reject
                </button>
              </>
            )}
            <button
              className="btn btn-outline-danger btn-sm"
              onClick={() => {
                if (window.confirm('Cancel this batch change?')) {
                  cancelBatchChange(change.id, { onSuccess: () => void navigate('/dnschanges') });
                }
              }}
            >
              Cancel
            </button>
          </div>
        )}
      </div>

      {/* Changes table */}
      <div className="card shadow-sm">
        <div className="card-header fw-semibold">
          Changes
          <span className="badge bg-secondary ms-2">{change.changes.length}</span>
        </div>
        <div className="card-body p-0">
          <div className="table-responsive">
            <table className="table table-hover table-sm mb-0">
              <thead className="table-secondary">
                <tr>
                  <th>Change Type</th>
                  <th>Input Name</th>
                  <th>Type</th>
                  <th>TTL</th>
                  <th>Status</th>
                  <th>Message</th>
                </tr>
              </thead>
              <tbody>
                {change.changes.map((c) => (
                  <tr key={c.id}>
                    <td><span className="badge bg-light text-dark border">{c.changeType}</span></td>
                    <td className="font-monospace small">{c.inputName}</td>
                    <td><span className="badge bg-info text-dark">{c.type}</span></td>
                    <td>{c.ttl ?? '—'}</td>
                    <td>
                      <span className={`badge ${STATUS_CLASS[c.status] ?? 'bg-secondary'}`}>
                        {c.status}
                      </span>
                    </td>
                    <td className="text-muted small">{c.systemMessage ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
