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

import React, { useState, useRef, useEffect } from 'react';
import type { RecordSet, RecordData } from '../../types/record';

// ── NS / PTR expand-collapse chip list ───────────────────────────────────────
const SHOW_LIMIT = 3;
function MultiValueChips({ values }: { values: string[] }) {
  const [expanded, setExpanded] = useState(false);
  const visible = expanded ? values : values.slice(0, SHOW_LIMIT);
  const extra = values.length - SHOW_LIMIT;
  return (
    <div className="d-flex flex-column gap-1">
      {visible.map((val, i) => (
        <span key={i} className="vds-record-data-chip vds-record-data-chip--wrap">
          <i className="bi bi-dot" style={{ fontSize: '0.65rem', marginRight: 2, opacity: 0.5 }} />
          {val}
        </span>
      ))}
      {values.length > SHOW_LIMIT && (
        <button
          type="button"
          className="vds-record-data-more"
          onClick={() => setExpanded((v) => !v)}
        >
          {expanded
            ? <><i className="bi bi-chevron-up" style={{ fontSize: '0.65rem', marginRight: 3 }} />Show fewer</>
            : <><i className="bi bi-chevron-down" style={{ fontSize: '0.65rem', marginRight: 3 }} />+{extra} more</>}
        </button>
      )}
    </div>
  );
}

export interface RecordsTableProps {
  records: RecordSet[];
  onEdit?: (record: RecordSet) => void;
  onDelete?: (record: RecordSet) => void;
  /** Whether the parent zone is shared */
  isSharedZone?: boolean;
  /** Current user is a super-user */
  isSuper?: boolean;
  /** Current user is a support user */
  isSupport?: boolean;
  /** Current user is a zone admin */
  isZoneAdmin?: boolean;
  /** IDs of groups the current user belongs to */
  userGroupIds?: string[];
  /** Called when user wants to claim / request ownership of a record */
  onRequestOwnership?: (record: RecordSet) => void;
  /** Called when user wants to cancel their pending ownership request */
  onCloseOwnershipRequest?: (record: RecordSet) => void;
  /** Called when owner-group member approves a transfer request */
  onApproveOwnership?: (record: RecordSet) => void;
  /** Called when owner-group member rejects a transfer request */
  onRejectOwnership?: (record: RecordSet) => void;
}

const statusClass = (status: string) => {
  if (status === 'Active')        return 'vds-zone-status-badge--active';
  if (status === 'Inactive')      return 'vds-zone-status-badge--deleted';
  if (status === 'PendingDelete') return 'vds-zone-status-badge--deleted';
  return 'vds-zone-status-badge--pending';
};

function formatRecordData(r: RecordSet): React.ReactNode {
  if (!r.records?.length) return <span className="text-muted">—</span>;
  
  // NS / PTR: stacked chips with expand/collapse after 3
  if (r.type === 'NS' || r.type === 'PTR') {
    const key = r.type === 'NS' ? 'nsdname' : 'ptrdname';
    const all = r.records.map((d: RecordData) => (d as Record<string, string>)[key] ?? '');
    return <MultiValueChips values={all} />;
  }

  // SOA: all 7 fields in 4 chips
  if (r.type === 'SOA' && r.records[0]) {
    const d = r.records[0];
    return (
      <div className="d-flex flex-column gap-1">
        <span className="vds-record-data-chip vds-record-data-chip--wrap">
          <i className="bi bi-server" style={{ fontSize: '0.65rem', marginRight: 3, opacity: 0.6 }} />
          {d.mname}
        </span>
        <span className="vds-record-data-chip vds-record-data-chip--wrap">
          <i className="bi bi-envelope" style={{ fontSize: '0.65rem', marginRight: 3, opacity: 0.6 }} />
          {d.rname}
        </span>
        <span className="vds-record-data-chip vds-record-data-chip--dim">
          serial {d.serial} · refresh {d.refresh} · retry {d.retry}
        </span>
        <span className="vds-record-data-chip vds-record-data-chip--dim">
          expire {d.expire} · minimum {d.minimum}
        </span>
      </div>
    );
  }

  const items = r.records.slice(0, 3).map((d: RecordData, i: number) => {
    let text = '';
    switch (r.type) {
      case 'A':
      case 'AAAA':   text = d.address ?? ''; break;
      case 'CNAME':  text = d.cname ?? ''; break;
      case 'MX':     text = `${d.preference ?? 10} ${d.exchange ?? ''}`; break;
      case 'TXT':
      case 'SPF':    text = `"${d.text ?? ''}"`; break;
      case 'SRV':    text = `${d.priority} ${d.weight} ${d.port} ${d.target}`; break;
      case 'CAA':    text = `${d.flags} ${d.tag} "${d.value}"`; break;
      case 'SSHFP':  text = `alg ${d.algorithm} fp ${String(d.fingerprint ?? '').slice(0, 12)}…`; break;
      case 'DS':     text = `tag ${d.keytag} alg ${d.algorithm} digest ${d.digesttype}`; break;
      case 'NAPTR':  text = `${d.order} ${d.preference} ${d.flags} ${d.service}`; break;
      default:       text = JSON.stringify(d).slice(0, 40);
    }
    return <span key={i} className="vds-record-data-chip vds-record-data-chip--wrap">{text}</span>;
  });

  return (
    <div className="d-flex flex-wrap gap-1 align-items-center">
      {items}
      {r.records.length > 3 && (
        <span className="vds-record-data-more"
          title={r.records.slice(3).map((d: RecordData) => JSON.stringify(d)).join('\n')}
        >+{r.records.length - 3} more</span>
      )}
    </div>
  );
}

