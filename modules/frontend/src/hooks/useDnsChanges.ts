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

import { useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dnsChangeService } from '../services/dnsChangeService';
import { usePaging } from './usePaging';
import { useAlerts } from '../contexts/AlertContext';
import type { CreateDnsChangeRequest } from '../types/dnsChange';

export function useDnsChanges(ignoreAccess = false) {
  const { paging, nextPageUpdate, prevPageUpdate, getPrevStartFrom,
    nextPageEnabled, prevPageEnabled, getPanelTitle } = usePaging(25);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['dnschanges', ignoreAccess, paging.next],
    queryFn: async () => {
      const res = await dnsChangeService.getBatchChanges(
        paging.maxItems,
        paging.next as number | undefined,
        ignoreAccess
      );
      return res.data;
    },
  });

  const createMutation = useMutation({
    mutationFn: ({ data, allowManualReview }: { data: CreateDnsChangeRequest; allowManualReview?: boolean }) =>
      dnsChangeService.createBatchChange(data, allowManualReview),
    onSuccess: () => {
      addAlert('success', 'Batch change submitted successfully');
      void queryClient.invalidateQueries({ queryKey: ['dnschanges'] });
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: unknown; status?: number; statusText?: string } };
      const status = error.response?.status;
      const responseData = error.response?.data;
      // 400 + array = per-row errors; the caller (DnsChangeNewPage) shows inline row errors + its own alert
      if (status === 400 && Array.isArray(responseData)) return;
      addAlert('danger', `Error submitting DNS change: HTTP ${status ?? 0} ${error.response?.statusText ?? ''}`);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (id: string) => dnsChangeService.cancelBatchChange(id),
    onSuccess: () => {
      addAlert('success', 'Batch change cancelled');
      void queryClient.invalidateQueries({ queryKey: ['dnschanges'] });
    },
    onError: () => {
      addAlert('danger', 'Failed to cancel batch change');
    },
  });

  const approveMutation = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      dnsChangeService.approveBatchChange(id, comment),
    onSuccess: () => {
      addAlert('success', 'Batch change approved');
      void queryClient.invalidateQueries({ queryKey: ['dnschanges'] });
    },
    onError: () => {
      addAlert('danger', 'Failed to approve batch change');
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      dnsChangeService.rejectBatchChange(id, comment),
    onSuccess: () => {
      addAlert('success', 'Batch change rejected');
      void queryClient.invalidateQueries({ queryKey: ['dnschanges'] });
    },
    onError: () => {
      addAlert('danger', 'Failed to reject batch change');
    },
  });

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.batchChanges?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    dnsChanges: data?.batchChanges ?? [],
    isLoading,
    refetch,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    createBatchChange: createMutation.mutate,
    cancelBatchChange: cancelMutation.mutate,
    approveBatchChange: approveMutation.mutate,
    rejectBatchChange: rejectMutation.mutate,
    isSubmitting: createMutation.isPending,
  };
}
