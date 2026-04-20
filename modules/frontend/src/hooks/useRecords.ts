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
import { recordsService } from '../services/recordsService';
import { usePaging } from './usePaging';
import { useAlerts } from '../contexts/AlertContext';
import type { RecordSet } from '../types/record';

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

/** Hook for global recordset search page */
export function useRecords() {
  const [nameFilter, setNameFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [nameSort, setNameSort] = useState('');
  const [ownerGroupFilter, setOwnerGroupFilter] = useState('');
  const { paging, nextPageUpdate, prevPageUpdate, getPrevStartFrom, resetPaging,
    nextPageEnabled, prevPageEnabled, getPanelTitle } = usePaging(100);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['recordsets', nameFilter, typeFilter, nameSort, ownerGroupFilter, paging.next],
    queryFn: async () => {
      const res = await recordsService.listRecordSetData(
        paging.maxItems,
        paging.next as string | undefined,
        nameFilter,
        typeFilter,
        nameSort,
        ownerGroupFilter
      );
      return res.data;
    },
  });

  const createRecordMutation = useMutation({
    mutationFn: ({ zoneId, record }: { zoneId: string; record: Partial<RecordSet> }) =>
      recordsService.createRecordSet(zoneId, record),
    onSuccess: () => {
      addAlert('success', 'Record created successfully');
      void queryClient.invalidateQueries({ queryKey: ['recordsets'] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const updateRecordMutation = useMutation({
    mutationFn: ({ zoneId, recordSetId, record }: { zoneId: string; recordSetId: string; record: Partial<RecordSet> }) =>
      recordsService.updateRecordSet(zoneId, recordSetId, record),
    onSuccess: () => {
      addAlert('success', 'Record updated successfully');
      void queryClient.invalidateQueries({ queryKey: ['recordsets'] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const deleteRecordMutation = useMutation({
    mutationFn: ({ zoneId, recordSetId }: { zoneId: string; recordSetId: string }) =>
      recordsService.deleteRecordSet(zoneId, recordSetId),
    onSuccess: () => {
      addAlert('success', 'Record deleted successfully');
      void queryClient.invalidateQueries({ queryKey: ['recordsets'] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const search = useCallback((filters: {
    name?: string;
    type?: string;
    sort?: string;
    ownerGroup?: string;
  }) => {
    setNameFilter(filters.name ?? '');
    setTypeFilter(filters.type ?? '');
    setNameSort(filters.sort ?? '');
    setOwnerGroupFilter(filters.ownerGroup ?? '');
    resetPaging();
  }, [resetPaging]);

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.recordSets?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    records: data?.recordSets ?? [],
    isLoading,
    nameFilter,
    typeFilter,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    refetch,
    createRecord: createRecordMutation.mutate,
    updateRecord: updateRecordMutation.mutate,
    deleteRecord: deleteRecordMutation.mutate,
  };
}

/** Hook for records within a single zone */
export function useZoneRecords(zoneId: string) {
  const [nameFilter, setNameFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const { paging, nextPageUpdate, prevPageUpdate, getPrevStartFrom, resetPaging,
    nextPageEnabled, prevPageEnabled, getPanelTitle } = usePaging(100);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['zone-recordsets', zoneId, nameFilter, typeFilter, paging.next],
    queryFn: async () => {
      const res = await recordsService.listRecordSetsByZone(
        zoneId,
        paging.maxItems,
        paging.next as string | undefined,
        nameFilter,
        typeFilter
      );
      return res.data;
    },
    enabled: Boolean(zoneId),
  });

  const createRecordMutation = useMutation({
    mutationFn: (record: Partial<RecordSet>) =>
      recordsService.createRecordSet(zoneId, record),
    onSuccess: () => {
      addAlert('success', 'Record created successfully');
      void queryClient.invalidateQueries({ queryKey: ['zone-recordsets', zoneId] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const updateRecordMutation = useMutation({
    mutationFn: ({ recordSetId, record }: { recordSetId: string; record: Partial<RecordSet> }) =>
      recordsService.updateRecordSet(zoneId, recordSetId, record),
    onSuccess: () => {
      addAlert('success', 'Record updated successfully');
      void queryClient.invalidateQueries({ queryKey: ['zone-recordsets', zoneId] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const deleteRecordMutation = useMutation({
    mutationFn: (recordSetId: string) =>
      recordsService.deleteRecordSet(zoneId, recordSetId),
    onSuccess: () => {
      addAlert('success', 'Record deleted successfully');
      void queryClient.invalidateQueries({ queryKey: ['zone-recordsets', zoneId] });
    },
    onError: (err: unknown) => {
      addAlert('danger', getErrorMessage(err as Parameters<typeof getErrorMessage>[0]));
    },
  });

  const search = useCallback((filters: { name?: string; type?: string }) => {
    setNameFilter(filters.name ?? '');
    setTypeFilter(filters.type ?? '');
    resetPaging();
  }, [resetPaging]);

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.recordSets?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    records: data?.recordSets ?? [],
    isLoading,
    search,
    refetch,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    createRecord: createRecordMutation.mutate,
    updateRecord: updateRecordMutation.mutate,
    deleteRecord: deleteRecordMutation.mutate,
  };
}
