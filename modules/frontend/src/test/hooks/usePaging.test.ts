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

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePaging } from '../../hooks/usePaging';

describe('usePaging', () => {
  it('initialises with page 0', () => {
    const { result } = renderHook(() => usePaging(100));
    expect(result.current.paging.pageNum).toBe(0);
    expect(result.current.prevPageEnabled).toBe(false);
    expect(result.current.nextPageEnabled).toBe(false);
  });

  it('nextPageUpdate increments page when data exists', () => {
    const { result } = renderHook(() => usePaging(100));
    act(() => {
      result.current.nextPageUpdate(5, 'next-token');
    });
    expect(result.current.paging.pageNum).toBe(1);
    expect(result.current.nextPageEnabled).toBe(true);
    expect(result.current.prevPageEnabled).toBe(true);
  });

  it('resetPaging resets to initial state', () => {
    const { result } = renderHook(() => usePaging(100));
    act(() => result.current.nextPageUpdate(5, 'next'));
    act(() => result.current.resetPaging());
    expect(result.current.paging.pageNum).toBe(0);
    expect(result.current.paging.next).toBeUndefined();
  });
});