const OWNERSHIP_STATUS_META: Record<string, { cls: string; icon: string; label: string }> = {
  AutoApproved:     { cls: 'vds-ownership-badge--approved',  icon: 'bi-check-circle-fill',      label: 'Auto Approved'  },
  ManuallyApproved: { cls: 'vds-ownership-badge--approved',  icon: 'bi-check-circle-fill',      label: 'Approved'       },
  ManuallyRejected: { cls: 'vds-ownership-badge--rejected',  icon: 'bi-x-circle-fill',          label: 'Rejected'       },
  Requested:        { cls: 'vds-ownership-badge--requested', icon: 'bi-arrow-right-circle-fill', label: 'Requested'      },
  PendingReview:    { cls: 'vds-ownership-badge--pending',   icon: 'bi-hourglass-split',        label: 'Pending Review' },
  Cancelled:        { cls: 'vds-ownership-badge--cancelled', icon: 'bi-ban',                   label: 'Cancelled'      },
};

function OwnershipStatusBadge({ status }: { status?: string }) {
  if (!status || status === 'None') {
    return <span className="vds-ownership-badge vds-ownership-badge--none">—</span>;
  }
  const meta = OWNERSHIP_STATUS_META[status];
  if (!meta) return <span className="vds-ownership-badge vds-ownership-badge--none">{status}</span>;
  return (
    <span className={`vds-ownership-badge ${meta.cls}`}>
      <i className={`bi ${meta.icon} me-1`} style={{ fontSize: '0.7rem' }} />
      {meta.label}
    </span>
  );
}

function OwnerGroupCell({ record }: { record: RecordSet }) {
  if (!record.ownerGroupId) {
    return (
      <span className="vds-unassigned-owner">
        <i className="bi bi-dash-circle me-1" />
        Unassigned
      </span>
    );
  }
  return (
    <span className="vds-owner-group-chip">
      <i className="bi bi-people-fill me-1" />
      {record.ownerGroupName ?? `${record.ownerGroupId.slice(0, 8)}…`}
    </span>
  );
}

type OwnershipCapability =
  | 'CLAIM'          
  | 'REQUEST'        
  | 'REQUESTOR'      
  | 'APPROVER'      
  | 'ADMIN_APPROVER' 
  | 'PENDING'       
  | 'NONE';          

function resolveOwnershipCapability(
  record: RecordSet,
  opts: { isSuper: boolean; isSupport: boolean; isZoneAdmin: boolean; userGroupIds: string[] },
): OwnershipCapability {
  const { isSuper, isSupport, isZoneAdmin, userGroupIds } = opts;
  const status = record.recordSetGroupChange?.ownershipTransferStatus ?? 'None';

  if (!record.ownerGroupId) return 'CLAIM';

  const isOwner = userGroupIds.includes(record.ownerGroupId);
  const isRequestor = Boolean(
    record.recordSetGroupChange?.requestedOwnerGroupId &&
    userGroupIds.includes(record.recordSetGroupChange.requestedOwnerGroupId)
  );

  if (status === 'PendingReview') {
    // Super / support always get full control — checked before requestor/owner
    if (isSuper || isSupport) return 'ADMIN_APPROVER';
    // Requestor (non-super): hourglass + cancel only
    if (isRequestor) return 'REQUESTOR';
    // Current owner (not requestor): approve + reject
    if (isOwner) return 'APPROVER';
    // Zone admin (not requestor, not owner): approve + reject + cancel
    if (isZoneAdmin) return 'ADMIN_APPROVER';
    return 'PENDING';
  }

  const hasNonOwnerGroup = userGroupIds.some((gid) => gid !== record.ownerGroupId);
  if (isOwner && !hasNonOwnerGroup && !isSuper && !isSupport) return 'NONE';
  return 'REQUEST';
}

