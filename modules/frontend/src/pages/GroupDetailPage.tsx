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

import { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsService } from '../services/groupsService';
import { profileService } from '../services/profileService';
import { GroupMemberList } from '../components/groups/GroupMemberList';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Pagination } from '../components/common/Pagination';
import { TimeFilterDropdown } from '../components/common/TimeFilterDropdown';
import { useAlerts } from '../contexts/AlertContext';
import { useProfile } from '../contexts/ProfileContext';
import { useBreadcrumbs } from '../contexts/BreadcrumbContext';
import { usePaging } from '../hooks/usePaging';
import type { Group, GroupChange, GroupMember } from '../types/group';

type SortDir = 'asc' | 'desc' | null;
function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === 'asc')  return <i className="bi bi-arrow-up"      style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  if (dir === 'desc') return <i className="bi bi-arrow-down"    style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  return                     <i className="bi bi-arrow-down-up" style={{ fontSize: '0.65rem', color: '#898a8b', marginLeft: 3, opacity: 0.7 }} />;
}

export function GroupDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { addAlert } = useAlerts();
  const { setCrumbs } = useBreadcrumbs();
  const { profile, loading: profileLoading } = useProfile();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'manage' | 'changes'>('manage');
  const [newMemberLogin, setNewMemberLogin] = useState('');
  const [newMemberIsAdmin, setNewMemberIsAdmin] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [groupModal, setGroupModal] = useState<{ title: string; group: Group } | null>(null);
  const [chTimeRange, setChTimeRange] = useState<'all' | '1d' | '7d' | '30d' | '90d' | 'custom'>('all');
  const [chDateFrom, setChDateFrom] = useState('');
  const [chDateTo, setChDateTo] = useState('');
  const [chTimeSort, setChTimeSort] = useState<SortDir>(null);

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
    mutationFn: async (memberId: string) => {
      const currentGroup = (await groupsService.getGroup(id)).data;
      const updatedMembers = currentGroup.members.filter((m) => m.id !== memberId);
      const updatedAdmins  = currentGroup.admins.filter((a) => a.id !== memberId);
      return groupsService.updateGroup(id, { ...currentGroup, members: updatedMembers, admins: updatedAdmins });
    },
    onSuccess: () => {
      addAlert('success', 'Successfully Removed Member');
      void queryClient.invalidateQueries({ queryKey: ['group', id] });
      void queryClient.invalidateQueries({ queryKey: ['group-members', id] });
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: string | { errors?: string[] } } };
      const data = e?.response?.data;
      let msg = 'Failed to remove member';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string' && data.trim()) {
        msg = data.replace(/^"|"$/g, '');
      }
      addAlert('danger', msg);
    },
  });

  const addMemberMutation = useMutation({
    mutationFn: async ({ login, makeAdmin }: { login: string; makeAdmin: boolean }) => {
      const userRes = await profileService.getUserDataByUsername(login);
      const user = userRes.data;
      if (!user?.id) throw new Error('User not found');
      const currentGroup = (await groupsService.getGroup(id)).data;
      const alreadyMember = currentGroup.members.some((m) => m.id === user.id);
      const updatedMembers = alreadyMember
        ? currentGroup.members
        : [...currentGroup.members, { id: user.id }];
      const updatedAdmins = makeAdmin && !currentGroup.admins.some((a) => a.id === user.id)
        ? [...currentGroup.admins, { id: user.id }]
        : currentGroup.admins;
      return groupsService.updateGroup(id, { ...currentGroup, members: updatedMembers, admins: updatedAdmins });
    },
    onSuccess: (_, { login }) => {
      addAlert('success', `Added ${login}`);
      setNewMemberLogin('');
      setNewMemberIsAdmin(false);
      setShowAddForm(false);
      void queryClient.invalidateQueries({ queryKey: ['group', id] });
      void queryClient.invalidateQueries({ queryKey: ['group-members', id] });
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: string | { errors?: string[] } } };
      const data = e?.response?.data;
      let msg = 'Failed to add member';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string' && data.trim()) {
        msg = data.replace(/^"|"$/g, '');
      }
      addAlert('danger', msg);
    },
  });

  const toggleAdminMutation = useMutation({
    mutationFn: async ({ memberId, makeAdmin }: { memberId: string; makeAdmin: boolean }) => {
      const currentGroup = (await groupsService.getGroup(id)).data;
      const updatedAdmins = makeAdmin
        ? currentGroup.admins.some((a) => a.id === memberId)
          ? currentGroup.admins
          : [...currentGroup.admins, { id: memberId }]
        : currentGroup.admins.filter((a) => a.id !== memberId);
      return groupsService.updateGroup(id, { ...currentGroup, admins: updatedAdmins });
    },
    onSuccess: (_, { makeAdmin }) => {
      addAlert('success', makeAdmin ? 'Made group manager' : 'Removed as group manager');
      void queryClient.invalidateQueries({ queryKey: ['group', id] });
      void queryClient.invalidateQueries({ queryKey: ['group-members', id] });
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: string | { errors?: string[] } } };
      const data = e?.response?.data;
      let msg = 'Failed to update admin status';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors) && data.errors.length > 0) {
        msg = data.errors.join(' ');
      } else if (typeof data === 'string' && data.trim().length > 0) {
        msg = data.replace(/^"|"$/g, '');
      }
      addAlert('danger', msg);
    },
  });
  const togglingMemberId = toggleAdminMutation.isPending
    ? (toggleAdminMutation.variables as { memberId: string } | undefined)?.memberId
    : undefined;

  const currentUserMember = memberListData?.find((m) => m.id === profile?.id);
  const isGroupAdmin =
    currentUserMember?.isAdmin ??
    ((group?.admins.some((a) => a.id === profile?.id) ?? false) || (profile?.isSuper ?? false));
  const isMember = group?.members.some((m) => m.id === profile?.id) ?? false;
  const showChangeHistory = isGroupAdmin || isMember || (profile?.isSupport ?? false);

  const {
    paging: chPaging,
    nextPageUpdate: chNextPageUpdate,
    prevPageUpdate: chPrevPageUpdate,
    getPrevStartFrom: getChPrevStartFrom,
    prevPageEnabled: chPrevEnabled,
  } = usePaging(100);

  const { data: changesData, isLoading: changesLoading, refetch: refetchChanges } = useQuery({
    queryKey: ['group-changes', id, chPaging.next],
    queryFn: async () => {
      const res = await groupsService.getGroupChanges(id, 100, chPaging.next as string | undefined);
      return res.data;
    },
    enabled: Boolean(id) && showChangeHistory && activeTab === 'changes',
  });

  const chNextEnabled = Boolean(changesData?.nextId);

  const chNextPage = useCallback(() => {
    chNextPageUpdate(changesData?.changes?.length ?? 0, changesData?.nextId);
  }, [changesData, chNextPageUpdate]);

  const chPrevPage = useCallback(() => {
    chPrevPageUpdate(getChPrevStartFrom());
  }, [chPrevPageUpdate, getChPrevStartFrom]);

  useEffect(() => {
    if (!group) return;
    setCrumbs([{ label: 'Groups', to: '/groups' }, { label: group.name }]);
    return () => setCrumbs(null);
  }, [group, setCrumbs]);

  if (isLoading || profileLoading) return <LoadingSpinner />;
  if (!group) return <div className="alert alert-danger">Group not found.</div>;

  const initials = (name: string) =>
    name.split(/[-_ ]+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('');

  const copyGroupId = async () => {
    try {
      await navigator.clipboard.writeText(group.id);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch {
      addAlert('danger', 'Failed to copy');
    }
  };

  const filteredChanges = (() => {
    if (!changesData?.changes || chTimeRange === 'all') return changesData?.changes ?? [];
    const now = Date.now();
    return changesData.changes.filter((c) => {
      const t = new Date(c.created).getTime();
      if (chTimeRange === 'custom') {
        const from = chDateFrom ? new Date(chDateFrom + 'T00:00:00').getTime() : 0;
        const to = chDateTo ? new Date(chDateTo + 'T23:59:59').getTime() : Infinity;
        return t >= from && t <= to;
      }
      const ms = chTimeRange === '1d' ? 86400000 : chTimeRange === '7d' ? 604800000 : chTimeRange === '30d' ? 2592000000 : chTimeRange === '90d' ? 7776000000 : 2592000000;
      return t >= now - ms;
    });
  })();

  return (
    <div>
      {/* ── Page header ── */}
      <div className="rounded-3 mb-4 d-flex justify-content-between align-items-center vds-page-header vds-page-header--lg">
        <div className="d-flex align-items-center gap-3">
          <div className="rounded-3 d-flex align-items-center justify-content-center fw-bold text-white vds-page-header__icon"
            style={{ fontSize: '1rem', letterSpacing: '0.04em' }}
          >
            {initials(group.name)}
          </div>
          <div>
            <h4 className="mb-0 fw-bold vds-page-header__title">{group.name}</h4>
            <small className="text-muted">{group.email}</small>
          </div>
        </div>
        <button
          type="button"
          className="btn btn-sm d-flex align-items-center gap-1 vds-btn-nav"
          onClick={() => navigate(-1)}
        >
          <i className="bi bi-arrow-left" />
          Back to Groups
        </button>
      </div>

      {/* ── Email validation notice ── */}
      <div className="vds-email-warning">
        <i className="bi bi-info-circle-fill fs-6" style={{ flexShrink: 0 }} />
        <span>Updates to group require a valid email. If group email is invalid please enter a valid email.</span>
      </div>

      {/* ── Info strip ── */}
      <div className="vds-info-strip">
        <div>
          <div className="d-flex align-items-center gap-3 vds-info-label">
            Group ID
            <button
              className={`btn btn-sm p-0 border-0 bg-transparent vds-copy-btn ${copySuccess ? 'text-success' : 'text-secondary'}`}
              title={copySuccess ? 'Copied!' : 'Copy Group ID to clipboard'}
              onClick={copyGroupId}
            >
              <i className={copySuccess ? 'bi bi-check2' : 'bi bi-clipboard'} />
            </button>
          </div>
          <div className="fw-semibold text-break vds-info-value">{group.id}</div>
        </div>
        <div>
          <div className="vds-info-label">Description</div>
          {group.description?.trim()
            ? <div className="vds-info-value--md">{group.description}</div>
            : <div className="vds-info-value--placeholder">No description</div>}
        </div>
        <div>
          <div className="vds-info-label">Members</div>
          <span className="vds-member-badge">
            <i className="bi bi-people-fill" />{group.members?.length ?? 0}
          </span>
        </div>
        <div>
          <div className="vds-info-label">Admins</div>
          <span className="vds-member-badge vds-member-badge--admin">
            <i className="bi bi-shield-fill" />{group.admins?.length ?? 0}
          </span>
        </div>
      </div>

      {/* ── Tabs ── */}
      <ul className="nav nav-tabs mb-0 vds-nav-tabs-bar">
        <li className="nav-item">
          <button
            className={`nav-link vds-tab-btn${activeTab === 'manage' ? ' active fw-semibold vds-tab-btn--active' : ''}`}
            onClick={() => setActiveTab('manage')}
          >
            <i className="bi bi-people me-1" />Manage Members
          </button>
        </li>
        {showChangeHistory && (
          <li className="nav-item">
            <button
              className={`nav-link vds-tab-btn${activeTab === 'changes' ? ' active fw-semibold vds-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('changes')}
            >
              <i className="bi bi-clock-history me-1" />Change History
            </button>
          </li>
        )}
      </ul>

      {/* ── Manage Members tab ── */}
      {activeTab === 'manage' && (
        <div className="rounded-bottom-3 vds-tab-panel-content vds-tab-enter">
          {/* ── Toolbar: member count + Add Member button ── */}
          <div className="px-4 py-2 d-flex align-items-center gap-2 vds-section-toolbar">
            <i className="bi bi-people-fill text-primary" />
            <span className="fw-semibold vds-section-toolbar__title">Members</span>
            <span className="vds-member-badge ms-1">
              {membersLoading ? '…' : memberListData?.length ?? 0}
            </span>
            {isGroupAdmin && (
              <button
                className={`btn btn-sm ms-auto d-flex align-items-center gap-1 vds-member-toggle-btn ${
                  showAddForm ? 'vds-btn-flat' : 'vds-btn-nav'
                }`}
                onClick={() => { setShowAddForm((v) => !v); setNewMemberLogin(''); setNewMemberIsAdmin(false); }}
              >
                <i className={`bi ${showAddForm ? 'bi-x-lg' : 'bi-person-plus-fill'}`} />
                {showAddForm ? 'Cancel' : 'Add Member'}
              </button>
            )}
          </div>

          {/* ── Add Member card ── */}
          {isGroupAdmin && showAddForm && (
            <div className="mx-4 my-3 rounded-3 p-3 vds-add-member-form">
              <div className="d-flex align-items-center gap-2 mb-3">
                <div className="rounded-circle d-flex align-items-center justify-content-center vds-add-member-icon">
                  <i className="bi bi-person-plus-fill text-white" style={{ fontSize: '0.75rem' }} />
                </div>
                <span className="fw-semibold vds-add-member-heading">Add New Member</span>
              </div>
              <div className="d-flex flex-wrap gap-2 align-items-end">
                <div style={{ width: 220, flexShrink: 0 }}>
                  <label className="vds-add-member-label">Username</label>
                  <div className="input-group input-group-sm">
                    <span className="input-group-text vds-add-member-prefix">
                      <i className="bi bi-person" style={{ color: '#0d6efd' }} />
                    </span>
                    <input
                      type="text"
                      className="form-control vds-add-member-input"
                      placeholder="e.g. john"
                      value={newMemberLogin}
                      autoFocus
                      onChange={(e) => setNewMemberLogin(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && newMemberLogin.trim() && !addMemberMutation.isPending) {
                          addMemberMutation.mutate({ login: newMemberLogin.trim(), makeAdmin: newMemberIsAdmin });
                        }
                      }}
                    />
                  </div>
                </div>
                <div className="d-flex align-items-center gap-2 pb-1">
                  <div className="form-check form-switch mb-0 d-flex align-items-center gap-2" style={{ cursor: 'pointer' }}>
                    <input
                      className="form-check-input"
                      type="checkbox"
                      role="switch"
                      id="newMemberAdmin"
                      checked={newMemberIsAdmin}
                      onChange={(e) => setNewMemberIsAdmin(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <label className="form-check-label vds-add-member-check-label" htmlFor="newMemberAdmin">
                      Group Manager
                    </label>
                  </div>
                </div>
                <button
                  className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1 vds-add-member-btn"
                  disabled={!newMemberLogin.trim() || addMemberMutation.isPending}
                  onClick={() => addMemberMutation.mutate({ login: newMemberLogin.trim(), makeAdmin: newMemberIsAdmin })}
                >
                  {addMemberMutation.isPending
                    ? <><span className="spinner-border spinner-border-sm vds-spinner-xs" /> Adding…</>
                    : <><i className="bi bi-person-check-fill" /> Add Member</>}
                </button>
              </div>
            </div>
          )}
          <div className="p-3">
            {membersLoading ? (
              <LoadingSpinner message="Loading members…" />
            ) : (
              <GroupMemberList
                members={memberListData ?? []}
                admins={group.admins}
                onRemove={(memberId) => removeMemberMutation.mutate(memberId)}
                onToggleAdmin={(memberId, makeAdmin) =>
                  toggleAdminMutation.mutate({ memberId, makeAdmin })
                }
                canManage={isGroupAdmin}
                isTogglingAdmin={toggleAdminMutation.isPending}
                togglingMemberId={togglingMemberId}
              />
            )}
          </div>
        </div>
      )}

      {/* ── Change History tab ── */}
      {activeTab === 'changes' && (
        <div className="rounded-bottom-3 vds-tab-panel-content vds-tab-enter">
          <div className="px-4 py-2 d-flex align-items-center gap-2 flex-wrap vds-section-toolbar">
            <i className="bi bi-clock-history text-primary" />
            <span className="fw-semibold vds-section-toolbar__title">Change History</span>
            {/* ── Time filter pushed to the right ── */}
            <div className="ms-auto d-flex align-items-center gap-2 flex-wrap">
              <TimeFilterDropdown
                value={chTimeRange}
                dateFrom={chDateFrom}
                dateTo={chDateTo}
                onChange={setChTimeRange}
                onDateFromChange={setChDateFrom}
                onDateToChange={setChDateTo}
              />
              <button
                className="btn btn-sm d-flex align-items-center gap-1 vds-member-toggle-btn vds-btn-flat"
                disabled={changesLoading}
                onClick={() => void refetchChanges()}
              >
                <i className={`bi bi-arrow-clockwise${changesLoading ? ' spin' : ''}`} />
                Refresh
              </button>
            </div>
          </div>
          {chTimeRange !== 'all' && (
            <div className="d-flex justify-content-end gap-2 mb-2 flex-wrap align-items-center px-4">
              <span className="vds-active-filter-tag d-flex align-items-center gap-1">
                <i className="bi bi-calendar3" />
                {chTimeRange === '1d' ? 'Today'
                  : chTimeRange === '7d' ? 'Last 7 days'
                  : chTimeRange === '30d' ? 'Last 30 days'
                  : chTimeRange === '90d' ? 'Last 90 days'
                  : chTimeRange === 'custom'
                    ? [chDateFrom, chDateTo].filter(Boolean).join(' → ') || 'Custom range'
                    : 'Custom range'}
                <button
                  type="button"
                  className="btn-close ms-1"
                  style={{ fontSize: '0.5rem', filter: 'invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)' }}
                  aria-label="Remove time filter"
                  onClick={() => { setChTimeRange('all'); setChDateFrom(''); setChDateTo(''); }}
                />
              </span>
              <button
                type="button"
                className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                style={{ color: '#e53e3e', border: '1px solid rgba(229,62,62,0.3)', fontSize: '0.78rem' }}
                onClick={() => { setChTimeRange('all'); setChDateFrom(''); setChDateTo(''); }}
              >
                <i className="bi bi-x-circle" />Clear All
              </button>
            </div>
          )}
          <div className="p-3">
            {changesLoading ? (
              <LoadingSpinner message="Loading changes…" />
            ) : !changesData?.changes || changesData.changes.length === 0 ? (
              <div className="vds-empty-state">
                <i className="bi bi-clock-history fs-2 mb-2" style={{ opacity: 0.4 }} />
                <p className="mb-0 fw-semibold">No changes recorded</p>
              </div>
            ) : filteredChanges.length === 0 ? (
              <div className="vds-empty-state">
                <i className="bi bi-funnel fs-2 mb-2" style={{ opacity: 0.4 }} />
                <p className="mb-0 fw-semibold">No changes in selected time range</p>
                <small className="text-muted">Try adjusting the time filter above.</small>
              </div>
            ) : (
              <div className="vds-groups-table-wrap">
                <table className="vds-groups-table">
                  <thead>
                    <tr>
                      <th
                        onClick={() => setChTimeSort((d) => d === 'asc' ? 'desc' : 'asc')}
                        style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                      >Time <SortArrow dir={chTimeSort} /></th>
                      <th>Group Change ID</th>
                      <th>Change Type</th>
                      <th>Change Message</th>
                      <th>Change Info</th>
                      <th>User</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...filteredChanges]
                      .sort((a, b) => chTimeSort
                        ? (chTimeSort === 'asc' ? 1 : -1) * (new Date(a.created).getTime() - new Date(b.created).getTime())
                        : 0)
                      .map((change: GroupChange) => (
                      <tr key={change.id}>
                        <td className="vds-table-secondary vds-table-nowrap">
                          {change.created ? new Date(change.created).toLocaleString() : '—'}
                        </td>
                        <td className="vds-table-muted vds-table-mono">
                          {change.id}
                        </td>
                        <td>
                          <span
                            className={`badge vds-change-badge vds-change-badge--${(change.changeType ?? '').toLowerCase()}`}
                          >
                            {change.changeType}
                          </span>
                        </td>
                        <td className="vds-table-secondary" style={{ maxWidth: 280 }}>
                          {change.groupChangeMessage
                            ? change.groupChangeMessage.split('. ').filter(Boolean).map((sentence, i) => (
                                <div key={i}>{sentence}{sentence.endsWith('.') ? '' : '.'}</div>
                              ))
                            : '—'}
                        </td>
                        <td>
                          <div className="d-flex flex-column gap-1">
                            {change.newGroup && (
                              <button
                                className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                onClick={() => setGroupModal({ title: change.changeType === 'Create' ? 'Created Group' : 'New Group', group: change.newGroup! })}
                              >
                                <i className="bi bi-eye" />
                                {change.changeType === 'Create' ? 'View created group' : 'View new group'}
                              </button>
                            )}
                            {change.changeType === 'Update' && change.oldGroup && (
                              <button
                                className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                onClick={() => setGroupModal({ title: 'Old Group', group: change.oldGroup! })}
                              >
                                <i className="bi bi-clock-history" />
                                View old group
                              </button>
                            )}
                          </div>
                        </td>
                        <td className="vds-table-secondary">{change.userName ?? change.userId}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {(chNextEnabled || chPrevEnabled) && (
              <Pagination
                onPrev={chPrevPage}
                onNext={chNextPage}
                prevEnabled={chPrevEnabled}
                nextEnabled={chNextEnabled}
              />
            )}
          </div>
        </div>
      )}

      {/* ── Group Snapshot Modal (rendered at page level, outside tabs) ── */}
      {groupModal && (
        <div
          className="modal d-block vds-group-modal-backdrop"
          onClick={() => setGroupModal(null)}
        >
          <div
            className="modal-dialog modal-dialog-centered"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-content vds-group-modal-content">
              <div className="vds-group-modal-header">
                <i className="bi bi-info-circle-fill text-primary" />
                <h6 className="modal-title vds-group-modal-title">{groupModal.title}</h6>
                <button className="btn-close ms-auto" onClick={() => setGroupModal(null)} />
              </div>
              <div className="modal-body px-4 py-3">
                {([
                  { label: 'Group ID',    value: groupModal.group.id },
                  { label: 'Group Name',  value: groupModal.group.name },
                  { label: 'Email',       value: groupModal.group.email },
                  { label: 'Description', value: groupModal.group.description ?? null },
                  { label: 'Status',      value: groupModal.group.status ?? null },
                  { label: 'Created',     value: groupModal.group.created ? new Date(groupModal.group.created).toLocaleString() : null },
                  { label: 'Member IDs',  value: groupModal.group.members?.map((m: GroupMember) => m.id).join('\n') ?? null, mono: true },
                  { label: 'Admin IDs',   value: groupModal.group.admins?.map((a: GroupMember) => a.id).join('\n') ?? null, mono: true },
                ] as Array<{ label: string; value: string | null; mono?: boolean }>)
                  .filter((row) => row.value)
                  .map((row) => (
                    <div key={row.label} className="mb-3">
                      <div className="vds-group-modal-label">{row.label}</div>
                      {row.mono ? (
                        <textarea
                          readOnly
                          className="form-control form-control-sm vds-group-modal-textarea"
                          rows={Math.min((row.value!.split('\n').length), 4)}
                          value={row.value!}
                        />
                      ) : (
                        <div className="fw-semibold vds-group-modal-value">{row.value}</div>
                      )}
                    </div>
                  ))}
              </div>
              <div className="modal-footer px-4 py-2 vds-group-modal-footer">
                <button className="btn btn-sm btn-outline-secondary" onClick={() => setGroupModal(null)}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
