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

import { useState, useCallback } from "react";
import type { PagingState } from "../types/common";

export function usePaging(initialMaxItems = 100, initialState?: PagingState) {
  const [paging, setPaging] = useState<PagingState>(
    initialState ?? {
      maxItems: initialMaxItems,
      pageNum: 0,
      startKeys: [],
      next: undefined,
    },
  );

  /** Change the page size and jump back to page 1. */
  const setMaxItems = useCallback((newMax: number) => {
    setPaging({ maxItems: newMax, pageNum: 0, startKeys: [], next: undefined });
  }, []);

  const nextPageUpdate = useCallback(
    (dataLength: number, nextId: string | number | undefined) => {
      setPaging((prev) => {
        if (dataLength === 0) {
          return { ...prev, next: undefined };
        }
        const newStartKeys =
          prev.next != null
            ? [...prev.startKeys, prev.next]
            : [...prev.startKeys];
        return {
          ...prev,
          startKeys: newStartKeys,
          next: nextId,
          pageNum: prev.pageNum + 1,
        };
      });
    },
    [],
  );

  const prevPageUpdate = useCallback((nextId: string | number | undefined) => {
    setPaging((prev) => {
      const newStartKeys = [...prev.startKeys];
      newStartKeys.pop();
      return {
        ...prev,
        startKeys: newStartKeys,
        next: nextId,
        pageNum: prev.pageNum - 1,
      };
    });
  }, []);

  const getPrevStartFrom = useCallback((): string | number | undefined => {
    // The last element of startKeys is the cursor for the current page.
    // Returning it lets prevPageUpdate pop it and fetch the previous page.
    return paging.startKeys[paging.startKeys.length - 1];
  }, [paging]);

  const resetPaging = useCallback(() => {
    setPaging((prev) => ({
      maxItems: prev.maxItems,
      pageNum: 0,
      startKeys: [],
      next: undefined,
    }));
  }, []);

  const getPanelTitle = () =>
    paging.pageNum > 0 ? `[Page ${paging.pageNum + 1}]` : "";

  return {
    paging,
    setMaxItems,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    getPanelTitle,
    currentPage: paging.pageNum + 1,
    nextPageEnabled: Boolean(paging.next),
    prevPageEnabled: paging.pageNum >= 1,
  };
}
