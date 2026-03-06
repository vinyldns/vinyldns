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
  canManage?: boolean;
}

export function GroupMemberList({ members, admins, onRemove, canManage }: GroupMemberListProps) {
  const adminIds = new Set(admins.map((a) => a.id));

  return (
    <div className="table-responsive">
      <table className="table table-hover table-sm align-middle">
        <thead className="table-secondary">
          <tr>
            <th>Username</th>
            <th>Name</th>
            <th>Email</th>
            <th>Role</th>
            {canManage && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.id}>
              <td>{member.userName ?? member.id}</td>
              <td>
                {member.firstName || member.lastName
                  ? `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim()
                  : '—'}
              </td>
              <td>{member.email ?? '—'}</td>
              <td>
                {adminIds.has(member.id) ? (
                  <span className="badge bg-warning text-dark">Admin</span>
                ) : (
                  <span className="badge bg-secondary">Member</span>
                )}
              </td>
              {canManage && (
                <td>
                  <button
                    className="btn btn-sm btn-outline-danger"
                    onClick={() => onRemove?.(member.id)}
                    title="Remove member"
                  >
                    <i className="bi bi-person-x" />
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
