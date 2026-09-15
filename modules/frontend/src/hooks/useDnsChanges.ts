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

import { useCallback } from "react";
import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
} from "@tanstack/react-query";
import { dnsChangeService } from "../services/dnsChangeService";
import { usePaging } from "./usePaging";
import { useAlerts } from "../contexts/AlertContext";
import type { CreateDnsChangeRequest } from "../types/dnsChange";

/**
 * Manages data fetching and all mutations for the DNS Changes feature.
 *
 * The hook is parameterized rather than split into separate hooks so the same
 * logic serves both the scoped "My Requests" view and the admin "All Requests"
 * view. `ignoreAccess` toggles the API's access control bypass, and the
 * optional filters (userName, approvalStatus, dates) are only forwarded when
 * `ignoreAccess` is true — the caller is responsible for this gating.
 *
 * All filter values are included in the React Query key so changing any filter
 * triggers an automatic refetch without manual cache invalidation.
 */
export function useDnsChanges(
  ignoreAccess = false,
  userName?: string,
  approvalStatus?: string,
  dateTimeRangeStart?: string,
  dateTimeRangeEnd?: string,
  initialPaging?: import("../types/common").PagingState,
) {
  const {
    paging,
    setMaxItems,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    currentPage,
    prevPageEnabled,
    getPanelTitle,
  } = usePaging(100, initialPaging);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: [
      "dnschanges",
      ignoreAccess,
      paging.maxItems,
      paging.next,
      userName,
      approvalStatus,
      dateTimeRangeStart,
      dateTimeRangeEnd,
    ],
    queryFn: async () => {
      const res = await dnsChangeService.getBatchChanges(
        paging.maxItems,
        paging.next as number | undefined,
        ignoreAccess,
        approvalStatus,
        userName,
        dateTimeRangeStart,
        dateTimeRangeEnd,
      );
      return res.data;
    },
    refetchOnWindowFocus: false, // don't refetch when switching tabs
    staleTime: 30_000, // don't re-fetch within 30 s of a successful load
    placeholderData: keepPreviousData, // show stale rows while new page loads (no empty flash)
  });

  const createMutation = useMutation({
    mutationFn: ({
      data,
      allowManualReview,
    }: {
      data: CreateDnsChangeRequest;
      allowManualReview?: boolean;
    }) => dnsChangeService.createBatchChange(data, allowManualReview),
    onSuccess: () => {
      addAlert("success", "Batch change submitted successfully");
      void queryClient.invalidateQueries({ queryKey: ["dnschanges"] });
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: { data?: unknown; status?: number; statusText?: string };
      };
      const status = error.response?.status;
      const responseData = error.response?.data;
      // A 400 with an array body means the server returned per-row validation
      // errors. DnsChangeNewPage handles these inline so the generic
      // alert here to avoid double-reporting the same failure.
      if (status === 400 && Array.isArray(responseData)) return;
      addAlert(
        "danger",
        `Error submitting DNS change: HTTP ${status ?? 0} ${error.response?.statusText ?? ""}`,
      );
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (id: string) => dnsChangeService.cancelBatchChange(id),
    onSuccess: () => {
      addAlert("success", "Batch change cancelled");
      void queryClient.invalidateQueries({ queryKey: ["dnschanges"] });
    },
    onError: () => {
      addAlert("danger", "Failed to cancel batch change");
    },
  });

  // Approve/reject mutations are admin-only actions exposed in the detail page.
  // They are intentionally kept in this shared hook so the query key invalidation
  // updates the list page cache even if the user navigates back before the
  // mutation resolves.
  const approveMutation = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      dnsChangeService.approveBatchChange(id, comment),
    onSuccess: () => {
      addAlert("success", "Batch change approved");
      void queryClient.invalidateQueries({ queryKey: ["dnschanges"] });
    },
    onError: () => {
      addAlert("danger", "Failed to approve batch change");
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      dnsChangeService.rejectBatchChange(id, comment),
    onSuccess: () => {
      addAlert("success", "Batch change rejected");
      void queryClient.invalidateQueries({ queryKey: ["dnschanges"] });
    },
    onError: () => {
      addAlert("danger", "Failed to reject batch change");
    },
  });

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.batchChanges?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  // nextPageEnabled is derived from the API response, not paging.next.
  // paging.next is the fetch cursor (undefined on page 1), so Boolean(paging.next)
  // would always be false on the first load even when the API returned a nextId.
  const nextPageEnabled = Boolean(data?.nextId);

  // Only surface page-size options that make sense given the current data:
  // always allow reducing the size; only offer larger sizes when more pages exist.
  const pageSizes = ([10, 25, 50, 100] as const).filter(
    (s) => s <= paging.maxItems || nextPageEnabled,
  );

  return {
    dnsChanges: data?.batchChanges ?? [],
    isLoading,
    isFetching,
    refetch,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    pageSize: paging.maxItems,
    setPageSize: setMaxItems,
    pageSizes,
    currentPage,
    paging,
    createBatchChange: createMutation.mutate,
    cancelBatchChange: cancelMutation.mutate,
    approveBatchChange: approveMutation.mutate,
    rejectBatchChange: rejectMutation.mutate,
    isSubmitting: createMutation.isPending,
  };
}
