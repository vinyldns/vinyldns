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

import { useState, useCallback, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zonesService } from "../services/zonesService";
import { usePaging } from "./usePaging";
import { useAlerts } from "../contexts/AlertContext";
import type { Zone, DeletedZoneChange } from "../types/zone";
import type { PagingState } from "../types/common";

export function useZones(
  ignoreAccess = false,
  includeReverse = true,
  initialPaging?: Partial<PagingState>,
) {
  const [query, setQuery] = useState("");
  const [searchByAdminGroup, setSearchByAdminGroup] = useState(false);
  const [accessFilter, setAccessFilter] = useState<number | undefined>(
    undefined,
  );
  const lastSearch = useRef({ query: "", byAdminGroup: false });
  const lastAccessFilter = useRef<number | undefined>(undefined);
  const {
    paging,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
  } = usePaging(100, initialPaging as PagingState | undefined);
  const { addAlert } = useAlerts();
  const queryClient = useQueryClient();

  const { data, isLoading, isFetching } = useQuery({
    queryKey: [
      "zones",
      ignoreAccess,
      includeReverse,
      query,
      searchByAdminGroup,
      accessFilter,
      paging.next,
    ],
    queryFn: async () => {
      const res = await zonesService.getZones(
        paging.maxItems,
        paging.next as string | undefined,
        query,
        searchByAdminGroup,
        ignoreAccess,
        includeReverse,
        accessFilter,
      );
      return res.data;
    },
  });

  const createZoneMutation = useMutation({
    mutationFn: (zone: Zone) => zonesService.createZone(zone),
    onSuccess: () => {
      addAlert("success", "Zone created successfully");
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: {
          data?: string | { errors?: string[] };
          statusText?: string;
          status?: number;
        };
      };
      const msg = getErrorMessage(error);
      addAlert("danger", msg);
    },
  });

  const updateZoneMutation = useMutation({
    mutationFn: ({ id, zone }: { id: string; zone: Zone }) =>
      zonesService.updateZone(id, zone),
    onSuccess: () => {
      addAlert("success", "Zone updated successfully");
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: {
          data?: string | { errors?: string[] };
          statusText?: string;
          status?: number;
        };
      };
      addAlert("danger", getErrorMessage(error));
    },
  });

  const deleteZoneMutation = useMutation({
    mutationFn: (id: string) => zonesService.deleteZone(id),
    onSuccess: () => {
      addAlert("success", "Zone deleted successfully");
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: {
          data?: string | { errors?: string[] };
          statusText?: string;
          status?: number;
        };
      };
      addAlert("danger", getErrorMessage(error));
    },
  });

  const search = useCallback(
    (q: string, byAdminGroup = false) => {
      if (
        lastSearch.current.query === q &&
        lastSearch.current.byAdminGroup === byAdminGroup
      )
        return;
      lastSearch.current = { query: q, byAdminGroup };
      setQuery(q);
      setSearchByAdminGroup(byAdminGroup);
      resetPaging();
    },
    [resetPaging],
  );

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.zones?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    zones: data?.zones ?? [],
    isLoading,
    isFetching,
    query,
    search,
    accessFilter,
    setAccessFilter: (f: number | undefined) => {
      if (lastAccessFilter.current === f) return;
      lastAccessFilter.current = f;
      setAccessFilter(f);
      resetPaging();
    },
    nextPage,
    prevPage,
    nextPageEnabled: Boolean(data?.nextId),
    prevPageEnabled,
    pageNum: paging.pageNum,
    paging,
    getPanelTitle,
    resetPaging,
    createZone: createZoneMutation.mutate,
    updateZone: updateZoneMutation.mutate,
    deleteZone: deleteZoneMutation.mutate,
    isCreating: createZoneMutation.isPending,
    isUpdating: updateZoneMutation.isPending,
    isDeleting: deleteZoneMutation.isPending,
  };
}

export function useDeletedZones(ignoreAccess = false, enabled = true) {
  const [query, setQuery] = useState("");
  const [accessFilter, setAccessFilter] = useState<number | undefined>(
    undefined,
  );
  const {
    paging,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    prevPageEnabled,
    getPanelTitle,
  } = usePaging(100);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ["deleted-zones", ignoreAccess, query, accessFilter, paging.next],
    queryFn: async () => {
      const res = await zonesService.getDeletedZones(
        paging.maxItems,
        paging.next as string | undefined,
        query,
        ignoreAccess,
        accessFilter,
      );
      return res.data;
    },
    enabled: enabled && Boolean(ignoreAccess !== undefined),
  });

  const search = useCallback(
    (q: string) => {
      setQuery(q);
      resetPaging();
    },
    [resetPaging],
  );

  const nextPage = useCallback(() => {
    nextPageUpdate(data?.zonesDeletedInfo?.length ?? 0, data?.nextId);
  }, [data, nextPageUpdate]);

  const prevPage = useCallback(() => {
    prevPageUpdate(getPrevStartFrom());
  }, [prevPageUpdate, getPrevStartFrom]);

  return {
    deletedZones: (data?.zonesDeletedInfo ?? []) as DeletedZoneChange[],
    isLoading,
    isFetching,
    query,
    search,
    nextPage,
    prevPage,
    nextPageEnabled: Boolean(data?.nextId),
    prevPageEnabled,
    pageNum: paging.pageNum,
    getPanelTitle,
    resetPaging,
    setAccessFilter: (f: number | undefined) => {
      setAccessFilter(f);
      resetPaging();
    },
  };
}

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
