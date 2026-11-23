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

import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useDnsChanges } from "../../hooks/useDnsChanges";

const mockAddAlert = vi.fn();

vi.mock("../../contexts/AlertContext", () => ({
  useAlerts: vi.fn(() => ({
    alerts: [],
    addAlert: mockAddAlert,
    removeAlert: vi.fn(),
    clearAlerts: vi.fn(),
  })),
}));

vi.mock("../../services/dnsChangeService", () => ({
  dnsChangeService: {
    getBatchChanges: vi.fn(),
    createBatchChange: vi.fn(),
    cancelBatchChange: vi.fn(),
    approveBatchChange: vi.fn(),
    rejectBatchChange: vi.fn(),
  },
}));

import { dnsChangeService } from "../../services/dnsChangeService";
const mockService = dnsChangeService as unknown as {
  getBatchChanges: ReturnType<typeof vi.fn>;
  createBatchChange: ReturnType<typeof vi.fn>;
  cancelBatchChange: ReturnType<typeof vi.fn>;
  approveBatchChange: ReturnType<typeof vi.fn>;
  rejectBatchChange: ReturnType<typeof vi.fn>;
};

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockService.getBatchChanges.mockResolvedValue({
    data: { batchChanges: [], nextId: undefined },
  });
});

describe("useDnsChanges", () => {
  describe("initial state", () => {
    it("returns an empty list before data loads", () => {
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });
      expect(result.current.dnsChanges).toEqual([]);
    });

    it("populates the list after a successful fetch", async () => {
      mockService.getBatchChanges.mockResolvedValueOnce({
        data: { batchChanges: [{ id: "b1" }], nextId: 10 },
      });
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.dnsChanges).toHaveLength(1));
      expect(result.current.nextPageEnabled).toBe(true);
    });

    it("disables next page when the API returns no nextId", async () => {
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(result.current.nextPageEnabled).toBe(false);
    });
  });

  describe("createBatchChange", () => {
    it("alerts success and invalidates the list", async () => {
      mockService.createBatchChange.mockResolvedValueOnce({
        data: { id: "b1" },
      });
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.createBatchChange({
          data: { comments: "", changes: [] },
        }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Batch change submitted successfully",
        ),
      );
    });

    it("suppresses the generic alert for a 400 array validation error", async () => {
      mockService.createBatchChange.mockRejectedValueOnce({
        response: { status: 400, data: [{ errors: ["bad"] }] },
      });
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.createBatchChange({
          data: { comments: "", changes: [] },
        }),
      );

      await waitFor(() =>
        expect(mockService.createBatchChange).toHaveBeenCalled(),
      );
      expect(mockAddAlert).not.toHaveBeenCalledWith(
        "danger",
        expect.stringContaining("Error submitting"),
      );
    });

    it("alerts a generic error for a non-validation failure", async () => {
      mockService.createBatchChange.mockRejectedValueOnce({
        response: { status: 500, statusText: "Server Error" },
      });
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.createBatchChange({
          data: { comments: "", changes: [] },
        }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          expect.stringContaining("Error submitting DNS change"),
        ),
      );
    });
  });

  describe("cancelBatchChange", () => {
    it("alerts success on cancel", async () => {
      mockService.cancelBatchChange.mockResolvedValueOnce({});
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.cancelBatchChange("b1"));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Batch change cancelled",
        ),
      );
    });

    it("alerts failure when cancel rejects", async () => {
      mockService.cancelBatchChange.mockRejectedValueOnce(new Error("nope"));
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.cancelBatchChange("b1"));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          "Failed to cancel batch change",
        ),
      );
    });
  });

  describe("approveBatchChange", () => {
    it("alerts success on approve", async () => {
      mockService.approveBatchChange.mockResolvedValueOnce({});
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.approveBatchChange({ id: "b1", comment: "ok" }));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Batch change approved",
        ),
      );
    });

    it("alerts failure when approve rejects", async () => {
      mockService.approveBatchChange.mockRejectedValueOnce(new Error("nope"));
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.approveBatchChange({ id: "b1" }));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          "Failed to approve batch change",
        ),
      );
    });

    it("on a 400 array body, shows the outstanding-issues message and marks failing rows in the cached change", async () => {
      mockService.approveBatchChange.mockRejectedValueOnce({
        response: {
          status: 400,
          statusText: "Bad Request",
          data: [
            { inputName: "a.ok." },
            { inputName: "b.ok.", errors: ["Zone Discovery Failed"] },
          ],
        },
      });
      const queryClient = new QueryClient({
        defaultOptions: {
          queries: { retry: false, staleTime: 0 },
          mutations: { retry: false },
        },
      });
      queryClient.setQueryData(["dnschange", "b1"], {
        id: "b1",
        changes: [
          { id: "c1", inputName: "a.ok." },
          { id: "c2", inputName: "b.ok." },
        ],
      });
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      );
      const { result } = renderHook(() => useDnsChanges(), { wrapper });

      act(() => result.current.approveBatchChange({ id: "b1" }));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          "HTTP 400 (Bad Request): Issues still remain, cannot approve DNS Change. Resolve all outstanding issues or reject the DNS Change.",
        ),
      );
      const cached = queryClient.getQueryData<{
        changes: Array<{
          validationErrors?: string[];
          outstandingErrors?: boolean;
        }>;
      }>(["dnschange", "b1"]);
      expect(cached?.changes[0].validationErrors).toEqual([]);
      expect(cached?.changes[0].outstandingErrors).toBeUndefined();
      expect(cached?.changes[1].validationErrors).toEqual([
        "Zone Discovery Failed",
      ]);
      expect(cached?.changes[1].outstandingErrors).toBe(true);
    });
  });

  describe("rejectBatchChange", () => {
    it("alerts success on reject", async () => {
      mockService.rejectBatchChange.mockResolvedValueOnce({});
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.rejectBatchChange({ id: "b1", comment: "no" }));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Batch change rejected",
        ),
      );
    });

    it("alerts failure when reject rejects", async () => {
      mockService.rejectBatchChange.mockRejectedValueOnce(new Error("nope"));
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.rejectBatchChange({ id: "b1" }));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          "Failed to reject batch change",
        ),
      );
    });
  });

  describe("page sizing", () => {
    it("updates the page size via setPageSize", async () => {
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.setPageSize(25));

      await waitFor(() => expect(result.current.pageSize).toBe(25));
    });

    it("offers smaller sizes and the current size", async () => {
      const { result } = renderHook(() => useDnsChanges(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      expect(result.current.pageSizes).toContain(10);
      expect(result.current.pageSizes).toContain(100);
    });
  });
});
