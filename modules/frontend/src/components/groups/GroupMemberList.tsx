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
import type { GroupMember } from '../../types/group';

interface GroupMemberListProps {
  members: GroupMember[];
  admins: GroupMember[];
  onRemove?: (memberId: string) => void;
  onToggleAdmin?: (memberId: string, makeAdmin: boolean) => void;
  canManage?: boolean;
  isTogglingAdmin?: boolean;
  togglingMemberId?: string;
}

export function GroupMemberList({ members, admins, onRemove, onToggleAdmin, canManage, isTogglingAdmin, togglingMemberId  }: GroupMemberListProps) {
  const adminIds = new Set(admins.map((a) => a.id));

  const formatName = (member: GroupMember) => {
    const parts = [member.lastName, member.firstName].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : '—';
  };

  if (members.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-person-x fs-2 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">No members yet</p>
      </div>
    );
  }

  return (
    <div className="vds-groups-table-wrap">
      <table className="vds-groups-table vds-group-members-table">
        <thead>
          <tr>
            <th>User Name</th>
            <th>Name</th>
            <th>Email</th>
            <th>Group Manager</th>
            <th>Status</th>
            {canManage && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {members
            .slice()
            .sort((a, b) => (a.userName ?? '').localeCompare(b.userName ?? ''))
            .map((member) => {
              const isAdminMember = member.isAdmin ?? adminIds.has(member.id);
              return (
                <tr key={member.id}>
                  <td className="vds-table-username">{member.userName ?? member.id}</td>
                  <td className="vds-table-secondary">{formatName(member)}</td>
                  <td className="vds-table-secondary">{member.email ?? '—'}</td>
                  <td>
                    <div className="form-check form-switch mb-0 d-flex align-items-center gap-2">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        role="switch"
                        checked={isAdminMember}
                        disabled={!canManage || isTogglingAdmin}
                        onChange={(e) => onToggleAdmin?.(member.id, e.target.checked)}
                      />
                      {isTogglingAdmin && togglingMemberId === member.id && (
                        <span className="spinner-border spinner-border-sm text-primary" role="status" aria-label="Updating…" />
                      )}
                    </div>
                  </td>
                  <td>
                    <span className={`vds-lock-status ${member.lockStatus === 'Locked' ? 'vds-lock-status--locked' : 'vds-lock-status--active'}`}>
                      {member.lockStatus ?? 'Active'}
                    </span>
                  </td>
                  {canManage && (
                    <td>
                      <button
                        className="vds-action-btn vds-action-btn--delete"
                        onClick={() => onRemove?.(member.id)}
                        title="Remove member"
                      >
                        <i className="bi bi-person-dash-fill" />
                      </button>
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
