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
import type { RecordSet, RecordSetListResponse } from '../types/record';

// Stable empty-array reference so consumers that use `records` as an effect/memo
// dependency don't re-run on every render while the query is disabled/loading.
const EMPTY_RECORDS: RecordSet[] = [];

function getErrorMessage(error: {
  response?: {
    data?: string | { errors?: string[] };
    statusText?: string;
    status?: number;
  };
}): string {
  const status = error.response?.status ?? 0;
  const statusText = error.response?.statusText ?? "Unknown";
  const data = error.response?.data;
  let msg = `HTTP ${status} (${statusText}): `;
  if (
    data &&
    typeof data === "object" &&
    "errors" in data &&
    Array.isArray(data.errors)
  ) {
    msg += data.errors.join("\n");
  } else if (typeof data === "string") {
    msg += data.replace(/^"|"$/g, "");
  }
  return msg;
}

/** Hook for global recordset search page */
export function useRecords() {
  const [nameFilter, setNameFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [nameSort, setNameSort] = useState("");
  const [ownerGroupFilter, setOwnerGroupFilter] = useState("");
  const {
    paging,
    setMaxItems,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    currentPage,
    prevPageEnabled,
    getPanelTitle,
  } = usePaging(100);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: [
      "recordsets",
      nameFilter,
      typeFilter,
      nameSort,
      ownerGroupFilter,
      paging.maxItems,
      paging.next,
    ],
    queryFn: async () => {
      const res = await recordsService.listRecordSetData(
        paging.maxItems,
        paging.next as string | undefined,
        nameFilter,
        typeFilter,
        nameSort,
        ownerGroupFilter,
      );
      return res.data;
    },
    enabled: nameFilter.trim().length >= 2,
  });

  // Wrap each mutation's error callback through `getErrorMessage` so user-
  // facing alerts always contain the server's validation messages rather than
  // the raw Axios error string.
  const createRecordMutation = useMutation({
    mutationFn: ({
      zoneId,
      record,
    }: {
      zoneId: string;
      record: Partial<RecordSet>;
    }) => recordsService.createRecordSet(zoneId, record),
    onSuccess: () => {
      addAlert("success", "Record created successfully");
      void queryClient.invalidateQueries({ queryKey: ["recordsets"] });
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  const updateRecordMutation = useMutation({
    mutationFn: ({
      zoneId,
      recordSetId,
      record,
    }: {
      zoneId: string;
      recordSetId: string;
      record: Partial<RecordSet>;
    }) => recordsService.updateRecordSet(zoneId, recordSetId, record),
    onSuccess: () => {
      addAlert("success", "Record updated successfully");
      void queryClient.invalidateQueries({ queryKey: ["recordsets"] });
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  const deleteRecordMutation = useMutation({
    mutationFn: ({
      zoneId,
      recordSetId,
    }: {
      zoneId: string;
      recordSetId: string;
    }) => recordsService.deleteRecordSet(zoneId, recordSetId),
    onSuccess: () => {
      addAlert("success", "Record deleted successfully");
      void queryClient.invalidateQueries({ queryKey: ["recordsets"] });
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  // Resetting pagination on every new search prevents the cursor from
  // a previous query leaking into the new one and returning an off-page result.
  const search = useCallback(
    (filters: {
      name?: string;
      type?: string;
      sort?: string;
      ownerGroup?: string;
    }) => {
      setNameFilter(filters.name ?? "");
      setTypeFilter(filters.type ?? "");
      setNameSort(filters.sort ?? "");
      setOwnerGroupFilter(filters.ownerGroup ?? "");
      resetPaging();
    },
    [resetPaging],
  );

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.recordSets?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  const nextPageEnabled = Boolean(data?.nextId);

  const pageSizes = ([10, 25, 50, 100] as const).filter(
    (s) => s <= paging.maxItems || nextPageEnabled,
  );

  return {
    records: data?.recordSets ?? EMPTY_RECORDS,
    isLoading,
    isFetching,
    nameFilter,
    typeFilter,
    search,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    pageSize: paging.maxItems,
    setPageSize: setMaxItems,
    pageSizes,
    currentPage,
    refetch,
    createRecord: createRecordMutation.mutate,
    updateRecord: updateRecordMutation.mutate,
    deleteRecord: deleteRecordMutation.mutate,
  };
}

/** Hook for records within a single zone */
export function useZoneRecords(zoneId: string) {
  const [nameFilter, setNameFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const {
    paging,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
  } = usePaging(100);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const patchZoneRecordCache = useCallback((updater: (items: RecordSet[]) => RecordSet[]) => {
    queryClient.setQueriesData<RecordSetListResponse>(
      { queryKey: ['zone-recordsets', zoneId] },
      (previous) => {
        if (!previous) return previous;
        return {
          ...previous,
          recordSets: updater(previous.recordSets ?? []),
        };
      }
    );
  }, [queryClient, zoneId]);

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['zone-recordsets', zoneId, nameFilter, typeFilter, paging.next],
    queryFn: async () => {
      const res = await recordsService.listRecordSetsByZone(
        zoneId,
        paging.maxItems,
        paging.next as string | undefined,
        nameFilter,
        typeFilter,
      );
      return res.data;
    },
    enabled: Boolean(zoneId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchOnMount: false,
  });

  const refreshZoneRecords = useCallback(() => {
    void refetch();
  }, [refetch]);

  const createRecordMutation = useMutation({
    mutationFn: (record: Partial<RecordSet>) =>
      recordsService.createRecordSet(zoneId, record),
    onSuccess: (response) => {
      const created = response.data.recordSet;
      patchZoneRecordCache((items) => {
        if (items.some((record) => record.id === created.id)) return items;
        return [created, ...items];
      });
      addAlert('success', 'Record created successfully');
      refreshZoneRecords();
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  const updateRecordMutation = useMutation({
    mutationFn: ({ recordSetId, record }: { recordSetId: string; record: Partial<RecordSet> }) =>
      recordsService.updateRecordSet(zoneId, recordSetId, record),
    onSuccess: (response, variables) => {
      const updated = response.data.recordSet;
      patchZoneRecordCache((items) =>
        items.map((item) => (item.id === variables.recordSetId ? updated : item))
      );
      addAlert('success', 'Record updated successfully');
      refreshZoneRecords();
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  const deleteRecordMutation = useMutation({
    mutationFn: (recordSetId: string) =>
      recordsService.deleteRecordSet(zoneId, recordSetId),
    onSuccess: (_response, deletedRecordSetId) => {
      patchZoneRecordCache((items) => items.filter((item) => item.id !== deletedRecordSetId));
      addAlert('success', 'Record deleted successfully');
      refreshZoneRecords();
    },
    onError: (err: unknown) => {
      addAlert(
        "danger",
        getErrorMessage(err as Parameters<typeof getErrorMessage>[0]),
      );
    },
  });

  const search = useCallback(
    (filters: { name?: string; type?: string }) => {
      setNameFilter(filters.name ?? "");
      setTypeFilter(filters.type ?? "");
      resetPaging();
    },
    [resetPaging],
  );

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.recordSets?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    records: data?.recordSets ?? [],
    isLoading,
    isFetching,
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
    isCreatePending: createRecordMutation.isPending,
    isUpdatePending: updateRecordMutation.isPending,
    isDeletePending: deleteRecordMutation.isPending,
  };
}