function ZoneTypePill({ isSharedZone }: { isSharedZone: boolean }) {
  return (
    <span className={`vds-records-zone-pill ${isSharedZone ? 'vds-records-zone-pill--shared' : 'vds-records-zone-pill--private'}`}>
      <i className={`bi ${isSharedZone ? 'bi-share-fill' : 'bi-lock-fill'} me-1`} style={{ fontSize: '0.62rem' }} />
      {isSharedZone ? 'Shared Zone' : 'Private Zone'}
    </span>
  );
}

interface OwnershipActionsDropdownProps {
  canCancel: boolean;  
  isPendingOnly?: boolean;  onApprove?: () => void;
  onReject?: () => void;
  onCancel?: () => void;
}

function OwnershipActionsDropdown({ canCancel, isPendingOnly = false, onApprove, onReject, onCancel }: OwnershipActionsDropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div className="vds-ownership-dropdown" ref={ref}>
      <button
        className={`vds-action-btn ${isPendingOnly ? 'vds-action-btn--pending-menu' : 'vds-action-btn--ownership-menu'}`}
        onClick={() => setOpen((o) => !o)}
        title={isPendingOnly ? 'Your request is pending — click to cancel' : 'Ownership actions'}
      >
        <i className={`bi ${isPendingOnly ? 'bi-hourglass-split' : 'bi-shield-fill-check'}`} />
        <i className={`bi bi-chevron-${open ? 'up' : 'down'} vds-ownership-dropdown__caret`} />
      </button>

      {open && (
        <div className="vds-ownership-dropdown__menu">
          {/* Menu header */}
          <div className={`vds-ownership-dropdown__header ${isPendingOnly ? 'vds-ownership-dropdown__header--pending' : ''}`}>
            <i className={`bi ${isPendingOnly ? 'bi-hourglass-split' : 'bi-shield-fill-check'} me-2`} style={{ fontSize: '0.75rem' }} />
            {isPendingOnly ? 'Request Pending' : 'Ownership Actions'}
          </div>

          {!isPendingOnly && onApprove && (
            <button
              className="vds-ownership-dropdown__item vds-ownership-dropdown__item--approve"
              onClick={() => { onApprove(); setOpen(false); }}
            >
              <span className="vds-ownership-dropdown__icon vds-ownership-dropdown__icon--approve">
                <i className="bi bi-check-lg" />
              </span>
              Approve
            </button>
          )}
          {!isPendingOnly && onReject && (
            <button
              className="vds-ownership-dropdown__item vds-ownership-dropdown__item--reject"
              onClick={() => { onReject(); setOpen(false); }}
            >
              <span className="vds-ownership-dropdown__icon vds-ownership-dropdown__icon--reject">
                <i className="bi bi-x-lg" />
              </span>
              Reject
            </button>
          )}
          {canCancel && onCancel && (
            <>
              {!isPendingOnly && <div className="vds-ownership-dropdown__divider" />}
              <button
                className="vds-ownership-dropdown__item vds-ownership-dropdown__item--cancel"
                onClick={() => { onCancel(); setOpen(false); }}
              >
                <span className="vds-ownership-dropdown__icon vds-ownership-dropdown__icon--cancel">
                  <i className="bi bi-x-circle-fill" />
                </span>
                Cancel Request
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

export function RecordsTable({
  records,
  onEdit,
  onDelete,
  isSharedZone = false,
  isSuper = false,
  isSupport = false,
  isZoneAdmin = false,
  userGroupIds = [],
  onRequestOwnership,
  onCloseOwnershipRequest,
  onApproveOwnership,
  onRejectOwnership,
}: RecordsTableProps) {
  if (!records.length) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-file-earmark-text fs-1 mb-2" style={{ opacity: 0.35 }} />
        <p className="mb-0 fw-semibold">No records found</p>
        <small className="text-muted">Try adjusting the filter or add a new record.</small>
      </div>
    );
  }

  const hasActions = !!(onEdit || onDelete || (isSharedZone && (onRequestOwnership || onApproveOwnership)));

  return (
    <div className="vds-zones-table-wrap">
      <div className="vds-records-table-banner">
        <ZoneTypePill isSharedZone={isSharedZone} />
        <span className="vds-records-table-banner__hint">
          {isSharedZone
            ? 'Ownership columns are shown because this zone is shared.'
            : 'This zone is private — ownership transfer is not available.'}
        </span>
      </div>

      <table className="vds-zones-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>TTL</th>
            <th>Record Data</th>
            {isSharedZone && (
              <>
                <th>
                  <span className="vds-th-with-icon">
                    <i className="bi bi-people-fill me-1 text-primary" style={{ fontSize: '0.75rem' }} />
                    Owner Group Name
                  </span>
                </th>
                <th>
                  <span className="vds-th-with-icon">
                    <i className="bi bi-arrow-left-right me-1 text-warning" style={{ fontSize: '0.75rem' }} />
                    Ownership Transfer Status
                  </span>
                </th>
              </>
            )}
            <th>Status</th>
            {hasActions && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {records.map((rec) => {
            const capability: OwnershipCapability | null = isSharedZone
              ? resolveOwnershipCapability(rec, { isSuper, isSupport, isZoneAdmin, userGroupIds })
              : null;

            return (
              <tr key={rec.id}>
                <td className="fw-semibold vds-table-primary">{rec.name}</td>
                <td><span className="vds-record-type-badge">{rec.type}</span></td>
                <td className="vds-table-secondary">{rec.ttl}s</td>
                <td>{formatRecordData(rec)}</td>

                {isSharedZone && (
                  <>
                    <td><OwnerGroupCell record={rec} /></td>
                    <td>
                      <OwnershipStatusBadge
                        status={
                          rec.recordSetGroupChange?.ownershipTransferStatus ??
                          (rec.ownerGroupId ? 'AutoApproved' : undefined)
                        }
                      />
                    </td>
                  </>
                )}

                <td>
                  <span className={`vds-zone-status-badge ${statusClass(rec.status)}`}>
                    {rec.status}
                  </span>
                </td>

                {hasActions && (
                  <td>
                    <div className="d-flex gap-1 align-items-center flex-nowrap">
                      {onEdit && (
                        <button
                          className="vds-action-btn vds-action-btn--edit"
                          onClick={() => onEdit(rec)}
                          title="Edit record"
                        >
                          <i className="bi bi-pencil-fill" />
                        </button>
                      )}
                      {onDelete && (
                        <button
                          className="vds-action-btn vds-action-btn--delete"
                          onClick={() => onDelete(rec)}
                          title="Delete record"
                        >
                          <i className="bi bi-trash3-fill" />
                        </button>
                      )}

                      {isSharedZone && capability && capability !== 'NONE' && (
                        <>
                          {capability === 'CLAIM' && onRequestOwnership && (
                            <button
                              className="vds-action-btn vds-action-btn--claim"
                              onClick={() => onRequestOwnership(rec)}
                              title="Claim ownership of this record"
                            >
                              <i className="bi bi-person-plus-fill" />
                            </button>
                          )}

                          {capability === 'REQUEST' && onRequestOwnership && (
                            <button
                              className="vds-action-btn vds-action-btn--request"
                              onClick={() => onRequestOwnership(rec)}
                              title="Request ownership transfer"
                            >
                              <i className="bi bi-arrow-left-right" />
                            </button>
                          )}

                          {capability === 'REQUESTOR' && (
                            <OwnershipActionsDropdown
                              isPendingOnly
                              canCancel
                              onCancel={onCloseOwnershipRequest ? () => onCloseOwnershipRequest(rec) : undefined}
                            />
                          )}

                          {capability === 'PENDING' && (
                            <button
                              className="vds-action-btn vds-action-btn--pending"
                              disabled
                              title="Ownership request is pending review"
                            >
                              <i className="bi bi-hourglass-split" />
                            </button>
                          )}

                          {capability === 'APPROVER' && (
                            <OwnershipActionsDropdown
                              canCancel={false}
                              onApprove={onApproveOwnership ? () => onApproveOwnership(rec) : undefined}
                              onReject={onRejectOwnership ? () => onRejectOwnership(rec) : undefined}
                            />
                          )}

                          {capability === 'ADMIN_APPROVER' && (
                            <OwnershipActionsDropdown
                              canCancel
                              onApprove={onApproveOwnership ? () => onApproveOwnership(rec) : undefined}
                              onReject={onRejectOwnership ? () => onRejectOwnership(rec) : undefined}
                              onCancel={onCloseOwnershipRequest ? () => onCloseOwnershipRequest(rec) : undefined}
                            />
                          )}
                        </>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>

    </div>
  );
}
