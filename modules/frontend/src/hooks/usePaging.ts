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
import type { PagingState } from '../types/common';

/** Mirrors the Angular pagingService behaviour */
export function usePaging(maxItems = 100) {
  const [paging, setPaging] = useState<PagingState>({
    maxItems,
    pageNum: 0,
    startKeys: [],
    next: undefined,
  });

  const nextPageUpdate = useCallback(
    (dataLength: number, nextId: string | number | undefined) => {
      setPaging((prev) => {
        if (dataLength === 0) {
          return { ...prev, next: undefined };
        }
        const newStartKeys = prev.next != null
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
    []
  );

  const prevPageUpdate = useCallback(
    (nextId: string | number | undefined) => {
      setPaging((prev) => {
        const newStartKeys = [...prev.startKeys];
        newStartKeys.pop();
        return { ...prev, startKeys: newStartKeys, next: nextId, pageNum: prev.pageNum - 1 };
      });
    },
    []
  );

  const getPrevStartFrom = useCallback((): string | number | undefined => {
    if (paging.pageNum > 1) {
      return paging.startKeys[paging.startKeys.length - 2];
    }
    return undefined;
  }, [paging]);

  const resetPaging = useCallback(() => {
    setPaging({ maxItems, pageNum: 0, startKeys: [], next: undefined });
  }, [maxItems]);

  const getPanelTitle = () =>
    paging.pageNum > 0 ? `[Page ${paging.pageNum + 1}]` : '';

  return {
    paging,
    nextPageUpdate,
    prevPageUpdate,
    getPrevStartFrom,
    resetPaging,
    getPanelTitle,
    nextPageEnabled: Boolean(paging.next),
    prevPageEnabled: paging.pageNum >= 1,
  };
}
