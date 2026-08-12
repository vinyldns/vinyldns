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

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { GroupCombobox } from '../components/zones/GroupCombobox';
import { useBreadcrumbs } from '../contexts/BreadcrumbContext';
import { useProfile } from '../contexts/ProfileContext';
import { zonesService } from '../services/zonesService';
import { recordsService } from '../services/recordsService';
import { groupsService } from '../services/groupsService';
import { profileService } from '../services/profileService';
import api from '../services/api';
import { RecordsTable } from '../components/records/RecordsTable';
import { RecordForm } from '../components/records/RecordForm';
import { Pagination } from '../components/common/Pagination';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { TimeFilterDropdown } from '../components/common/TimeFilterDropdown';
import type { TimeRange } from '../components/common/TimeFilterDropdown';
import { useZoneRecords } from '../hooks/useRecords';
import { usePaging } from '../hooks/usePaging';
import { formatDateTime } from '../utils/dateUtils';
import type { Zone, AclRule } from '../types/zone';
import type { RecordSet, RecordSetGroupChange } from '../types/record';

type DetailTab = 'records' | 'recordChanges' | 'zoneChanges' | 'zone';

const inRange = (dateStr: string | undefined, range: TimeRange, from: string, to: string): boolean => {
  if (range === 'all') return true;
  if (!dateStr) return true;
  const ts = new Date(dateStr).getTime();
  const now = Date.now();
  if (range === '1d') return ts >= now - 86400000;
  if (range === '7d') return ts >= now - 7 * 86400000;
  if (range === '30d') return ts >= now - 30 * 86400000;
  if (range === '90d') return ts >= now - 90 * 86400000;
  if (range === 'custom') {
    if (from && ts < new Date(from + 'T00:00:00').getTime()) return false;
    if (to && ts > new Date(to + 'T23:59:59').getTime()) return false;
  }
  return true;
};

type SortDir = 'asc' | 'desc' | null;
function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === 'asc')  return <i className="bi bi-arrow-up" style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  if (dir === 'desc') return <i className="bi bi-arrow-down" style={{ fontSize: '0.7rem', color: '#2e5090', marginLeft: 3 }} />;
  return <i className="bi bi-arrow-down-up" style={{ fontSize: '0.65rem', color: '#898a8b', marginLeft: 3, opacity: 0.7 }} />;
}

type AclRuleForm = {
  mode: 'create' | 'edit';
  ruleIndex?: number;
  rule: {
    priority: 'User' | 'Group';
    userName?: string;
    groupId?: string;
    accessLevel: string;
    recordTypes: string[];
    recordMask?: string;
    description?: string;
  };
};

const statusClass = (status: string) => {
  if (status === 'Active') return 'vds-zone-status-badge--active';
  if (status === 'Deleted') return 'vds-zone-status-badge--deleted';
  if (status === 'Syncing') return 'vds-zone-status-badge--syncing';
  return 'vds-zone-status-badge--pending';
};

const changeStatusClass = (status: string) => {
  if (status === 'Complete') return 'vds-zone-status-badge--active';
  if (status === 'Failed') return 'vds-zone-status-badge--deleted';
  return 'vds-zone-status-badge--pending';
};

