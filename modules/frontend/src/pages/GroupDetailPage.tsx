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
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsService } from '../services/groupsService';
import { GroupMemberList } from '../components/groups/GroupMemberList';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useAlerts } from '../contexts/AlertContext';
import { useProfile } from '../contexts/ProfileContext';

export function GroupDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const { addAlert } = useAlerts();
  const { profile } = useProfile();
  const queryClient = useQueryClient();

  const { data: group, isLoading } = useQuery({
    queryKey: ['group', id],
    queryFn: async () => {
      const res = await groupsService.getGroup(id);
      return res.data;
    },
    enabled: Boolean(id),
  });

  const { data: memberListData, isLoading: membersLoading } = useQuery({
    queryKey: ['group-members', id],
    queryFn: async () => {
      const res = await groupsService.getGroupMemberList(id);
      return res.data.members ?? [];
    },
    enabled: Boolean(id),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (memberId: string) => groupsService.deleteGroupMember(id, memberId),
    onSuccess: () => {
      addAlert('success', 'Member removed');
      void queryClient.invalidateQueries({ queryKey: ['group-members', id] });
    },
    onError: () => addAlert('danger', 'Failed to remove member'),
  });

  const isAdmin = group?.admins.some((a) => a.id === profile?.id) ?? false;

  if (isLoading) return <LoadingSpinner />;
  if (!group) return <div className="alert alert-danger">Group not found.</div>;

  return (
    <div>
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item"><Link to="/groups">Groups</Link></li>
          <li className="breadcrumb-item active">{group.name}</li>
        </ol>
      </nav>

      {/* Group info */}
      <div className="card mb-4 shadow-sm">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <div className="text-muted small">Group Name</div>
              <div className="fw-semibold">{group.name}</div>
            </div>
            <div className="col-md-4">
              <div className="text-muted small">Email</div>
              <div>{group.email}</div>
            </div>
            <div className="col-md-4">
              <div className="text-muted small">Description</div>
              <div>{group.description ?? '—'}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Members */}
      <div className="card shadow-sm">
        <div className="card-header fw-semibold">
          Members
          <span className="badge bg-secondary ms-2">
            {membersLoading ? '…' : memberListData?.length ?? 0}
          </span>
        </div>
        <div className="card-body">
          {membersLoading ? (
            <LoadingSpinner message="Loading members…" />
          ) : (
            <GroupMemberList
              members={memberListData ?? []}
              admins={group.admins}
              onRemove={(memberId) => removeMemberMutation.mutate(memberId)}
              canManage={isAdmin}
            />
          )}
        </div>
      </div>
    </div>
  );
}
