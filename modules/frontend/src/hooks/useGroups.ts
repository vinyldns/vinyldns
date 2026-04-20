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

import { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupsService } from '../services/groupsService';
import { usePaging } from './usePaging';
import { useAlerts } from '../contexts/AlertContext';
import type { Group } from '../types/group';

function getErrorMessage(error: { response?: { data?: string | { errors?: string[] }; statusText?: string; status?: number } }): string {
  const status = error.response?.status ?? 0;
  const statusText = error.response?.statusText ?? 'Unknown';
  const data = error.response?.data;
  let msg = `HTTP ${status} (${statusText}): `;
  if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
    msg += data.errors.join('\n');
  } else if (typeof data === 'string') {
    msg += data.replace(/^"|"$/g, '');
  }
  return msg;
}

export function useGroups(ignoreAccess = false) {
  const [query, setQuery] = useState('');
  const { paging, nextPageUpdate, prevPageUpdate, getPrevStartFrom, resetPaging,
    nextPageEnabled, prevPageEnabled, getPanelTitle } = usePaging(100);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['groups', ignoreAccess, query, paging.next],
    staleTime: 0,
    queryFn: async () => {
      const res = await groupsService.getGroupsAbridged(
        paging.maxItems,
        paging.next as string | undefined,
        ignoreAccess,
        query
      );
      return res.data;
    },
  });

  const createGroupMutation = useMutation({
    mutationFn: (group: Partial<Group>) => groupsService.createGroup(group),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['groups'] });
      addAlert('success', 'Group created successfully');
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const updateGroupMutation = useMutation({
    mutationFn: ({ id, group }: { id: string; group: Partial<Group> }) =>
      groupsService.updateGroup(id, group),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['groups'] });
      addAlert('success', 'Group updated successfully');
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const deleteGroupMutation = useMutation({
    mutationFn: (id: string) => groupsService.deleteGroup(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['groups'] });
      addAlert('success', 'Group deleted successfully');
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const search = useCallback(
    (q: string) => {
      setQuery(q);
      resetPaging();
    },
    [resetPaging]
  );

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.groups?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    groups: data?.groups ?? [],
    isLoading,
    query,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    refetch,
    createGroup: createGroupMutation.mutate,
    updateGroup: updateGroupMutation.mutate,
    deleteGroup: deleteGroupMutation.mutate,
    isCreating: createGroupMutation.isPending,
    isUpdating: updateGroupMutation.isPending,
    isDeleting: deleteGroupMutation.isPending,
  };
}