export function ZoneDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { setCrumbs } = useBreadcrumbs();
  const { profile } = useProfile();
  const isSuper  = profile?.isSuper   ?? false;
  const isSupport = profile?.isSupport ?? false;

  const [activeTab, setActiveTab]         = useState<DetailTab>('records');
  const [showRecordForm, setShowRecordForm] = useState(false);
  const [editRecord, setEditRecord]        = useState<RecordSet | null>(null);
  const [recordToDelete, setRecordToDelete] = useState<RecordSet | null>(null);
  const [nameFilter, setNameFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [ttlFilter, setTtlFilter] = useState<number | null>(null);
  const [typeDropdownOpen, setTypeDropdownOpen] = useState(false);
  const [statusDropdownOpen, setStatusDropdownOpen] = useState(false);
  const [ttlDropdownOpen, setTtlDropdownOpen] = useState(false);
  const [copiedZoneId, setCopiedZoneId] = useState(false);
  const [availableTypes, setAvailableTypes] = useState<string[]>([]);
  const [copiedChangeId, setCopiedChangeId] = useState<string | null>(null);
  const [recentChangesOpen, setRecentChangesOpen] = useState(false);
  const [zcUserNames, setZcUserNames] = useState<Record<string, string>>({});
  const [zcGroupNames, setZcGroupNames] = useState<Record<string, string>>({});
  const [viewingRecordSet, setViewingRecordSet] = useState<{ label: string; rs: RecordSet } | null>(null);
  const [aclModal, setAclModal] = useState<{ rules: AclRule[] } | null>(null);
  const navigate = useNavigate();
  const [zoneFormData, setZoneFormData] = useState<Zone | null>(null);
  const [zoneUpdateMode, setZoneUpdateMode] = useState<'idle' | 'confirm'>('idle');
  const [zoneConnOpen, setZoneConnOpen] = useState(false);
  const [zoneTransferOpen, setZoneTransferOpen] = useState(false);
  const [abandonZoneModal, setAbandonZoneModal] = useState(false);
  const [aclRuleModal, setAclRuleModal] = useState<AclRuleForm | null>(null);
  const [aclDeleteModal, setAclDeleteModal] = useState<{ index: number } | null>(null);
  const [zoneTabAlert, setZoneTabAlert] = useState<{ type: 'success' | 'danger'; msg: string } | null>(null);
  const [aclAlert, setAclAlert] = useState<{ type: 'success' | 'danger'; msg: string } | null>(null);
  const [syncSchedule, setSyncSchedule] = useState('');
  const [syncScheduleRemove, setSyncScheduleRemove] = useState(false);
  const [syncScheduleMode, setSyncScheduleMode] = useState<'idle' | 'confirm'>('idle');
  const [syncDays, setSyncDays] = useState<string[]>([]);
  const [syncHour, setSyncHour] = useState(2);
  const [syncMinute, setSyncMinute] = useState(0);
  const [localTimeInput, setLocalTimeInput] = useState('');
  const [utcTimeOutput, setUtcTimeOutput] = useState('');
  const [showUtcConverter, setShowUtcConverter] = useState(false);
  const [zoneInfoOpen, setZoneInfoOpen] = useState(true);
  const [zoneAclOpen, setZoneAclOpen] = useState(true);
  const [zoneSyncOpen, setZoneSyncOpen] = useState(true);
  const [copiedRcChangeId, setCopiedRcChangeId] = useState<string | null>(null);
  const [recentRcTimeRange, setRecentRcTimeRange] = useState<TimeRange>('all');
  const [recentRcDateFrom, setRecentRcDateFrom] = useState('');
  const [recentRcDateTo, setRecentRcDateTo] = useState('');
  const [rcTimeRange, setRcTimeRange] = useState<TimeRange>('all');
  const [rcDateFrom, setRcDateFrom] = useState('');
  const [rcDateTo, setRcDateTo] = useState('');
  const [zcTimeRange, setZcTimeRange] = useState<TimeRange>('all');
  const [zcDateFrom, setZcDateFrom] = useState('');
  const [zcDateTo, setZcDateTo] = useState('');

  // ── Ownership state ──────────────────────────────────────────────────────
  const [ownershipModal, setOwnershipModal] = useState<{
    mode: 'claim' | 'request';
    record: RecordSet;
  } | null>(null);
  const [ownershipGroupId, setOwnershipGroupId] = useState('');
  const [ownershipAlert, setOwnershipAlert] = useState<{
    type: 'success' | 'danger' | 'warning';
    title: string;
    msg: string;
    icon: string;
    accent: string;   
  } | null>(null);

  const [recentRcDateSort, setRecentRcDateSort] = useState<SortDir>(null);
  const [rcTimeSort, setRcTimeSort] = useState<SortDir>(null);
  const [zcCreatedSort, setZcCreatedSort] = useState<SortDir>(null);
  const [zcUpdatedSort, setZcUpdatedSort] = useState<SortDir>(null);

  const typeDropdownRef = useRef<HTMLDivElement>(null);
  const statusDropdownRef = useRef<HTMLDivElement>(null);
  const ttlDropdownRef = useRef<HTMLDivElement>(null);

  const { data: zoneData, isLoading: zoneLoading } = useQuery({
    queryKey: ['zone', id],
    queryFn: async () => {
      const res = await zonesService.getZone(id);
      return res.data.zone;
    },
    enabled: Boolean(id),
  });

  const { data: adminGroupData } = useQuery({
    queryKey: ['admin-group', zoneData?.adminGroupId],
    queryFn: async () => {
      const res = await groupsService.getGroup(zoneData!.adminGroupId);
      return res.data;
    },
    enabled: Boolean(zoneData?.adminGroupId) && !isSuper,
  });
  const isZoneAdmin = isSuper || Boolean(
    profile?.id &&
    adminGroupData?.members?.some((m) => m.id === profile.id)
  );

  const { data: groupsData } = useQuery({
    queryKey: ['all-groups', isSuper],
    queryFn: async () => {
      const res = await groupsService.getGroups(isSuper, '', 3000);
      return res.data.groups ?? [];
    },
    enabled: isZoneAdmin || Boolean(zoneData?.shared), // load for zone admins and shared zones
  });

  const { data: myGroupsData } = useQuery({
    queryKey: ['my-groups', profile?.id],
    queryFn: async () => {
      const res = await groupsService.getGroups(false, '');
      return res.data.groups ?? [];
    },
    enabled: Boolean(profile?.id),
  });
  const userGroupIds = (myGroupsData ?? []).map((g) => g.id);
  const { data: backendIds } = useQuery({
    queryKey: ['backend-ids'],
    queryFn: async () => {
      const res = await zonesService.getBackendIds();
      return res.data ?? [];
    },
  });

  const {
    records, isLoading: recordsLoading,
    nextPage: recordsNext, prevPage: recordsPrev,
    nextPageEnabled: recNextEnabled, prevPageEnabled: recPrevEnabled,
    getPanelTitle: recPanelTitle,
    search: searchRecords,
    refetch: refetchRecords,
    createRecord, updateRecord, deleteRecord,
    isCreatePending, isUpdatePending, isDeletePending,
  } = useZoneRecords(id);

  // Update the available type list only when no type filter is active (unfiltered results).
  // This prevents the dropdown from losing types like SOA when NS is selected.
  useEffect(() => {
    if (!typeFilter) {
      const types = Array.from(new Set(records.map((r) => r.type))).sort();
      if (types.length > 0) setAvailableTypes(types);
    }
  }, [records, typeFilter]);

  // ── Sync zone ──────────────────────────────────────────────────────────────
  const syncMutation = useMutation({
    mutationFn: () => zonesService.syncZone(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['zone', id] }),
  });

  // ── Update zone (Manage Zone tab) ──────────────────────────────────────────
  const updateZoneMutation = useMutation({
    mutationFn: (zone: Zone) => zonesService.updateZone(id, zone),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['zone', id] });
      setZoneUpdateMode('idle');
      setZoneTabAlert({ type: 'success', msg: 'Zone updated successfully.' });
      setTimeout(() => setZoneTabAlert(null), 3000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Failed to update zone.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setZoneTabAlert({ type: 'danger', msg });
      setTimeout(() => setZoneTabAlert(null), 8000);
    },
  });

  // ── Abandon (delete) zone ──────────────────────────────────────────────────
  const abandonZoneMutation = useMutation({
    mutationFn: () => zonesService.deleteZone(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['deleted-zones'] });
      navigate('/zones');
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Failed to abandon zone.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setZoneTabAlert({ type: 'danger', msg });
      setTimeout(() => setZoneTabAlert(null), 8000);
    },
  });

  // ── Save ACL rule ──────────────────────────────────────────────────────────
  const saveAclRuleMutation = useMutation({
    mutationFn: async ({ zone, modal }: { zone: Zone; modal: AclRuleForm }) => {
      let finalRule: AclRule = {
        accessLevel: modal.rule.accessLevel as AclRule['accessLevel'],
        recordTypes: modal.rule.recordTypes.length ? modal.rule.recordTypes : undefined,
        recordMask: modal.rule.recordMask || undefined,
        description: modal.rule.description || undefined,
      };
      if (modal.rule.priority === 'User' && modal.rule.userName) {
        const userRes = await profileService.getUserDataByUsername(modal.rule.userName);
        finalRule = { ...finalRule, userId: userRes.data.id };
      } else if (modal.rule.priority === 'Group' && modal.rule.groupId) {
        finalRule = { ...finalRule, groupId: modal.rule.groupId };
      }
      const newRules = [...(zone.acl?.rules ?? [])];
      if (modal.mode === 'edit' && modal.ruleIndex !== undefined) {
        newRules[modal.ruleIndex] = finalRule;
      } else {
        newRules.push(finalRule);
      }
      return zonesService.updateZone(id, { ...zone, acl: { rules: newRules } });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['zone', id] });
      setAclRuleModal(null);
      setAclAlert({ type: 'success', msg: 'ACL rule saved.' });
      setTimeout(() => setAclAlert(null), 3000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] }; statusText?: string; status?: number } };
      const data = error.response?.data;
      let msg = 'Failed to save ACL rule.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setAclRuleModal(null);
      setAclAlert({ type: 'danger', msg });
      setTimeout(() => setAclAlert(null), 8000);
    },
  });

  // ── Delete ACL rule ────────────────────────────────────────────────────────
  const deleteAclRuleMutation = useMutation({
    mutationFn: ({ zone, index }: { zone: Zone; index: number }) => {
      const newRules = [...(zone.acl?.rules ?? [])];
      newRules.splice(index, 1);
      return zonesService.updateZone(id, { ...zone, acl: { rules: newRules } });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['zone', id] });
      setAclDeleteModal(null);
      setAclAlert({ type: 'success', msg: 'ACL rule deleted.' });
      setTimeout(() => setAclAlert(null), 3000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Failed to delete ACL rule.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setAclDeleteModal(null);
      setAclAlert({ type: 'danger', msg });
      setTimeout(() => setAclAlert(null), 8000);
    },
  });

  // ── Ownership transfer mutations (shared zones) ───────────────────────────
  const invalidateRecords = () =>
    void queryClient.invalidateQueries({ queryKey: ['zone-recordsets', id] });

  const claimOrRequestOwnershipMutation = useMutation({
    mutationFn: ({ record, groupId }: { record: RecordSet; groupId: string }) => {
      return recordsService.updateRecordSet(id, record.id, {
        ...record,
        recordSetGroupChange: {
          ownershipTransferStatus: 'Requested',
          requestedOwnerGroupId: groupId,
        },
      });
    },
    onSuccess: (_, { record }) => {
      invalidateRecords();
      setOwnershipModal(null);
      setOwnershipGroupId('');
      const hasOwner = Boolean(record.ownerGroupId);
      setOwnershipAlert(hasOwner
        ? { type: 'success', title: 'Transfer Requested', icon: 'bi-arrow-left-right', accent: '#6366f1',
            msg: `A transfer request for “${record.name}” has been sent to the current owner group.` }
        : { type: 'success', title: 'Ownership Claimed', icon: 'bi-person-check-fill', accent: '#0d9488',
            msg: `You have successfully claimed ownership of “${record.name}”.` }
      );
      setTimeout(() => setOwnershipAlert(null), 5000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Could not submit the ownership request.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setOwnershipAlert({ type: 'danger', title: 'Request Failed', icon: 'bi-exclamation-octagon-fill', accent: '#dc2626', msg });
      setTimeout(() => setOwnershipAlert(null), 8000);
    },
  });

  const approveOwnershipMutation = useMutation({
    mutationFn: (record: RecordSet) =>
      recordsService.updateRecordSet(id, record.id, {
        ...record,
        recordSetGroupChange: { ...record.recordSetGroupChange, ownershipTransferStatus: 'ManuallyApproved' },
      }),
    onSuccess: (_, record) => {
      invalidateRecords();
      setOwnershipAlert({ type: 'success', title: 'Transfer Approved', icon: 'bi-check-circle-fill', accent: '#16a34a',
        msg: `Ownership of “${record.name}” has been transferred to the new owner group.` });
      setTimeout(() => setOwnershipAlert(null), 5000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Could not approve the ownership transfer.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setOwnershipAlert({ type: 'danger', title: 'Approval Failed', icon: 'bi-exclamation-octagon-fill', accent: '#dc2626', msg });
      setTimeout(() => setOwnershipAlert(null), 8000);
    },
  });

  const rejectOwnershipMutation = useMutation({
    mutationFn: (record: RecordSet) =>
      recordsService.updateRecordSet(id, record.id, {
        ...record,
        recordSetGroupChange: { ...record.recordSetGroupChange, ownershipTransferStatus: 'ManuallyRejected' },
      }),
    onSuccess: (_, record) => {
      invalidateRecords();
      setOwnershipAlert({ type: 'warning', title: 'Transfer Rejected', icon: 'bi-x-circle-fill', accent: '#ea580c',
        msg: `The ownership transfer request for “${record.name}” has been rejected.` });
      setTimeout(() => setOwnershipAlert(null), 5000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Could not reject the ownership transfer.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setOwnershipAlert({ type: 'danger', title: 'Rejection Failed', icon: 'bi-exclamation-octagon-fill', accent: '#dc2626', msg });
      setTimeout(() => setOwnershipAlert(null), 8000);
    },
  });

  const cancelOwnershipRequestMutation = useMutation({
    mutationFn: (record: RecordSet) =>
      recordsService.updateRecordSet(id, record.id, {
        ...record,
        recordSetGroupChange: { ...record.recordSetGroupChange, ownershipTransferStatus: 'Cancelled' },
      }),
    onSuccess: (_, record) => {
      invalidateRecords();
      setOwnershipAlert({ type: 'warning', title: 'Request Cancelled', icon: 'bi-slash-circle-fill', accent: '#64748b',
        msg: `The ownership request for “${record.name}” has been cancelled.` });
      setTimeout(() => setOwnershipAlert(null), 5000);
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: string | { errors?: string[] } } };
      const data = error.response?.data;
      let msg = 'Could not cancel the ownership request.';
      if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
        msg = data.errors.join('\n');
      } else if (typeof data === 'string') {
        msg = data.replace(/^"|"$/g, '');
      }
      setOwnershipAlert({ type: 'danger', title: 'Cancel Failed', icon: 'bi-exclamation-octagon-fill', accent: '#dc2626', msg });
      setTimeout(() => setOwnershipAlert(null), 8000);
    },
  });

  // ── Recent record changes (paginated, 100 per page like old portal) ─────────
  const {
    paging: recentRcPaging, nextPageUpdate: recentRcNextUpdate,
    prevPageUpdate: recentRcPrevUpdate, getPrevStartFrom: recentRcGetPrev,
    prevPageEnabled: recentRcPrevEnabled,
    getPanelTitle: recentRcPanelTitle,
  } = usePaging(100);

  const { data: recentRcData } = useQuery({
    queryKey: ['record-changes-recent', id, recentRcPaging.next],
    queryFn: async () => {
      const res = await recordsService.getRecordSetChanges(id, 100, recentRcPaging.next as string | undefined);
      return res.data;
    },
    enabled: Boolean(id),
  });

  const recentRcNextEnabled = Boolean(recentRcData?.nextId);
  const recentRcNextPage = useCallback(() => {
    recentRcNextUpdate(recentRcData?.recordSetChanges?.length ?? 0, recentRcData?.nextId);
  }, [recentRcData, recentRcNextUpdate]);
  const recentRcPrevPage = useCallback(() => { recentRcPrevUpdate(recentRcGetPrev()); }, [recentRcPrevUpdate, recentRcGetPrev]);

  // ── Record set changes ────────────────────────────────────────────────────
  const {
    paging: rcPaging, nextPageUpdate: rcNextUpdate,
    prevPageUpdate: rcPrevUpdate, getPrevStartFrom: rcGetPrev,
    prevPageEnabled: rcPrevEnabled,
    getPanelTitle: rcPanelTitle,
  } = usePaging(100);

  const { data: rcData, isLoading: rcLoading } = useQuery({
    queryKey: ['record-changes', id, rcPaging.next],
    queryFn: async () => {
      const res = await recordsService.getRecordSetChanges(id, 100, rcPaging.next as string | undefined);
      return res.data;
    },
    enabled: Boolean(id) && activeTab === 'recordChanges',
  });

  const rcNextEnabled = Boolean(rcData?.nextId);

  const rcNextPage = useCallback(() => {
    rcNextUpdate(rcData?.recordSetChanges?.length ?? 0, rcData?.nextId);
  }, [rcData, rcNextUpdate]);
  const rcPrevPage = useCallback(() => { rcPrevUpdate(rcGetPrev()); }, [rcPrevUpdate, rcGetPrev]);

  // ── Zone changes ───────────────────────────────────────────────────────────
  const {
    paging: zcPaging, nextPageUpdate: zcNextUpdate,
    prevPageUpdate: zcPrevUpdate, getPrevStartFrom: zcGetPrev,
    prevPageEnabled: zcPrevEnabled,
    getPanelTitle: zcPanelTitle,
  } = usePaging(100);

  const { data: zcData, isLoading: zcLoading } = useQuery({
    queryKey: ['zone-changes', id, zcPaging.next],
    queryFn: async () => {
      const res = await zonesService.getZoneChanges(100, zcPaging.next as string | undefined, id);
      return res.data;
    },
    enabled: Boolean(id) && activeTab === 'zoneChanges',
  });

  const zcNextEnabled = Boolean(zcData?.nextId);

  const zcNextPage = useCallback(() => {
    zcNextUpdate(zcData?.zoneChanges?.length ?? 0, zcData?.nextId);
  }, [zcData, zcNextUpdate]);
  const zcPrevPage = useCallback(() => { zcPrevUpdate(zcGetPrev()); }, [zcPrevUpdate, zcGetPrev]);

  useEffect(() => {
    if (!zcData?.zoneChanges?.length) return;
    const changes = zcData.zoneChanges;

    const newUserIds = [...new Set(changes.map(c => c.userId))]
      .filter(uid => uid && uid !== 'system' && !zcUserNames[uid]);
    newUserIds.forEach(uid => {
      api.get<{ userName: string }>(`/users/${uid}`)
        .then(res => setZcUserNames(prev => ({ ...prev, [uid]: res.data.userName })))
        .catch(() => {}); // keep showing userId if lookup fails
    });

    const newGroupIds = [...new Set(changes.map(c => c.zone.adminGroupId))]
      .filter(gid => gid && !zcGroupNames[gid]);
    newGroupIds.forEach(gid => {
      groupsService.getGroup(gid)
        .then(res => setZcGroupNames(prev => ({ ...prev, [gid]: res.data.name })))
        .catch(() => {});
    });
  }, [zcData]);

  // ── Outside-click for filter dropdowns ────────────────────────────────────
  React.useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (typeDropdownRef.current && !typeDropdownRef.current.contains(e.target as Node)) {
        setTypeDropdownOpen(false);
      }
      if (statusDropdownRef.current && !statusDropdownRef.current.contains(e.target as Node)) {
        setStatusDropdownOpen(false);
      }
      if (ttlDropdownRef.current && !ttlDropdownRef.current.contains(e.target as Node)) {
        setTtlDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // ── Handlers ───────────────────────────────────────────────────────────────
  const handleCreateRecord = (data: Partial<RecordSet>) => {
    const payload: Partial<RecordSet> = data.ownerGroupId
      ? {
          ...data,
          recordSetGroupChange: data.recordSetGroupChange ?? {
            ownershipTransferStatus: 'AutoApproved',
          },
        }
      : data;
    createRecord(payload, { onSuccess: () => setShowRecordForm(false) });
  };

  const handleUpdateRecord = (data: Partial<RecordSet>) => {
    if (!editRecord) return;
    const recordSetGroupChange: RecordSetGroupChange | undefined =
      editRecord.recordSetGroupChange ??
      (editRecord.ownerGroupId
        ? { requestedOwnerGroupId: editRecord.ownerGroupId, ownershipTransferStatus: 'AutoApproved' }
        : undefined);
    updateRecord(
      {
        recordSetId: editRecord.id,
        record: {
          id: editRecord.id,
          zoneId: editRecord.zoneId,
          ...data,
          ...(recordSetGroupChange && { recordSetGroupChange }),
        },
      },
      { onSuccess: () => setEditRecord(null) },
    );
  };

  const handleDeleteConfirm = () => {
    if (recordToDelete) {
      deleteRecord(recordToDelete.id, { onSuccess: () => setRecordToDelete(null) });
    }
  };

  const initials = (name: string) =>
    name.split(/[-_.]+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('') || name.slice(0, 2).toUpperCase();

  useEffect(() => {
    if (zoneData) {
      setCrumbs([{ label: 'Zones', to: '/zones' }, { label: zoneData.name }]);
    }
    return () => setCrumbs(null);
  }, [zoneData, setCrumbs]);

  useEffect(() => {
    if (zoneData) {
      setZoneFormData({ ...zoneData, adminGroupId: '' });
      setZoneConnOpen(Boolean(zoneData.connection?.keyName || zoneData.connection?.primaryServer));
      setZoneTransferOpen(Boolean(zoneData.transferConnection?.keyName || zoneData.transferConnection?.primaryServer));
      const cron = zoneData.recurrenceSchedule ?? '';
      setSyncSchedule(cron);
      setSyncScheduleRemove(false);
      const parts = cron.split(' ');
      if (parts.length >= 6) {
        const h = parseInt(parts[2], 10);
        const m = parseInt(parts[1], 10);
        const dayPart = parts[5];
        if (!isNaN(h)) setSyncHour(h);
        if (!isNaN(m)) setSyncMinute(m);
        if (dayPart && dayPart !== '*') setSyncDays(dayPart.split(',').filter(Boolean));
      }
    }
  }, [zoneData]);

  if (zoneLoading) return <LoadingSpinner />;
  if (!zoneData) return <div className="alert alert-danger m-4">Zone not found.</div>;

  return (
    <div>
      {/* ── Page header ── */}
      <div className="rounded-3 mb-3 d-flex justify-content-between align-items-center vds-page-header vds-page-header--lg">
        <div className="d-flex align-items-center gap-3">
          <div className="vds-zone-detail-avatar">{initials(zoneData.name)}</div>
          <div>
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <h4 className="mb-0 fw-bold vds-page-header__title">{zoneData.name}</h4>
              <span className={`vds-zone-status-badge ${statusClass(zoneData.status)}`}>{zoneData.status}</span>
              <span className={`vds-access-badge ${zoneData.shared ? 'vds-access-badge--shared' : 'vds-access-badge--private'}`}>
                <i className={`bi ${zoneData.shared ? 'bi-share-fill' : 'bi-lock-fill'} me-1`} style={{ fontSize: '0.65rem' }} />
                {zoneData.shared ? 'Shared' : 'Private'}
              </span>
            </div>
            <small className="text-muted">{zoneData.email}</small>
          </div>
        </div>
        <button type="button" className="btn btn-sm d-flex align-items-center gap-1 vds-btn-nav" onClick={() => navigate(-1)}>
          <i className="bi bi-arrow-left" />
          Back to Zones
        </button>
      </div>

      <div className="vds-zone-meta-strip mb-3">
        <div
          className="vds-zone-meta-item"
          style={{ cursor: 'pointer', userSelect: 'none' }}
          title="Click to copy Zone ID"
          onClick={() => {
            void navigator.clipboard.writeText(zoneData.id);
            setCopiedZoneId(true);
            setTimeout(() => setCopiedZoneId(false), 1800);
          }}
        >
          <div className="vds-zone-meta-icon vds-zone-meta-icon--gray">
            <i className={`bi ${copiedZoneId ? 'bi-check-lg' : 'bi-tag'}`} />
          </div>
          <div>
            <div className="vds-zone-meta-label">
              Zone ID <i className="bi bi-clipboard ms-1" style={{ fontSize: '0.65rem', opacity: 0.6 }} />
            </div>
            <div className="vds-zone-meta-value vds-zone-meta-mono">
              {copiedZoneId ? <span style={{ color: '#22c55e' }}>Copied!</span> : zoneData.id}
            </div>
          </div>
        </div>
        <div className="vds-zone-meta-item">
          <div className="vds-zone-meta-icon vds-zone-meta-icon--blue">
            <i className="bi bi-people-fill" />
          </div>
          <div>
            <div className="vds-zone-meta-label">Admin Group</div>
            <div className="vds-zone-meta-value">{zoneData.adminGroupName ?? zoneData.adminGroupId}</div>
          </div>
        </div>
        <div className="vds-zone-meta-item">
          <div className="vds-zone-meta-icon vds-zone-meta-icon--teal">
            <i className="bi bi-calendar3" />
          </div>
          <div>
            <div className="vds-zone-meta-label">Created</div>
            <div className="vds-zone-meta-value">{zoneData.created ? formatDateTime(zoneData.created) : '—'}</div>
          </div>
        </div>
        <div className="vds-zone-meta-item">
          <div className="vds-zone-meta-icon vds-zone-meta-icon--purple">
            <i className="bi bi-arrow-clockwise" />
          </div>
          <div>
            <div className="vds-zone-meta-label">Last Sync</div>
            <div className="vds-zone-meta-value">{zoneData.latestSync ? formatDateTime(zoneData.latestSync) : '—'}</div>
          </div>
        </div>
        {zoneData.backendId && (
          <div className="vds-zone-meta-item">
            <div className="vds-zone-meta-icon vds-zone-meta-icon--amber">
              <i className="bi bi-server" />
            </div>
            <div>
              <div className="vds-zone-meta-label">Backend</div>
              <div className="vds-zone-meta-value">{zoneData.backendId}</div>
            </div>
          </div>
        )}
      </div>

      {/* ── Tab toolbar ── */}
      <div className="card mb-3 vds-toolbar-card">
        <div className="card-body py-2 px-3">
          <div className="vds-pill-toggle">
            <button type="button"
              className={`vds-pill-toggle__btn${activeTab === 'records' ? ' vds-pill-toggle__btn--active' : ''}`}
              onClick={() => setActiveTab('records')}>
              <i className="bi bi-file-earmark-text" />Manage Records
            </button>
            <button type="button"
              className={`vds-pill-toggle__btn${activeTab === 'recordChanges' ? ' vds-pill-toggle__btn--active' : ''}`}
              onClick={() => setActiveTab('recordChanges')}>
              <i className="bi bi-clock-history" />Record Changes
            </button>
            <button type="button"
              className={`vds-pill-toggle__btn${activeTab === 'zoneChanges' ? ' vds-pill-toggle__btn--active' : ''}`}
              onClick={() => setActiveTab('zoneChanges')}>
              <i className="bi bi-journal-text" />Zone Changes
            </button>
            <button type="button"
              className={`vds-pill-toggle__btn${activeTab === 'zone' ? ' vds-pill-toggle__btn--active' : ''}`}
              onClick={() => setActiveTab('zone')}>
              <i className="bi bi-gear" />Manage Zone
            </button>
          </div>
        </div>
      </div>

      {/* ── Manage Records ── */}
      {activeTab === 'records' && (
        <>
          {/* ── Record form modal ── */}
          {(showRecordForm || editRecord) && (
            <>
              <div
                className="modal fade show d-block"
                tabIndex={-1}
                role="dialog"
                onClick={(e) => { if (e.target === e.currentTarget) { setShowRecordForm(false); setEditRecord(null); } }}
              >
                <div className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable" role="document">
                  <div className="modal-content">
                    <div
                      className="modal-header"
                      style={{ background: editRecord ? 'linear-gradient(90deg, #3a6db5, #1e3a6e)' : 'linear-gradient(90deg, #1e5fa8, #0d1b3e)', color: '#fff' }}
                    >
                      <h5 className="modal-title d-flex align-items-center gap-2">
                        <i className={`bi ${editRecord ? 'bi-pencil-square' : 'bi-plus-circle'}`} />
                        {editRecord ? `Edit: ${editRecord.name} (${editRecord.type})` : 'Add DNS Record'}
                      </h5>
                      <button
                        type="button"
                        className="btn-close btn-close-white"
                        onClick={() => { setShowRecordForm(false); setEditRecord(null); }}
                      />
                    </div>
                    <div className="modal-body">
                      <RecordForm
                        zoneId={id}
                        zoneName={zoneData.name}
                        initialData={editRecord ?? undefined}
                        onSubmit={editRecord ? handleUpdateRecord : handleCreateRecord}
                        onCancel={() => { setShowRecordForm(false); setEditRecord(null); }}
                        mode={editRecord ? 'edit' : 'create'}
                        isSharedZone={zoneData?.shared ?? false}
                        isReverseZone={zoneData.name.endsWith('in-addr.arpa.') || zoneData.name.endsWith('ip6.arpa.')}
                        isLoading={editRecord ? isUpdatePending : isCreatePending}
                        allGroups={groupsData ?? []}
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-backdrop fade show" style={{ backdropFilter: 'blur(4px)', WebkitBackdropFilter: 'blur(4px)', opacity: 0.7 }} />
            </>
          )}

          {recordsLoading ? <LoadingSpinner /> : (
            <>
              {/* ── Recent Record Changes ── */}
              {recentRcData?.recordSetChanges?.length ? (
                <div className="vds-recent-changes-panel mb-3">
                  <div className="vds-recent-changes-panel__header">
                    <i className="bi bi-clock-history me-2" />
                    Recent Record Changes
                    <div className="ms-auto d-flex align-items-center gap-2 flex-wrap">
                      <TimeFilterDropdown
                        value={recentRcTimeRange}
                        dateFrom={recentRcDateFrom}
                        dateTo={recentRcDateTo}
                        onChange={setRecentRcTimeRange}
                        onDateFromChange={setRecentRcDateFrom}
                        onDateToChange={setRecentRcDateTo}
                      />
                      <button
                        className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                        onClick={() => void queryClient.invalidateQueries({ queryKey: ['record-changes-recent', id] })}
                        title="Refresh recent changes"
                      >
                        <i className="bi bi-arrow-clockwise" />
                        <span className="vds-btn-flat__label">Refresh</span>
                      </button>
                      <button
                        className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                        onClick={() => setActiveTab('recordChanges')}
                      >
                        <span className="vds-btn-flat__label">View all</span>
                        <i className="bi bi-arrow-right" style={{ fontSize: '0.7rem' }} />
                      </button>
                      <button
                        className="vds-panel-toggle-btn"
                        onClick={() => setRecentChangesOpen((o) => !o)}
                        title={recentChangesOpen ? 'Collapse' : 'Expand'}
                        aria-label={recentChangesOpen ? 'Collapse' : 'Expand'}
                      >
                        <i className={`bi bi-chevron-${recentChangesOpen ? 'up' : 'down'}`} />
                      </button>
                    </div>
                  </div>
                  {recentChangesOpen && (
                    <>
                      <div className="vds-zones-table-wrap">
                        <table className="vds-zones-table">
                        <thead>
                          <tr>
                            <th>Record</th>
                            <th>Type</th>
                            <th>Change</th>
                            <th>Status</th>
                            <th>User</th>
                            <th
                              onClick={() => setRecentRcDateSort((d) => d === 'asc' ? 'desc' : 'asc')}
                              style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                            >Date <SortArrow dir={recentRcDateSort} /></th>
                            <th>Additional Info</th>
                          </tr>
                        </thead>
                        <tbody>
                          {[...(recentRcData.recordSetChanges)]
                            .filter((c) => inRange(c.created, recentRcTimeRange, recentRcDateFrom, recentRcDateTo))
                            .sort((a, b) => recentRcDateSort
                              ? (recentRcDateSort === 'asc' ? 1 : -1) * (new Date(a.created).getTime() - new Date(b.created).getTime())
                              : 0)
                            .map((c) => (
                            <tr key={c.id}>
                              <td className="fw-semibold vds-table-primary">{c.recordSet.name}</td>
                              <td><span className="vds-record-type-badge">{c.recordSet.type}</span></td>
                              <td><span className={`vds-change-badge vds-change-badge--${c.changeType.toLowerCase()}`}>{c.changeType}</span></td>
                              <td><span className={`vds-zone-status-badge ${changeStatusClass(c.status)}`}>{c.status}</span></td>
                              <td className="vds-table-secondary vds-table-nowrap small">{c.userName ?? c.userId}</td>
                              <td className="vds-table-secondary vds-table-nowrap small">{formatDateTime(c.created)}</td>
                              <td className="small">
                                {/* systemMessage always shown (e.g. "Change applied via zone sync") */}
                                {c.systemMessage && <div className="vds-table-secondary mb-1">{c.systemMessage}</div>}
                                {c.status !== 'Failed' && (
                                  <div className="d-flex flex-column gap-1">
                                    {c.changeType === 'Create' && (
                                      <button
                                        className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                        onClick={() => setViewingRecordSet({ label: 'Created Record Set', rs: c.recordSet })}>
                                        <i className="bi bi-eye" />View created recordset
                                      </button>
                                    )}
                                    {c.changeType === 'Update' && (<>
                                      <button
                                        className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                        onClick={() => setViewingRecordSet({ label: 'New Record Set', rs: c.recordSet })}>
                                        <i className="bi bi-eye" />View new recordset
                                      </button>
                                      {c.updates && (
                                        <button
                                          className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                          onClick={() => setViewingRecordSet({ label: 'Old Record Set', rs: c.updates! })}>
                                          <i className="bi bi-clock-history" />View old recordset
                                        </button>
                                      )}
                                    </>)}
                                    {c.changeType === 'Delete' && (
                                      <button
                                        className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                        onClick={() => setViewingRecordSet({ label: 'Deleted Record Set', rs: c.recordSet })}>
                                        <i className="bi bi-clock-history" />View deleted recordset
                                      </button>
                                    )}
                                  </div>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    {(recentRcNextEnabled || recentRcPrevEnabled) && (
                      <Pagination onPrev={recentRcPrevPage} onNext={recentRcNextPage}
                        prevEnabled={recentRcPrevEnabled} nextEnabled={recentRcNextEnabled}
                        panelTitle={recentRcPanelTitle()} />
                    )}
                    </>
                  )}
                </div>
              ) : null}

              {/* Records toolbar */}
              <div className="card mb-2 vds-toolbar-card">
                <div className="card-body py-2 px-3">
                  <div className="d-flex gap-2 align-items-center flex-wrap">
                    <div className="vds-record-count-badge">
                      <i className="bi bi-file-earmark-text vds-record-count-badge__icon" />
                      <span className="vds-record-count-badge__num">
                        {records.filter((r) =>
                          (!statusFilter || r.status === statusFilter) &&
                          (ttlFilter === null || r.ttl === ttlFilter)
                        ).length}
                      </span>
                      <span className="vds-record-count-badge__label">records</span>
                    </div>

                    <div className="vds-search-group input-group input-group-sm" style={{ width: 220, flexShrink: 1 }}>
                      <span className="input-group-text border-0 bg-transparent pe-1">
                        <i className="bi bi-search text-muted" />
                      </span>
                      <input
                        type="text"
                        className="form-control border-0 ps-0 shadow-none bg-transparent"
                        placeholder="Filter by name"
                        value={nameFilter}
                        onChange={(e) => {
                          setNameFilter(e.target.value);
                          if (e.target.value === '') searchRecords({ name: '', type: typeFilter });
                        }}
                        onKeyDown={(e) => e.key === 'Enter' && searchRecords({ name: nameFilter, type: typeFilter })}
                      />
                    </div>

                    {/* Type filter dropdown */}
                    <div ref={typeDropdownRef} className="position-relative">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setTypeDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-tag" />
                        <span className="vds-btn-flat__label">Type</span>
                        {typeFilter && <span className="vds-filter-chip--accent">{typeFilter}</span>}
                        <i className={`bi bi-chevron-${typeDropdownOpen ? 'up' : 'down'} ms-1`} style={{ fontSize: '0.65rem', color: '#506080' }} />
                      </button>
                      {typeDropdownOpen && (
                        <ul className="list-group position-absolute shadow" style={{ zIndex: 1050, top: 'calc(100% + 4px)', left: 0, minWidth: '110px', borderRadius: '0.55rem', overflow: 'hidden', border: '1px solid #d4dbe8' }}>
                          {availableTypes.map((t) => (
                            <li
                              key={t}
                              className={`list-group-item list-group-item-action py-2 px-3 vds-suggestion-item${typeFilter === t ? ' vds-role-item--selected' : ''}`}
                              style={{ cursor: 'pointer', fontSize: '0.83rem', fontWeight: 600, letterSpacing: '0.02em' }}
                              onMouseDown={() => {
                                const next = typeFilter === t ? '' : t;
                                setTypeFilter(next);
                                searchRecords({ name: nameFilter, type: next });
                                setTypeDropdownOpen(false);
                              }}
                            >
                              {t}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>

                    {/* Status filter dropdown */}
                    <div ref={statusDropdownRef} className="position-relative">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setStatusDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-circle-half" />
                        <span className="vds-btn-flat__label">Status</span>
                        {statusFilter && <span className="vds-filter-chip--accent">{statusFilter}</span>}
                        <i className={`bi bi-chevron-${statusDropdownOpen ? 'up' : 'down'} ms-1`} style={{ fontSize: '0.65rem', color: '#506080' }} />
                      </button>
                      {statusDropdownOpen && (
                        <ul className="list-group position-absolute shadow" style={{ zIndex: 1050, top: 'calc(100% + 4px)', left: 0, minWidth: '130px', borderRadius: '0.55rem', overflow: 'hidden', border: '1px solid #d4dbe8' }}>
                          {Array.from(new Set(records.map((r) => r.status))).map((s) => (
                            <li
                              key={s}
                              className={`list-group-item list-group-item-action py-2 px-3 vds-suggestion-item${statusFilter === s ? ' vds-role-item--selected' : ''}`}
                              style={{ cursor: 'pointer', fontSize: '0.83rem', fontWeight: 600 }}
                              onMouseDown={() => {
                                setStatusFilter(statusFilter === s ? '' : s);
                                setStatusDropdownOpen(false);
                              }}
                            >
                              {s}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>

                    {/* TTL filter dropdown */}
                    <div ref={ttlDropdownRef} className="position-relative">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setTtlDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-clock" />
                        <span className="vds-btn-flat__label">TTL</span>
                        {ttlFilter !== null && <span className="vds-filter-chip--accent">{ttlFilter}s</span>}
                        <i className={`bi bi-chevron-${ttlDropdownOpen ? 'up' : 'down'} ms-1`} style={{ fontSize: '0.65rem', color: '#506080' }} />
                      </button>
                      {ttlDropdownOpen && (
                        <ul className="list-group position-absolute shadow" style={{ zIndex: 1050, top: 'calc(100% + 4px)', left: 0, minWidth: '110px', borderRadius: '0.55rem', overflow: 'hidden', border: '1px solid #d4dbe8' }}>
                          {Array.from(new Set(records.map((r) => r.ttl))).sort((a, b) => a - b).map((t) => (
                            <li
                              key={t}
                              className={`list-group-item list-group-item-action py-2 px-3 vds-suggestion-item${ttlFilter === t ? ' vds-role-item--selected' : ''}`}
                              style={{ cursor: 'pointer', fontSize: '0.83rem', fontWeight: 600 }}
                              onMouseDown={() => {
                                setTtlFilter(ttlFilter === t ? null : t);
                                setTtlDropdownOpen(false);
                              }}
                            >
                              {t}s
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>

                    <div className="ms-auto d-flex align-items-center gap-2">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-nav"
                        onClick={() => { setShowRecordForm(true); setEditRecord(null); }}
                      >
                        <i className="bi bi-plus-circle-fill" />Create New Record
                      </button>
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        disabled={syncMutation.isPending}
                        onClick={() => syncMutation.mutate()}
                        title="Sync zone with DNS server"
                      >
                        <i className={`bi bi-arrow-repeat${syncMutation.isPending ? ' vds-spin' : ''}`} />
                        <span className="vds-btn-flat__label">{syncMutation.isPending ? 'Syncing…' : 'Sync Zone'}</span>
                      </button>
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => {
                          setNameFilter('');
                          setTypeFilter('');
                          setStatusFilter('');
                          setTtlFilter(null);
                          searchRecords({ name: '', type: '' });
                          void refetchRecords();
                        }}
                      >
                        <i className="bi bi-arrow-clockwise" />
                        <span className="vds-btn-flat__label">Refresh</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Active filter tags */}
              {(nameFilter || typeFilter || statusFilter || ttlFilter !== null) && (
                <div className="d-flex flex-wrap align-items-center gap-2 mb-2 px-1">
                  {nameFilter && (
                    <span className="vds-active-filter-tag">
                      <i className="bi bi-search me-1" style={{ fontSize: '0.7rem' }} />
                      Name: <strong className="ms-1">{nameFilter}</strong>
                      <button className="btn-close ms-2" style={{ fontSize: '0.6rem' }}
                        onClick={() => { setNameFilter(''); searchRecords({ name: '', type: typeFilter }); }} />
                    </span>
                  )}
                  {typeFilter && (
                    <span className="vds-active-filter-tag">
                      <i className="bi bi-tag me-1" style={{ fontSize: '0.7rem' }} />
                      Type: <strong className="ms-1">{typeFilter}</strong>
                      <button className="btn-close ms-2" style={{ fontSize: '0.6rem' }}
                        onClick={() => { setTypeFilter(''); searchRecords({ name: nameFilter, type: '' }); }} />
                    </span>
                  )}
                  {statusFilter && (
                    <span className="vds-active-filter-tag">
                      <i className="bi bi-circle-half me-1" style={{ fontSize: '0.7rem' }} />
                      Status: <strong className="ms-1">{statusFilter}</strong>
                      <button className="btn-close ms-2" style={{ fontSize: '0.6rem' }}
                        onClick={() => setStatusFilter('')} />
                    </span>
                  )}
                  {ttlFilter !== null && (
                    <span className="vds-active-filter-tag">
                      <i className="bi bi-clock me-1" style={{ fontSize: '0.7rem' }} />
                      TTL: <strong className="ms-1">{ttlFilter}s</strong>
                      <button className="btn-close ms-2" style={{ fontSize: '0.6rem' }}
                        onClick={() => setTtlFilter(null)} />
                    </span>
                  )}
                  <button
                    className="btn btn-sm vds-btn-clear-all"
                    onClick={() => { setNameFilter(''); setTypeFilter(''); setStatusFilter(''); setTtlFilter(null); searchRecords({ name: '', type: '' }); }}
                  >
                    <i className="bi bi-x-circle me-1" />Clear All
                  </button>
                </div>
              )}

              <RecordsTable
                records={records.filter((r) =>
                  (!statusFilter || r.status === statusFilter) &&
                  (ttlFilter === null || r.ttl === ttlFilter)
                )}
                onEdit={(rec) => { setEditRecord(rec); setShowRecordForm(false); }}
                onDelete={setRecordToDelete}
                isSharedZone={zoneData.shared ?? false}
                isSuper={isSuper}
                isSupport={isSupport}
                isZoneAdmin={isZoneAdmin}
                userGroupIds={userGroupIds}
                onRequestOwnership={(rec) => {
                  const mode = rec.ownerGroupId ? 'request' : 'claim';
                  setOwnershipGroupId('');
                  setOwnershipModal({ mode, record: rec });
                }}
                onCloseOwnershipRequest={(rec) => cancelOwnershipRequestMutation.mutate(rec)}
                onApproveOwnership={(rec) => approveOwnershipMutation.mutate(rec)}
                onRejectOwnership={(rec) => rejectOwnershipMutation.mutate(rec)}
              />
              {(recNextEnabled || recPrevEnabled) && (
                <Pagination onPrev={recordsPrev} onNext={recordsNext}
                  prevEnabled={recPrevEnabled} nextEnabled={recNextEnabled}
                  panelTitle={recPanelTitle()} />
              )}
            </>
          )}

          {/* Delete modal */}
          {recordToDelete && (
            <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
              onMouseDown={(e) => { if (e.target === e.currentTarget) setRecordToDelete(null); }}>
              <div className="modal-dialog modal-dialog-centered">
                <div className="modal-content">
                  <div className="modal-header">
                    <h5 className="modal-title fw-semibold">Delete Record</h5>
                    <button type="button" className="btn-close" onClick={() => setRecordToDelete(null)} />
                  </div>
                  <div className="modal-body">
                    Delete <strong>{recordToDelete.name}</strong> ({recordToDelete.type})? This cannot be undone.
                  </div>
                  <div className="modal-footer">
                    <button className="btn btn-outline-secondary" onClick={() => setRecordToDelete(null)} disabled={isDeletePending}>Cancel</button>
                    <button className="btn btn-danger" onClick={handleDeleteConfirm} disabled={isDeletePending}>
                      {isDeletePending
                        ? <><span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true" />Deleting…</>
                        : <><i className="bi bi-trash me-1" />Delete</>}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ── Ownership toast notification ── */}
          {ownershipAlert && (
            <div
              className="vds-ownership-toast"
              role="alert"
              style={{ borderLeftColor: ownershipAlert.accent }}
            >
              <div className="vds-ownership-toast__icon" style={{ background: ownershipAlert.accent + '1a', color: ownershipAlert.accent }}>
                <i className={`bi ${ownershipAlert.icon}`} />
              </div>
              <div className="vds-ownership-toast__body">
                <div className="vds-ownership-toast__title" style={{ color: ownershipAlert.accent }}>
                  {ownershipAlert.title}
                </div>
                <div className="vds-ownership-toast__msg">{ownershipAlert.msg}</div>
              </div>
              <button
                className="vds-ownership-toast__close"
                onClick={() => setOwnershipAlert(null)}
                aria-label="Dismiss"
              >
                <i className="bi bi-x" />
              </button>
            </div>
          )}

          {/* ── Ownership Claim / Request Transfer Modal ── */}
          {ownershipModal && (
            <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
              onMouseDown={(e) => { if (e.target === e.currentTarget) { setOwnershipModal(null); setOwnershipGroupId(''); } }}>
              <div className="modal-dialog modal-dialog-centered">
                <div className="modal-content" style={{ borderRadius: '1rem', overflow: 'hidden' }}>
                  <div
                    className="modal-header pb-3"
                    style={{
                      background: ownershipModal.mode === 'claim'
                        ? 'linear-gradient(135deg,#0d9488,#14b8a6)'
                        : 'linear-gradient(135deg,#4f46e5,#6366f1)',
                      color: '#fff',
                      border: 'none',
                    }}
                  >
                    <div className="d-flex align-items-center gap-2">
                      <i className={`bi ${ownershipModal.mode === 'claim' ? 'bi-person-plus-fill' : 'bi-arrow-left-right'} fs-5`} />
                      <h5 className="modal-title fw-bold mb-0">
                        {ownershipModal.mode === 'claim' ? 'Claim Record Ownership' : 'Request Ownership Transfer'}
                      </h5>
                    </div>
                    <button
                      type="button"
                      className="btn-close btn-close-white"
                      onClick={() => { setOwnershipModal(null); setOwnershipGroupId(''); }}
                    />
                  </div>

                  <div className="modal-body px-4 py-3">
                    {/* Record info */}
                    <div className="d-flex align-items-center gap-3 mb-4 p-3 rounded-3"
                      style={{ background: 'linear-gradient(135deg,#f8faff,#eff6ff)', border: '1px solid #dbeafe' }}>
                      <div>
                        <div className="fw-bold text-primary" style={{ fontSize: '1rem' }}>
                          {ownershipModal.record.name}
                        </div>
                        <div className="d-flex align-items-center gap-2 mt-1">
                          <span className="vds-record-type-badge">{ownershipModal.record.type}</span>
                          {ownershipModal.record.ownerGroupId && (
                            <span className="vds-owner-group-chip" style={{ fontSize: '0.7rem' }}>
                              <i className="bi bi-people-fill" />
                              Current: {ownershipModal.record.ownerGroupName ?? ownershipModal.record.ownerGroupId.slice(0, 10) + '…'}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="mb-3">
                      <label className="form-label fw-semibold">
                        {ownershipModal.mode === 'claim' ? 'Assign ownership to' : 'Request transfer to'}
                        <span className="text-danger ms-1">*</span>
                      </label>
                      <select
                        className="form-select"
                        value={ownershipGroupId}
                        onChange={(e) => setOwnershipGroupId(e.target.value)}
                        style={{ borderRadius: '0.6rem' }}
                      >
                        <option value="">— Select your group —</option>
                        {(myGroupsData ?? [])
                          .filter((g) =>
                            ownershipModal.mode === 'claim' || g.id !== ownershipModal.record.ownerGroupId
                          )
                          .map((g) => (
                          <option key={g.id} value={g.id}>{g.name}</option>
                        ))}
                      </select>
                      <div className="form-text">
                        <i className="bi bi-info-circle me-1" />
                        {ownershipModal.mode === 'claim'
                          ? 'The selected group will become the owner of this record.'
                          : 'A transfer request will be sent to the current owner group for approval.'}
                      </div>
                    </div>
                  </div>

                  <div className="modal-footer" style={{ borderTop: '1px solid #e2e8f0' }}>
                    <button
                      className="btn btn-outline-secondary"
                      onClick={() => { setOwnershipModal(null); setOwnershipGroupId(''); }}
                    >
                      Cancel
                    </button>
                    <button
                      className="btn text-white fw-semibold"
                      disabled={!ownershipGroupId || claimOrRequestOwnershipMutation.isPending}
                      onClick={() => {
                        if (ownershipGroupId) {
                          claimOrRequestOwnershipMutation.mutate({
                            record: ownershipModal.record,
                            groupId: ownershipGroupId,
                          });
                        }
                      }}
                      style={{
                        background: ownershipModal.mode === 'claim'
                          ? 'linear-gradient(135deg,#0d9488,#14b8a6)'
                          : 'linear-gradient(135deg,#4f46e5,#6366f1)',
                        border: 'none',
                        borderRadius: '0.6rem',
                      }}
                    >
                      {claimOrRequestOwnershipMutation.isPending ? (
                        <><i className="bi bi-hourglass-split me-1 vds-spin" />Processing…</>
                      ) : ownershipModal.mode === 'claim' ? (
                        <><i className="bi bi-person-plus-fill me-1" />Claim Ownership</>
                      ) : (
                        <><i className="bi bi-arrow-left-right me-1" />Request Transfer</>
                      )}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── Record Change History ── */}
      {activeTab === 'recordChanges' && (
        rcLoading ? <LoadingSpinner /> : (
          <div className="vds-recent-changes-panel">
            <div className="vds-recent-changes-panel__header">
              <i className="bi bi-clock-history me-2" />
              All Record Changes
              <div className="ms-auto d-flex align-items-center gap-2 flex-wrap">
                <TimeFilterDropdown
                  value={rcTimeRange}
                  dateFrom={rcDateFrom}
                  dateTo={rcDateTo}
                  onChange={setRcTimeRange}
                  onDateFromChange={setRcDateFrom}
                  onDateToChange={setRcDateTo}
                />
                <button
                  className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                  onClick={() => void queryClient.invalidateQueries({ queryKey: ['record-changes', id] })}
                >
                  <i className="bi bi-arrow-clockwise" />
                  <span className="vds-btn-flat__label">Refresh</span>
                </button>
              </div>
            </div>
            <div className="vds-zones-table-wrap">
              <table className="vds-zones-table">
                <thead>
                  <tr>
                    <th
                      onClick={() => setRcTimeSort((d) => d === 'asc' ? 'desc' : 'asc')}
                      style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                    >Time <SortArrow dir={rcTimeSort} /></th>
                    <th>Recordset Name</th>
                    <th>Recordset Type</th>
                    <th>Change Type</th>
                    <th>User</th>
                    <th>Status</th>
                    <th>Additional Info</th>
                  </tr>
                </thead>
                <tbody>
                  {!rcData?.recordSetChanges?.length ? (
                    <tr><td colSpan={7} className="text-center text-muted py-5">
                      <i className="bi bi-clock-history fs-3 d-block mb-2 opacity-25" />
                      No record changes found.
                    </td></tr>
                  ) : [...(rcData.recordSetChanges)]
                      .filter((c) => inRange(c.created, rcTimeRange, rcDateFrom, rcDateTo))
                      .sort((a, b) => rcTimeSort
                        ? (rcTimeSort === 'asc' ? 1 : -1) * (new Date(a.created).getTime() - new Date(b.created).getTime())
                        : 0)
                      .map((c) => (
                    <tr key={c.id}>
                      <td className="vds-table-secondary vds-table-nowrap small">{formatDateTime(c.created)}</td>
                      <td className="fw-semibold vds-table-primary">{c.recordSet.name}</td>
                      <td><span className="vds-record-type-badge">{c.recordSet.type}</span></td>
                      <td>
                        <div className="d-flex align-items-center gap-1 flex-wrap">
                          <span className={`vds-change-badge vds-change-badge--${c.changeType.toLowerCase()}`}>{c.changeType}</span>
                          <button
                            className="vds-copy-id-btn"
                            title="Copy change ID"
                            onClick={() => {
                              void navigator.clipboard.writeText(c.id);
                              setCopiedRcChangeId(c.id);
                              setTimeout(() => setCopiedRcChangeId(null), 1800);
                            }}
                          >
                            <i className={`bi ${copiedRcChangeId === c.id ? 'bi-check-lg' : 'bi-clipboard'}`}
                              style={{ color: copiedRcChangeId === c.id ? '#22c55e' : undefined }} />
                          </button>
                        </div>
                      </td>
                      <td className="vds-table-secondary vds-table-nowrap small">{c.userName ?? c.userId}</td>
                      <td><span className={`vds-zone-status-badge ${changeStatusClass(c.status)}`}>{c.status}</span></td>
                      <td className="small">
                        {c.systemMessage && <div className="vds-table-secondary mb-1">{c.systemMessage}</div>}
                        {c.status !== 'Failed' && (
                          <div className="d-flex flex-column gap-1">
                            {c.changeType === 'Create' && (
                              <button className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                onClick={() => setViewingRecordSet({ label: 'Created Record Set', rs: c.recordSet })}>
                                <i className="bi bi-eye" />View created recordset
                              </button>
                            )}
                            {c.changeType === 'Delete' && (
                              <button className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                onClick={() => setViewingRecordSet({ label: 'Deleted Record Set', rs: c.recordSet })}>
                                <i className="bi bi-clock-history" />View deleted recordset
                              </button>
                            )}
                            {c.changeType === 'Update' && (<>
                              <button className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                onClick={() => setViewingRecordSet({ label: 'New Record Set', rs: c.recordSet })}>
                                <i className="bi bi-eye" />View new recordset
                              </button>
                              {c.updates && (
                                <button className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                                  onClick={() => setViewingRecordSet({ label: 'Old Record Set', rs: c.updates! })}>
                                  <i className="bi bi-clock-history" />View old recordset
                                </button>
                              )}
                            </>)}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {(rcNextEnabled || rcPrevEnabled) && (
              <Pagination onPrev={rcPrevPage} onNext={rcNextPage}
                prevEnabled={rcPrevEnabled} nextEnabled={rcNextEnabled} panelTitle={rcPanelTitle()} />
            )}
          </div>
        )
      )}

      {/* ── Zone Change History ── */}
      {activeTab === 'zoneChanges' && (
        zcLoading ? <LoadingSpinner /> : (
          <div className="vds-recent-changes-panel">
            <div className="vds-recent-changes-panel__header">
              <i className="bi bi-journal-text me-2" />
              Zone Change History
              <div className="ms-auto d-flex align-items-center gap-2 flex-wrap">
                <TimeFilterDropdown
                  value={zcTimeRange}
                  dateFrom={zcDateFrom}
                  dateTo={zcDateTo}
                  onChange={setZcTimeRange}
                  onDateFromChange={setZcDateFrom}
                  onDateToChange={setZcDateTo}
                />
                <button
                  className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                  onClick={() => void queryClient.invalidateQueries({ queryKey: ['zone-changes', id] })}
                >
                  <i className="bi bi-arrow-clockwise" />
                  <span className="vds-btn-flat__label">Refresh</span>
                </button>
              </div>
            </div>
            <div className="vds-zones-table-wrap">
              <table className="vds-zones-table">
                <thead>
                  <tr>
                    <th>User Name</th>
                    <th>Email</th>
                    <th>Access</th>
                    <th
                      onClick={() => { setZcCreatedSort((d) => d === 'asc' ? 'desc' : 'asc'); setZcUpdatedSort(null); }}
                      style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                    >Created <SortArrow dir={zcCreatedSort} /></th>
                    <th
                      onClick={() => { setZcUpdatedSort((d) => d === 'asc' ? 'desc' : 'asc'); setZcCreatedSort(null); }}
                      style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                    >Updated <SortArrow dir={zcUpdatedSort} /></th>
                    <th>Change Type</th>
                    <th>Admin Group</th>
                    <th>ACL</th>
                  </tr>
                </thead>
                <tbody>
                  {!zcData?.zoneChanges?.length ? (
                    <tr><td colSpan={8} className="text-center text-muted py-5">
                      <i className="bi bi-journal-text fs-3 d-block mb-2 opacity-25" />
                      No zone changes found.
                    </td></tr>
                  ) : [...(zcData.zoneChanges)]
                      .filter((c) => inRange(c.zone.updated ?? c.zone.created, zcTimeRange, zcDateFrom, zcDateTo))
                      .sort((a, b) => {
                        if (zcCreatedSort) return (zcCreatedSort === 'asc' ? 1 : -1) * (new Date(a.zone.created ?? '').getTime() - new Date(b.zone.created ?? '').getTime());
                        if (zcUpdatedSort) return (zcUpdatedSort === 'asc' ? 1 : -1) * (new Date(a.zone.updated ?? '').getTime() - new Date(b.zone.updated ?? '').getTime());
                        return 0;
                      })
                      .map((c) => (
                    <tr key={c.id}>
                      <td className="vds-table-secondary small">
                        {c.userId === 'system'
                          ? <span className="vds-table-mono" style={{ fontSize: '0.75rem', opacity: 0.7 }}>system</span>
                          : (zcUserNames[c.userId] ?? c.userId)}
                      </td>
                      <td className="vds-table-secondary small">{c.zone.email}</td>
                      <td>
                        <span className={`vds-access-badge ${c.zone.shared ? 'vds-access-badge--shared' : 'vds-access-badge--private'}`}>
                          <i className={`bi ${c.zone.shared ? 'bi-share-fill' : 'bi-lock-fill'} me-1`} style={{ fontSize: '0.6rem' }} />
                          {c.zone.shared ? 'Shared' : 'Private'}
                        </span>
                      </td>
                      <td className="vds-table-secondary vds-table-nowrap small">{c.zone.created ? formatDateTime(c.zone.created) : '—'}</td>
                      <td className="vds-table-secondary vds-table-nowrap small">{c.zone.updated ? formatDateTime(c.zone.updated) : '—'}</td>
                      <td>
                        <div className="d-flex align-items-center gap-1 flex-wrap">
                          <span className={`vds-change-badge vds-change-badge--${c.changeType === 'AutomatedSync' ? 'sync' : c.changeType.toLowerCase()}`}>
                            {c.changeType === 'AutomatedSync' ? 'Automated Sync' : c.changeType}
                          </span>
                          <button
                            className="vds-copy-id-btn"
                            title={`Copy change ID`}
                            onClick={() => {
                              void navigator.clipboard.writeText(c.id);
                              setCopiedChangeId(c.id);
                              setTimeout(() => setCopiedChangeId(null), 1800);
                            }}
                          >
                            <i className={`bi ${copiedChangeId === c.id ? 'bi-check-lg' : 'bi-clipboard'}`}
                              style={{ color: copiedChangeId === c.id ? '#22c55e' : undefined }} />
                          </button>
                        </div>
                      </td>
                      <td className="small">
                        {zcGroupNames[c.zone.adminGroupId]
                          ? <Link to={`/groups/${c.zone.adminGroupId}`} className="vds-table-link">{zcGroupNames[c.zone.adminGroupId]}</Link>
                          : c.zone.adminGroupId
                            ? <Link to={`/groups/${c.zone.adminGroupId}`} className="vds-table-link vds-table-mono" style={{ fontSize: '0.75rem' }}>{c.zone.adminGroupId}</Link>
                            : <span className="vds-table-secondary">—</span>}
                      </td>
                      <td>
                        {(c.zone.acl?.rules?.length ?? 0) > 0 && (
                          <button
                            className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                            onClick={() => setAclModal({ rules: c.zone.acl!.rules })}
                          >
                            <i className="bi bi-shield-lock" />ACL Rules
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {(zcNextEnabled || zcPrevEnabled) && (
              <Pagination onPrev={zcPrevPage} onNext={zcNextPage}
                prevEnabled={zcPrevEnabled} nextEnabled={zcNextEnabled} panelTitle={zcPanelTitle()} />
            )}
          </div>
        )
      )}

      {/* ── Manage Zone ── */}
      {activeTab === 'zone' && (
        <>
          {zoneTabAlert && (
            <div className={`alert alert-${zoneTabAlert.type} alert-dismissible d-flex align-items-center gap-2 mb-3`} role="alert">
              <i className={`bi ${zoneTabAlert.type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'}`} />
              {zoneTabAlert.msg}
              <button type="button" className="btn-close ms-auto" onClick={() => setZoneTabAlert(null)} />
            </div>
          )}

          {/* ── Zone Info Panel ── */}
          <div className="vds-recent-changes-panel mb-4">
            <div className="vds-recent-changes-panel__header">
              <i className="bi bi-info-circle me-2" />Zone Info
              {!isZoneAdmin && (
                <span className="ms-2 badge rounded-pill" style={{ background: 'rgba(91,143,232,0.15)', color: '#3a6bc4', fontSize: '0.7rem', fontWeight: 600 }}>
                  <i className="bi bi-eye me-1" />Read Only
                </span>
              )}
              <div className="ms-auto d-flex align-items-center gap-2">
                <button
                  className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                  onClick={() => void queryClient.invalidateQueries({ queryKey: ['zone', id] })}
                >
                  <i className="bi bi-arrow-clockwise" />
                  <span className="vds-btn-flat__label">Refresh</span>
                </button>
                <button
                  className="vds-panel-toggle-btn"
                  onClick={() => setZoneInfoOpen(o => !o)}
                  title={zoneInfoOpen ? 'Collapse' : 'Expand'}
                  aria-label={zoneInfoOpen ? 'Collapse' : 'Expand'}
                >
                  <i className={`bi bi-chevron-${zoneInfoOpen ? 'up' : 'down'}`} />
                </button>
              </div>
            </div>

            {zoneInfoOpen && (
            <div className="p-4">
              {!isZoneAdmin && zoneData && (
                <div className="row g-4">
                  {[
                    { icon: 'bi-tag', label: 'Zone ID', value: zoneData.id, mono: true, copy: true },
                    { icon: 'bi-envelope', label: 'Zone Email', value: zoneData.email },
                    { icon: 'bi-circle-half', label: 'Status', value: zoneData.status, badge: true },
                    { icon: 'bi-share-fill', label: 'Access', value: zoneData.shared ? 'Shared' : 'Private' },
                    { icon: 'bi-server', label: 'DNS Backend ID', value: zoneData.backendId ?? '—', mono: true },
                    { icon: 'bi-calendar3', label: 'Created', value: zoneData.created ? formatDateTime(zoneData.created) : '—' },
                    { icon: 'bi-pencil-square', label: 'Latest Update', value: zoneData.updated ? formatDateTime(zoneData.updated) : '—' },
                    { icon: 'bi-arrow-clockwise', label: 'Latest Sync', value: zoneData.latestSync ? formatDateTime(zoneData.latestSync) : '—' },
                  ].map(field => (
                    <div key={field.label} className="col-sm-6 col-lg-4 col-xl-3">
                      <div className="vds-zone-info-card">
                        <div className="vds-zone-info-card__icon"><i className={`bi ${field.icon}`} /></div>
                        <div className="vds-zone-info-card__label">{field.label}</div>
                        {field.badge ? (
                          <span className={`vds-zone-status-badge ${statusClass(field.value)}`}>{field.value}</span>
                        ) : field.copy ? (
                          <div
                            className="vds-zone-info-card__value vds-zone-info-card__value--clickable"
                            title="Click to copy"
                            onClick={() => {
                              void navigator.clipboard.writeText(field.value);
                              setCopiedZoneId(true);
                              setTimeout(() => setCopiedZoneId(false), 1800);
                            }}
                          >
                            <span style={{ fontFamily: 'monospace', fontSize: '0.78rem', wordBreak: 'break-all' }}>{field.value}</span>
                            <i className={`bi ${copiedZoneId ? 'bi-check-lg text-success' : 'bi-clipboard'} ms-1`} style={{ fontSize: '0.7rem', opacity: 0.6 }} />
                          </div>
                        ) : (
                          <div className={`vds-zone-info-card__value${field.mono ? ' vds-table-mono' : ''}`}>{field.value}</div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* ─── EDITABLE view (zone admin) ─── */}
              {isZoneAdmin && zoneFormData && (
                <>
                  <div className="row g-3 mb-3">
                    {/* ── Left column ── */}
                    <div className="col-lg-6">
                      {/* Zone ID (read-only, copyable) */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Zone ID</label>
                        <div
                          className="form-control vds-zone-form__input d-flex align-items-center justify-content-between"
                          style={{ cursor: 'pointer', userSelect: 'none', background: '#f4f7fb' }}
                          title="Click to copy Zone ID"
                          onClick={() => {
                            void navigator.clipboard.writeText(zoneFormData.id);
                            setCopiedZoneId(true);
                            setTimeout(() => setCopiedZoneId(false), 1800);
                          }}
                        >
                          <span className="vds-table-mono" style={{ fontSize: '0.82rem', opacity: 0.8 }}>{zoneFormData.id}</span>
                          <i className={`bi ${copiedZoneId ? 'bi-check-lg text-success' : 'bi-clipboard'}`} style={{ fontSize: '0.75rem', opacity: 0.6 }} />
                        </div>
                      </div>
                      {/* Zone Email */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Zone Email</label>
                        <input
                          type="email"
                          className="form-control vds-zone-form__input"
                          value={zoneFormData.email}
                          onChange={e => setZoneFormData(prev => prev ? { ...prev, email: e.target.value } : prev)}
                        />
                      </div>
                      {/* Status (read-only badge) */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Status</label>
                        <div className="form-control vds-zone-form__input d-flex align-items-center" style={{ background: '#f4f7fb' }}>
                          <span className={`vds-zone-status-badge ${statusClass(zoneFormData.status)}`}>{zoneFormData.status}</span>
                        </div>
                      </div>
                      {/* Access */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Access</label>
                        <select
                          className="form-select vds-zone-form__input"
                          value={String(zoneFormData.shared ?? false)}
                          onChange={e => setZoneFormData(prev => prev ? { ...prev, shared: e.target.value === 'true' } : prev)}
                        >
                          <option value="true">Shared</option>
                          <option value="false">Private</option>
                        </select>
                      </div>
                      {/* Admin Group */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Admin Group</label>
                        <GroupCombobox
                          groups={groupsData ?? []}
                          value={zoneFormData.adminGroupId}
                          onChange={(id) => setZoneFormData(prev => prev ? { ...prev, adminGroupId: id } : prev)}
                        />
                        {zoneFormData.adminGroupId && (
                          <Link
                            to={`/groups/${zoneFormData.adminGroupId}`}
                            className="vds-table-link d-inline-flex align-items-center gap-1 mt-1"
                            style={{ fontSize: '0.78rem' }}
                          >
                            <i className="bi bi-box-arrow-up-right" />View group
                          </Link>
                        )}
                      </div>
                      {/* Backend ID */}
                      <div className="mb-0">
                        <label className="vds-zone-form__label">DNS Backend ID <span className="text-muted fw-normal">(optional)</span></label>
                        <select
                          className="form-select vds-zone-form__input"
                          value={zoneFormData.backendId ?? ''}
                          onChange={e => setZoneFormData(prev => prev ? { ...prev, backendId: e.target.value || undefined } : prev)}
                        >
                          <option value="">— Default —</option>
                          {(backendIds ?? []).map(bid => (
                            <option key={bid} value={bid}>{bid}</option>
                          ))}
                        </select>
                      </div>
                    </div>

                    {/* ── Right column ── */}
                    <div className="col-lg-6">
                      {/* Created */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Created</label>
                        <div className="form-control vds-zone-form__input" style={{ background: '#f4f7fb', color: '#64748b' }}>
                          {zoneFormData.created ? formatDateTime(zoneFormData.created) : '—'}
                        </div>
                      </div>
                      {/* Latest Update */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Latest Update</label>
                        <div className="form-control vds-zone-form__input" style={{ background: '#f4f7fb', color: '#64748b' }}>
                          {zoneFormData.updated ? formatDateTime(zoneFormData.updated) : '—'}
                        </div>
                      </div>
                      {/* Latest Sync */}
                      <div className="mb-3">
                        <label className="vds-zone-form__label">Latest Sync</label>
                        <div className="form-control vds-zone-form__input" style={{ background: '#f4f7fb', color: '#64748b' }}>
                          {zoneFormData.latestSync ? formatDateTime(zoneFormData.latestSync) : '—'}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* ── Connection Information ── */}
                  <div className="mb-3">
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-flat d-flex align-items-center gap-2 mb-2"
                      onClick={() => setZoneConnOpen(o => !o)}
                    >
                      <i className={`bi bi-chevron-${zoneConnOpen ? 'down' : 'right'}`} />
                      Connection Information
                      <span className="text-muted fw-normal">(optional)</span>
                    </button>
                    {zoneConnOpen && (
                      <div className="p-3 rounded-3 border" style={{ background: '#f8fafc' }}>
                        <div className="row g-3">
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Name</label>
                            <input
                              type="text"
                              className="form-control vds-zone-form__input"
                              value={zoneFormData.connection?.keyName ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, connection: { ...(prev.connection ?? {}), keyName: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">DNS Server</label>
                            <input
                              type="text"
                              className="form-control vds-zone-form__input"
                              value={zoneFormData.connection?.primaryServer ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, connection: { ...(prev.connection ?? {}), primaryServer: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Secret</label>
                            <input
                              type="password"
                              className="form-control vds-zone-form__input"
                              placeholder="Current key hidden — enter to change"
                              value={zoneFormData.connection?.key ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, connection: { ...(prev.connection ?? {}), key: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Algorithm</label>
                            <select
                              className="form-select vds-zone-form__input"
                              value={zoneFormData.connection?.algorithm ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, connection: { ...(prev.connection ?? {}), algorithm: e.target.value } } : prev)}
                            >
                              <option value="">—</option>
                              {['HMAC-MD5','HMAC-SHA1','HMAC-SHA224','HMAC-SHA256','HMAC-SHA384','HMAC-SHA512'].map(a => (
                                <option key={a} value={a}>{a}</option>
                              ))}
                            </select>
                          </div>
                        </div>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger mt-3"
                          onClick={() => setZoneFormData(prev => prev ? { ...prev, connection: undefined } : prev)}
                        >
                          <i className="bi bi-x-circle me-1" />Clear Connection
                        </button>
                      </div>
                    )}
                  </div>

                  {/* ── Transfer Connection ── */}
                  <div className="mb-1">
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-flat d-flex align-items-center gap-2 mb-2"
                      onClick={() => setZoneTransferOpen(o => !o)}
                    >
                      <i className={`bi bi-chevron-${zoneTransferOpen ? 'down' : 'right'}`} />
                      Transfer Connection
                      <span className="text-muted fw-normal">(optional)</span>
                    </button>
                    {zoneTransferOpen && (
                      <div className="p-3 rounded-3 border" style={{ background: '#f8fafc' }}>
                        <div className="row g-3">
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Name</label>
                            <input
                              type="text"
                              className="form-control vds-zone-form__input"
                              value={zoneFormData.transferConnection?.keyName ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, transferConnection: { ...(prev.transferConnection ?? {}), keyName: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">DNS Server</label>
                            <input
                              type="text"
                              className="form-control vds-zone-form__input"
                              value={zoneFormData.transferConnection?.primaryServer ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, transferConnection: { ...(prev.transferConnection ?? {}), primaryServer: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Secret</label>
                            <input
                              type="password"
                              className="form-control vds-zone-form__input"
                              placeholder="Current key hidden — enter to change"
                              value={zoneFormData.transferConnection?.key ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, transferConnection: { ...(prev.transferConnection ?? {}), key: e.target.value } } : prev)}
                            />
                          </div>
                          <div className="col-md-6">
                            <label className="vds-zone-form__label">Key Algorithm</label>
                            <select
                              className="form-select vds-zone-form__input"
                              value={zoneFormData.transferConnection?.algorithm ?? ''}
                              onChange={e => setZoneFormData(prev => prev ? { ...prev, transferConnection: { ...(prev.transferConnection ?? {}), algorithm: e.target.value } } : prev)}
                            >
                              <option value="">—</option>
                              {['HMAC-MD5','HMAC-SHA1','HMAC-SHA224','HMAC-SHA256','HMAC-SHA384','HMAC-SHA512'].map(a => (
                                <option key={a} value={a}>{a}</option>
                              ))}
                            </select>
                          </div>
                        </div>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger mt-3"
                          onClick={() => setZoneFormData(prev => prev ? { ...prev, transferConnection: undefined } : prev)}
                        >
                          <i className="bi bi-x-circle me-1" />Clear Transfer Connection
                        </button>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
            )} 

            {zoneInfoOpen && isZoneAdmin && (
            <div className="vds-manage-zone-footer">
              {zoneUpdateMode === 'idle' ? (
                <div className="d-flex justify-content-between align-items-center w-100">
                  <button
                    type="button"
                    className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                    onClick={() => setZoneFormData(zoneData ? { ...zoneData } : null)}
                  >
                    <i className="bi bi-arrow-counterclockwise" />Reset
                  </button>
                  <div className="d-flex align-items-center gap-2">
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1"
                      disabled={updateZoneMutation.isPending}
                      onClick={() => setZoneUpdateMode('confirm')}
                    >
                      <i className="bi bi-check-circle" />Update Zone
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-danger d-flex align-items-center gap-1"
                      onClick={() => setAbandonZoneModal(true)}
                    >
                      <i className="bi bi-box-arrow-left" />Abandon Zone
                    </button>
                  </div>
                </div>
              ) : (
                <div className="d-flex align-items-center justify-content-end gap-3 w-100">
                  <span className="d-flex align-items-center gap-1 fw-semibold" style={{ color: '#d97706', fontSize: '0.9rem' }}>
                    <i className="bi bi-exclamation-triangle-fill" />
                    Are you sure you want to update this zone?
                  </span>
                  <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setZoneUpdateMode('idle')}>
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-success d-flex align-items-center gap-1"
                    disabled={updateZoneMutation.isPending}
                    onClick={() => {
                      if (zoneFormData) {
                        const zone = zonesService.normalizeZoneDates({ ...zoneFormData });
                        updateZoneMutation.mutate(zone);
                      }
                    }}
                  >
                    {updateZoneMutation.isPending
                      ? <><i className="bi bi-hourglass-split vds-spin" />Updating…</>
                      : <><i className="bi bi-check-lg" />Yes, Update</>}
                  </button>
                </div>
              )}
            </div>
            )}
          </div>

          {isZoneAdmin && (
          <div className="vds-recent-changes-panel mt-4">
            <div className="vds-recent-changes-panel__header">
              <i className="bi bi-shield-lock me-2" />Zone Access Rules
              <div className="ms-auto d-flex align-items-center gap-2">
                <button
                  className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                  onClick={() => void queryClient.invalidateQueries({ queryKey: ['zone', id] })}
                >
                  <i className="bi bi-arrow-clockwise" />
                  <span className="vds-btn-flat__label">Refresh</span>
                </button>
                <button
                  className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1"
                  onClick={() => setAclRuleModal({ mode: 'create', rule: { priority: 'User', accessLevel: 'Read', recordTypes: [] } })}
                >
                  <i className="bi bi-plus-circle-fill" />Create ACL Rule
                </button>
                <button
                  className="vds-panel-toggle-btn"
                  onClick={() => setZoneAclOpen(o => !o)}
                  title={zoneAclOpen ? 'Collapse' : 'Expand'}
                  aria-label={zoneAclOpen ? 'Collapse' : 'Expand'}
                >
                  <i className={`bi bi-chevron-${zoneAclOpen ? 'up' : 'down'}`} />
                </button>
              </div>
            </div>
            {zoneAclOpen && (
            <div className="vds-zones-table-wrap">
              {aclAlert && (
                <div className={`alert alert-${aclAlert.type} alert-dismissible d-flex align-items-center gap-2 mb-0`} role="alert" style={{ borderRadius: 0, borderLeft: 'none', borderRight: 'none', borderTop: 'none' }}>
                  <i className={`bi ${aclAlert.type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'}`} />
                  {aclAlert.msg}
                  <button type="button" className="btn-close ms-auto" onClick={() => setAclAlert(null)} />
                </div>
              )}
              <table className="vds-zones-table">
                <thead>
                  <tr>
                    <th>User / Group</th>
                    <th>Access Level</th>
                    <th>Record Types</th>
                    <th>Record Mask</th>
                    <th>Description</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {!(zoneData?.acl?.rules?.length) ? (
                    <tr>
                      <td colSpan={6} className="text-center text-muted py-5">
                        <i className="bi bi-shield-x fs-3 d-block mb-2 opacity-25" />
                        No ACL rules configured. Access is governed by group membership.
                      </td>
                    </tr>
                  ) : zoneData.acl!.rules.map((rule, i) => (
                    <tr key={i}>
                      <td className="vds-table-primary">
                        {rule.groupId
                          ? <Link to={`/groups/${rule.groupId}`} className="vds-table-link d-inline-flex align-items-center gap-1">
                              <i className="bi bi-people" />{rule.displayName ?? rule.groupId}
                            </Link>
                          : <span className="d-inline-flex align-items-center gap-1">
                              <i className="bi bi-person" />{rule.userName ?? rule.userId ?? '—'}
                            </span>}
                      </td>
                      <td>
                        <span className={`vds-access-badge vds-access-badge--${
                          rule.accessLevel === 'NoAccess' ? 'private'
                          : (rule.accessLevel ?? '').toLowerCase()
                        }`}>
                          {rule.accessLevel === 'NoAccess' ? 'No Access' : rule.accessLevel}
                        </span>
                      </td>
                      <td className="vds-table-secondary small">
                        {!rule.recordTypes?.length
                          ? <span className="text-muted fst-italic">All Types</span>
                          : rule.recordTypes.map(t => <span key={t} className="vds-record-type-badge me-1">{t}</span>)}
                      </td>
                      <td className="vds-table-secondary small vds-table-mono">{rule.recordMask ?? '—'}</td>
                      <td className="vds-table-secondary small">{rule.description ?? '—'}</td>
                      <td>
                        <div className="d-flex gap-1">
                          <button
                            className="btn btn-sm vds-btn-flat px-2 py-0 d-flex align-items-center gap-1 vds-history-btn"
                            onClick={() => setAclRuleModal({
                              mode: 'edit',
                              ruleIndex: i,
                              rule: {
                                priority: rule.groupId ? 'Group' : 'User',
                                userName: rule.userName ?? rule.userId,
                                groupId: rule.groupId,
                                accessLevel: rule.accessLevel === 'NoAccess' ? 'NoAccess' : rule.accessLevel,
                                recordTypes: rule.recordTypes ?? [],
                                recordMask: rule.recordMask,
                                description: rule.description,
                              }
                            })}
                          >
                            <i className="bi bi-pencil" />Update
                          </button>
                          <button
                            className="btn btn-sm px-2 py-0 d-flex align-items-center gap-1 vds-acl-delete-btn"
                            onClick={() => setAclDeleteModal({ index: i })}
                          >
                            <i className="bi bi-trash" />Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            )} 
          </div>
          )} 

          {/* ── Zone Sync Schedule Panel (super users only) ── */}
          {isSuper && (
            <div className="vds-recent-changes-panel mt-4">
              <div className="vds-recent-changes-panel__header">
                <i className="bi bi-calendar-check me-2" />Schedule Zone Sync
                <div className="ms-auto">
                  <button
                    className="vds-panel-toggle-btn"
                    onClick={() => setZoneSyncOpen(o => !o)}
                    title={zoneSyncOpen ? 'Collapse' : 'Expand'}
                    aria-label={zoneSyncOpen ? 'Collapse' : 'Expand'}
                  >
                    <i className={`bi bi-chevron-${zoneSyncOpen ? 'up' : 'down'}`} />
                  </button>
                </div>
              </div>
              {zoneSyncOpen && (              <>
              <div className="p-4">
                <div className="mb-4">
                  <label className="vds-zone-form__label d-flex align-items-center gap-2">
                    <i className="bi bi-calendar-week" />Run on Days
                  </label>
                  <div className="d-flex gap-2 flex-wrap mt-2">
                    {[
                      { short: 'MON', label: 'Mon' },
                      { short: 'TUE', label: 'Tue' },
                      { short: 'WED', label: 'Wed' },
                      { short: 'THU', label: 'Thu' },
                      { short: 'FRI', label: 'Fri' },
                      { short: 'SAT', label: 'Sat' },
                      { short: 'SUN', label: 'Sun' },
                    ].map(({ short, label }) => {
                      const active = syncDays.includes(short);
                      return (
                        <button
                          key={short}
                          type="button"
                          className={`vds-acl-type-chip${active ? ' vds-acl-type-chip--active' : ''}`}
                          disabled={syncScheduleRemove}
                          onClick={() => setSyncDays(prev =>
                            prev.includes(short) ? prev.filter(d => d !== short) : [...prev, short]
                          )}
                        >
                          {label}
                        </button>
                      );
                    })}
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary px-2 py-0"
                      style={{ fontSize: '0.75rem', height: 28 }}
                      disabled={syncScheduleRemove}
                      onClick={() => setSyncDays(['MON','TUE','WED','THU','FRI'])}
                    >Weekdays</button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary px-2 py-0"
                      style={{ fontSize: '0.75rem', height: 28 }}
                      disabled={syncScheduleRemove}
                      onClick={() => setSyncDays(['MON','TUE','WED','THU','FRI','SAT','SUN'])}
                    >All Days</button>
                    {syncDays.length > 0 && (
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-danger px-2 py-0"
                      style={{ fontSize: '0.75rem', height: 28 }}
                      disabled={syncScheduleRemove}
                      onClick={() => setSyncDays([])}
                    >Clear All</button>
                    )}
                  </div>
                </div>

                <div className="row g-3 mb-4">
                  <div className="col-sm-4">
                    <label className="vds-zone-form__label d-flex align-items-center gap-2">
                      <i className="bi bi-clock" />Hour (UTC)
                    </label>
                    <select
                      className="form-select vds-zone-form__input"
                      value={syncHour}
                      disabled={syncScheduleRemove}
                      onChange={e => setSyncHour(Number(e.target.value))}
                    >
                      {Array.from({ length: 24 }, (_, i) => (
                        <option key={i} value={i}>{String(i).padStart(2, '0')}:00</option>
                      ))}
                    </select>
                  </div>
                  <div className="col-sm-4">
                    <label className="vds-zone-form__label d-flex align-items-center gap-2">
                      <i className="bi bi-hourglass-split" />Minute
                    </label>
                    <select
                      className="form-select vds-zone-form__input"
                      value={syncMinute}
                      disabled={syncScheduleRemove}
                      onChange={e => setSyncMinute(Number(e.target.value))}
                    >
                      {[0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55].map(m => (
                        <option key={m} value={m}>:{String(m).padStart(2, '0')}</option>
                      ))}
                    </select>
                  </div>
                  <div className="col-sm-4">
                    <label className="vds-zone-form__label d-flex align-items-center gap-2">
                      <i className="bi bi-code" />Generated Expression
                    </label>
                    <div
                      className="form-control vds-zone-form__input d-flex align-items-center justify-content-between"
                      style={{ background: '#f4f7fb', fontFamily: 'monospace', fontSize: '0.82rem', opacity: syncScheduleRemove ? 0.45 : 1 }}
                      title="Quartz cron expression"
                    >
                      <span>
                        {syncScheduleRemove
                          ? '— removed —'
                          : syncDays.length
                            ? `0 ${syncMinute} ${syncHour} ? * ${syncDays.join(',')}`
                            : '— select at least 1 day —'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* ─── UTC Time Converter ─── */}
                <div className="mb-3">
                  <button
                    type="button"
                    className="btn btn-sm vds-btn-flat d-flex align-items-center gap-2 mb-2"
                    onClick={() => setShowUtcConverter(o => !o)}
                  >
                    <i className={`bi bi-chevron-${showUtcConverter ? 'down' : 'right'}`} />
                    <i className="bi bi-globe2" />Convert to UTC time
                  </button>
                  {showUtcConverter && (
                    <div className="p-3 rounded-3 border d-flex align-items-center gap-3 flex-wrap vds-utc-converter-box" style={{ background: '#f8fafc' }}>
                      <div>
                        <label className="vds-zone-form__label">Your local time</label>
                        <input
                          type="time"
                          className="form-control vds-zone-form__input"
                          style={{ width: 150 }}
                          value={localTimeInput}
                          onChange={e => {
                            setLocalTimeInput(e.target.value);
                            if (e.target.value) {
                              const [h, m] = e.target.value.split(':').map(Number);
                              const d = new Date();
                              d.setHours(h, m, 0, 0);
                              setUtcTimeOutput(`${String(d.getUTCHours()).padStart(2,'0')}:${String(d.getUTCMinutes()).padStart(2,'0')} UTC`);
                            } else {
                              setUtcTimeOutput('');
                            }
                          }}
                        />
                        <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.72rem' }}>
                          {Intl.DateTimeFormat().resolvedOptions().timeZone}
                        </p>
                      </div>
                      <div className="d-flex align-items-center" style={{ fontSize: '1.4rem', color: '#8aaad4' }}>
                        <i className="bi bi-arrow-right" />
                      </div>  
                      <div style={{ marginBottom: '1.3rem' }}>
                        <label className="vds-zone-form__label">UTC equivalent</label>
                        <div
                          className="form-control vds-zone-form__input d-flex align-items-center gap-2 vds-utc-output-box"
                          style={{ width: 160, background: '#f0f6ff', fontWeight: 700, color: '#1e3a5f', fontSize: '1.05rem' }}
                        >
                          <i className="bi bi-globe2 text-muted" style={{ fontSize: '0.85rem' }} />
                          {utcTimeOutput || <span className="text-muted fw-normal" style={{ fontSize: '0.85rem' }}>—</span>}
                        </div>
                      </div>
                      {utcTimeOutput && (
                        <div className="align-self-center">
                          <button
                            type="button"
                            className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1"
                            onClick={() => {
                              const [utcH, utcM] = utcTimeOutput.replace(' UTC','').split(':').map(Number);
                              setSyncHour(utcH);
                              setSyncMinute(Math.round(utcM / 5) * 5 % 60);
                            }}
                          >
                            <i className="bi bi-check2-circle" />Use this time
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                <p className="text-muted mb-0" style={{ fontSize: '0.78rem' }}>
                  Schedule for zone sync at the provided time. All times are in <strong>UTC timezone</strong>.
                </p>

                {zoneData?.recurrenceSchedule && (
                  <div className="form-check mt-3">
                    <input
                      type="checkbox"
                      className="form-check-input"
                      id="remove-sync-schedule"
                      checked={syncScheduleRemove}
                      onChange={e => setSyncScheduleRemove(e.target.checked)}
                    />
                    <label className="form-check-label text-danger fw-semibold" htmlFor="remove-sync-schedule">
                      <i className="bi bi-trash me-1" />Remove Zone Sync Schedule
                    </label>
                  </div>
                )}
              </div>
              <div className="vds-manage-zone-footer">
                {syncScheduleMode === 'idle' ? (
                  <div className="d-flex justify-content-between align-items-center w-100">
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
                      onClick={() => {
                        setSyncSchedule(zoneData?.recurrenceSchedule ?? '');
                        setSyncScheduleRemove(false);
                        const cron = zoneData?.recurrenceSchedule ?? '';
                        const parts = cron.split(' ');
                        if (parts.length >= 6) {
                          const h = parseInt(parts[2], 10);
                          const m = parseInt(parts[1], 10);
                          const dp = parts[5];
                          if (!isNaN(h)) setSyncHour(h);
                          if (!isNaN(m)) setSyncMinute(m);
                          if (dp && dp !== '*') setSyncDays(dp.split(',').filter(Boolean));
                        } else {
                          setSyncDays(['MON']);
                          setSyncHour(2);
                          setSyncMinute(0);
                        }
                      }}
                    >
                      <i className="bi bi-arrow-counterclockwise" />Reset
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1"
                      disabled={updateZoneMutation.isPending || (!syncScheduleRemove && syncDays.length === 0)}
                      onClick={() => setSyncScheduleMode('confirm')}
                    >
                      <i className="bi bi-calendar-check" />Update Zone Sync Schedule
                    </button>
                  </div>
                ) : (
                  <div className="d-flex align-items-center justify-content-end gap-3 w-100">
                    <span className="d-flex align-items-center gap-1 fw-semibold" style={{ color: '#d97706', fontSize: '0.9rem' }}>
                      <i className="bi bi-exclamation-triangle-fill" />
                      Update zone sync schedule?
                    </span>
                    <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setSyncScheduleMode('idle')}>
                      Cancel
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-success d-flex align-items-center gap-1"
                      disabled={updateZoneMutation.isPending}
                      onClick={() => {
                        if (zoneData) {
                          const zone = zonesService.normalizeZoneDates({ ...zoneData });
                          if (syncScheduleRemove) {
                            delete zone.recurrenceSchedule;
                          } else {
                            zone.recurrenceSchedule = `0 ${syncMinute} ${syncHour} ? * ${syncDays.join(',')}`;
                          }
                          updateZoneMutation.mutate(zone);
                          setSyncScheduleMode('idle');
                        }
                      }}
                    >
                      {updateZoneMutation.isPending
                        ? <><i className="bi bi-hourglass-split vds-spin" />Updating…</>
                        : <><i className="bi bi-check-lg" />Yes, Update</>}
                    </button>
                  </div>
                )}
              </div>
              </>
              )} 
            </div>
          )}

          {/* ── ACL Rule Create / Edit Modal ── */}
          {aclRuleModal && (
            <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.55)' }}
              onMouseDown={e => { if (e.target === e.currentTarget) setAclRuleModal(null); }}>
              <div className="modal-dialog modal-dialog-centered modal-lg">
                <div className="modal-content">
                  <div className="modal-header" style={{ background: 'linear-gradient(90deg,#1e3a5f 0%,#2a4d7f 100%)', color: '#fff' }}>
                    <h5 className="modal-title fw-semibold d-flex align-items-center gap-2">
                      <i className="bi bi-shield-plus" />
                      {aclRuleModal.mode === 'create' ? 'Create ACL Rule' : 'Update ACL Rule'}
                    </h5>
                    <button type="button" className="btn-close btn-close-white" onClick={() => setAclRuleModal(null)} />
                  </div>
                  <div className="modal-body p-4">
                    <div className="row g-3">
                      {/* Apply Rule to */}
                      <div className="col-12">
                        <label className="vds-zone-form__label">Apply Rule to</label>
                        <div className="d-flex gap-4">
                          {(['User', 'Group'] as const).map(p => (
                            <div key={p} className="form-check">
                              <input
                                type="radio"
                                className="form-check-input"
                                id={`acl-priority-${p}`}
                                name="aclPriority"
                                checked={aclRuleModal.rule.priority === p}
                                onChange={() => setAclRuleModal(prev => prev ? {
                                  ...prev,
                                  rule: { ...prev.rule, priority: p, userName: undefined, groupId: undefined }
                                } : null)}
                              />
                              <label className="form-check-label fw-semibold" htmlFor={`acl-priority-${p}`}>{p}</label>
                            </div>
                          ))}
                        </div>
                        <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.78rem' }}>
                          The more specific a rule is the more precedence it has. User rules will have a higher priority than Group, which will have a higher priority than All.
                        </p>
                      </div>

                      {/* User NTID or Group select */}
                      {aclRuleModal.rule.priority === 'User' ? (
                        <div className="col-md-6">
                          <label className="vds-zone-form__label">User NTID <span className="text-danger">*</span></label>
                          <input
                            type="text"
                            className="form-control vds-zone-form__input"
                            placeholder="Enter username / NTID"
                            value={aclRuleModal.rule.userName ?? ''}
                            onChange={e => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, userName: e.target.value } } : null)}
                          />
                          <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.78rem' }}>NTID of the user this rule applies to.</p>
                        </div>
                      ) : (
                        <div className="col-md-6">
                          <label className="vds-zone-form__label">Group <span className="text-danger">*</span></label>
                          <select
                            className="form-select vds-zone-form__input"
                            value={aclRuleModal.rule.groupId ?? ''}
                            onChange={e => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, groupId: e.target.value } } : null)}
                          >
                            <option value="">— Select a group —</option>
                            {(groupsData ?? []).slice().sort((a, b) => a.name.localeCompare(b.name)).map(g => (
                              <option key={g.id} value={g.id}>{g.name}{g.description ? ` (${g.description})` : ''}</option>
                            ))}
                          </select>
                          <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.78rem' }}>Group this rule applies to.</p>
                        </div>
                      )}

                      {/* Access Level */}
                      <div className="col-md-6">
                        <label className="vds-zone-form__label">Access Level</label>
                        <select
                          className="form-select vds-zone-form__input"
                          value={aclRuleModal.rule.accessLevel}
                          onChange={e => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, accessLevel: e.target.value } } : null)}
                        >
                          {[{ label: 'Read', value: 'Read' }, { label: 'Write', value: 'Write' }, { label: 'Delete', value: 'Delete' }, { label: 'No Access', value: 'NoAccess' }].map(({ label, value }) => (
                            <option key={value} value={value}>{label}</option>
                          ))}
                        </select>
                        <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.78rem' }}>The access level that the selected user or group will be given within this zone.</p>
                      </div>

                      {/* Record Types */}
                      <div className="col-12">
                        <label className="vds-zone-form__label">
                          Record Type(s) <span className="text-muted fw-normal">(empty = all types)</span>
                        </label>
                        <div className="d-flex flex-wrap gap-2 mb-1">
                          {['A','AAAA','CNAME','DS','MX','NS','PTR','SRV','NAPTR','SSHFP','TXT'].map(t => {
                            const checked = (aclRuleModal.rule.recordTypes ?? []).includes(t);
                            return (
                              <label
                                key={t}
                                className={`vds-acl-type-chip${checked ? ' vds-acl-type-chip--active' : ''}`}
                              >
                                <input
                                  type="checkbox"
                                  className="visually-hidden"
                                  checked={checked}
                                  onChange={e => setAclRuleModal(prev => {
                                    if (!prev) return null;
                                    const types = prev.rule.recordTypes ?? [];
                                    return { ...prev, rule: { ...prev.rule, recordTypes: e.target.checked ? [...types, t] : types.filter(x => x !== t) } };
                                  })}
                                />
                                {t}
                              </label>
                            );
                          })}
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-secondary px-2 py-0"
                            style={{ fontSize: '0.75rem', height: 28 }}
                            onClick={() => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, recordTypes: [] } } : null)}
                          >
                            Clear
                          </button>
                        </div>
                        <p className="text-muted mb-0" style={{ fontSize: '0.78rem' }}>
                          This rule will apply only to the selected record types. If no types are selected then the rule will apply to all record types.
                        </p>
                      </div>

                      {/* Record Mask */}
                      <div className="col-md-6">
                        <label className="vds-zone-form__label">Record Mask <span className="text-muted fw-normal">(optional)</span></label>
                        <input
                          type="text"
                          className="form-control vds-zone-form__input"
                          placeholder="e.g. .* or 192.168.0.0/24"
                          value={aclRuleModal.rule.recordMask ?? ''}
                          onChange={e => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, recordMask: e.target.value } } : null)}
                        />
                        <p className="text-muted mb-0 mt-1" style={{ fontSize: '0.78rem' }}>
                          Record masks further refine the types of records this record applies to. For non-PTR records, any valid regex will be accepted. For PTR records, please input a CIDR rule. If no mask is entered, the rule will apply to all.
                        </p>
                      </div>

                      {/* Description */}
                      <div className="col-md-6">
                        <label className="vds-zone-form__label">Description <span className="text-muted fw-normal">(optional)</span></label>
                        <textarea
                          className="form-control vds-zone-form__input"
                          rows={3}
                          value={aclRuleModal.rule.description ?? ''}
                          onChange={e => setAclRuleModal(prev => prev ? { ...prev, rule: { ...prev.rule, description: e.target.value } } : null)}
                        />
                      </div>
                    </div>
                  </div>
                  <div className="modal-footer">
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary me-auto"
                      onClick={() => setAclRuleModal(prev => prev ? { ...prev, rule: { priority: 'User', accessLevel: 'Read', recordTypes: [], userName: undefined, groupId: undefined, recordMask: undefined, description: undefined } } : null)}
                    >
                      <i className="bi bi-arrow-counterclockwise me-1" />Clear Form
                    </button>
                    <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setAclRuleModal(null)}>
                      Close
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm vds-btn-nav d-flex align-items-center gap-1"
                      disabled={
                        saveAclRuleMutation.isPending ||
                        (aclRuleModal.rule.priority === 'User' && !aclRuleModal.rule.userName?.trim()) ||
                        (aclRuleModal.rule.priority === 'Group' && !aclRuleModal.rule.groupId)
                      }
                      onClick={() => { if (zoneData) saveAclRuleMutation.mutate({ zone: zoneData, modal: aclRuleModal! }); }}
                    >
                      {saveAclRuleMutation.isPending
                        ? <><i className="bi bi-hourglass-split vds-spin" />Saving…</>
                        : <><i className="bi bi-check-circle" />Save Rule</>}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ── ACL Delete Confirmation Modal ── */}
          {aclDeleteModal && (
            <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
              onMouseDown={e => { if (e.target === e.currentTarget) setAclDeleteModal(null); }}>
              <div className="modal-dialog modal-dialog-centered">
                <div className="modal-content">
                  <div className="modal-header">
                    <h5 className="modal-title fw-semibold d-flex align-items-center gap-2 text-danger">
                      <i className="bi bi-exclamation-triangle-fill" />Delete ACL Rule
                    </h5>
                    <button type="button" className="btn-close" onClick={() => setAclDeleteModal(null)} />
                  </div>
                  <div className="modal-body">
                    Are you sure you want to delete ACL rule #{aclDeleteModal.index + 1}? This cannot be undone.
                  </div>
                  <div className="modal-footer">
                    <button className="btn btn-outline-secondary" onClick={() => setAclDeleteModal(null)}>No</button>
                    <button
                      className="btn btn-danger d-flex align-items-center gap-1"
                      disabled={deleteAclRuleMutation.isPending}
                      onClick={() => { if (zoneData) deleteAclRuleMutation.mutate({ zone: zoneData, index: aclDeleteModal.index }); }}
                    >
                      {deleteAclRuleMutation.isPending
                        ? <><i className="bi bi-hourglass-split vds-spin" />Deleting…</>
                        : <><i className="bi bi-trash" />Yes, Delete</>}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ── Abandon Zone Modal ── */}
          {abandonZoneModal && (
            <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
              onMouseDown={e => { if (e.target === e.currentTarget) setAbandonZoneModal(false); }}>
              <div className="modal-dialog modal-dialog-centered">
                <div className="modal-content">
                  <div className="modal-header">
                    <h5 className="modal-title fw-semibold d-flex align-items-center gap-2">
                      <i className="bi bi-exclamation-triangle-fill text-danger" />Abandon Zone?
                    </h5>
                    <button type="button" className="btn-close" onClick={() => setAbandonZoneModal(false)} />
                  </div>
                  <div className="modal-body">
                    <p className="mb-0">
                      Are you sure you want to abandon <strong>{zoneData?.name}</strong>?
                      This disconnects the zone from VinylDNS but DNS records will still exist unless
                      deleted from <Link to={`/zones/${id}`} onClick={() => { setAbandonZoneModal(false); setActiveTab('records'); }} className="fw-semibold">Manage Records</Link>.
                    </p>
                  </div>
                  <div className="modal-footer">
                    <button className="btn btn-outline-secondary" onClick={() => setAbandonZoneModal(false)}>Close</button>
                    <button
                      className="btn btn-danger d-flex align-items-center gap-1"
                      disabled={abandonZoneMutation.isPending}
                      onClick={() => abandonZoneMutation.mutate()}
                    >
                      {abandonZoneMutation.isPending
                        ? <><i className="bi bi-hourglass-split vds-spin" />Abandoning…</>
                        : <><i className="bi bi-box-arrow-left" />Abandon</>}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── ACL Rules Modal ── */}
      {aclModal && (
        <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.55)' }}
          onMouseDown={(e) => { if (e.target === e.currentTarget) setAclModal(null); }}>
          <div className="modal-dialog modal-dialog-centered modal-lg" style={{ maxWidth: '820px' }}>
            <div className="modal-content">
              <div className="modal-header" style={{ background: 'linear-gradient(90deg,#1e3a5f 0%,#2a4d7f 100%)', color: '#fff' }}>
                <h5 className="modal-title fw-semibold d-flex align-items-center gap-2">
                  <i className="bi bi-shield-lock" />ACL Rules
                </h5>
                <button type="button" className="btn-close btn-close-white" onClick={() => setAclModal(null)} />
              </div>
              <div className="modal-body p-0">
                <div className="vds-zones-table-wrap">
                  <table className="vds-zones-table">
                    <thead>
                      <tr>
                        <th>User / Group</th>
                        <th>Access Level</th>
                        <th>Record Types</th>
                        <th>Record Mask</th>
                        <th>Description</th>
                      </tr>
                    </thead>
                    <tbody>
                      {aclModal.rules.map((rule, i) => (
                        <tr key={i}>
                          <td className="vds-table-primary">
                            {rule.groupId
                              ? <Link to={`/groups/${rule.groupId}`} className="vds-table-link"><i className="bi bi-people me-1" />{rule.displayName ?? rule.groupId}</Link>
                              : <span><i className="bi bi-person me-1" />{rule.userName ?? rule.userId ?? '—'}</span>}
                          </td>
                          <td>
                            <span className={`vds-access-badge vds-access-badge--${(rule.accessLevel ?? '').toLowerCase().replace(' ', '-')}`}>
                              {rule.accessLevel}
                            </span>
                          </td>
                          <td className="vds-table-secondary small">
                            {!rule.recordTypes?.length ? 'All Types' : rule.recordTypes.map(t => (
                              <span key={t} className="vds-record-type-badge me-1">{t}</span>
                            ))}
                          </td>
                          <td className="vds-table-secondary small vds-table-mono">{rule.recordMask ?? '—'}</td>
                          <td className="vds-table-secondary small">{rule.description ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary btn-sm" onClick={() => setAclModal(null)}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}
      {/* ── Record Set Viewer Modal ── */}
      {viewingRecordSet && (
        <div className="modal d-block" style={{ backgroundColor: 'rgba(0,0,0,0.55)' }}
          onMouseDown={(e) => { if (e.target === e.currentTarget) setViewingRecordSet(null); }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content">
              <div className="modal-header" style={{ background: 'linear-gradient(90deg,#1e3a5f 0%,#2a4d7f 100%)', color: '#fff' }}>
                <h5 className="modal-title fw-semibold d-flex align-items-center gap-2">
                  <i className="bi bi-file-earmark-text" />
                  {viewingRecordSet.label}
                </h5>
                <button type="button" className="btn-close btn-close-white" onClick={() => setViewingRecordSet(null)} />
              </div>
              <div className="modal-body p-0">
                {/* Summary row */}
                <div className="d-flex gap-3 flex-wrap px-4 py-3 vds-modal-summary-row" style={{ background: '#f4f7fb', borderBottom: '1px solid #e3eaf4' }}>
                  <div>
                    <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Name</div>
                    <div className="fw-semibold">{viewingRecordSet.rs.name}</div>
                  </div>
                  <div>
                    <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Type</div>
                    <span className="vds-record-type-badge">{viewingRecordSet.rs.type}</span>
                  </div>
                  <div>
                    <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>TTL</div>
                    <div className="fw-semibold">{viewingRecordSet.rs.ttl}s</div>
                  </div>
                  <div>
                    <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Status</div>
                    <span className={`vds-zone-status-badge ${statusClass(viewingRecordSet.rs.status)}`}>{viewingRecordSet.rs.status}</span>
                  </div>
                  {viewingRecordSet.rs.id && (
                    <div>
                      <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>ID</div>
                      <code style={{ fontSize: '0.78rem' }}>{viewingRecordSet.rs.id}</code>
                    </div>
                  )}
                </div>
                {/* Record data */}
                <div className="px-4 py-3">
                  {(viewingRecordSet.rs.ownerGroupId ||
                    (viewingRecordSet.rs.recordSetGroupChange?.requestedOwnerGroupId &&
                      viewingRecordSet.rs.recordSetGroupChange.requestedOwnerGroupId !== 'null') ||
                    (viewingRecordSet.rs.recordSetGroupChange?.ownershipTransferStatus)) && (
                    <div className="mb-3 pb-3" style={{ borderBottom: '1px solid #e3eaf4' }}>
                      <div className="fw-semibold mb-2" style={{ fontSize: '0.85rem', color: '#3a5c8c' }}>
                        <i className="bi bi-people-fill me-1" />Ownership
                      </div>
                      <div className="d-flex flex-wrap gap-3">
                        {viewingRecordSet.rs.ownerGroupId && (
                          <div>
                            <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Record Owner Group</div>
                            <div className="fw-semibold small">
                              {viewingRecordSet.rs.ownerGroupName ?? viewingRecordSet.rs.ownerGroupId}
                            </div>
                          </div>
                        )}
                        {viewingRecordSet.rs.recordSetGroupChange?.requestedOwnerGroupId &&
                          viewingRecordSet.rs.recordSetGroupChange.requestedOwnerGroupId !== 'null' && (
                          <div>
                            <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Ownership Transfer Group</div>
                            <div className="fw-semibold small">
                              {viewingRecordSet.rs.recordSetGroupChange.requestedOwnerGroupId}
                            </div>
                          </div>
                        )}
                        {viewingRecordSet.rs.recordSetGroupChange?.ownershipTransferStatus && (
                          <div>
                            <div className="text-muted" style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Ownership Transfer Status</div>
                            <div className="fw-semibold small">
                              {viewingRecordSet.rs.recordSetGroupChange.ownershipTransferStatus}
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                  <div className="fw-semibold mb-2" style={{ fontSize: '0.85rem', color: '#3a5c8c' }}>
                    <i className="bi bi-list-ul me-1" />Record Data ({viewingRecordSet.rs.records.length})
                  </div>
                  {viewingRecordSet.rs.records.length === 0 ? (
                    <div className="text-muted small">No records</div>
                  ) : (
                    <div className="vds-zones-table-wrap" style={{ maxHeight: 320, overflowY: 'auto' }}>
                      <table className="vds-zones-table">
                        <thead>
                          <tr>
                            <th>Field</th>
                            <th>Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          {viewingRecordSet.rs.records.map((rec, i) => (
                            <React.Fragment key={i}>
                              {i > 0 && (
                                <tr>
                                  <td colSpan={2} style={{ background: '#f0f4fa', fontSize: '0.72rem', color: '#7a9cc4', fontStyle: 'italic', padding: '3px 12px' }}>
                                    — record {i + 1} —
                                  </td>
                                </tr>
                              )}
                              {Object.entries(rec)
                                .filter(([, v]) => v !== undefined && v !== null && v !== '')
                                .map(([k, v]) => (
                                  <tr key={k}>
                                    <td className="vds-table-secondary" style={{ fontWeight: 600, width: '30%', fontSize: '0.82rem' }}>{k}</td>
                                    <td className="vds-table-primary" style={{ fontFamily: 'monospace', fontSize: '0.82rem', wordBreak: 'break-all' }}>{String(v)}</td>
                                  </tr>
                                ))}
                            </React.Fragment>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-outline-secondary btn-sm" onClick={() => setViewingRecordSet(null)}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
