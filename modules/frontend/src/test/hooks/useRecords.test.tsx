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
import { useRecords, useZoneRecords } from "../../hooks/useRecords";
import { aRecord } from "../fixtures/testData";

const mockAddAlert = vi.fn();

vi.mock("../../contexts/AlertContext", () => ({
  useAlerts: vi.fn(() => ({
    alerts: [],
    addAlert: mockAddAlert,
    removeAlert: vi.fn(),
    clearAlerts: vi.fn(),
  })),
}));

vi.mock("../../services/recordsService", () => ({
  recordsService: {
    listRecordSetData: vi.fn(),
    listRecordSetsByZone: vi.fn(),
    createRecordSet: vi.fn(),
    updateRecordSet: vi.fn(),
    deleteRecordSet: vi.fn(),
  },
}));

import { recordsService } from "../../services/recordsService";
const mockService = recordsService as unknown as {
  listRecordSetData: ReturnType<typeof vi.fn>;
  listRecordSetsByZone: ReturnType<typeof vi.fn>;
  createRecordSet: ReturnType<typeof vi.fn>;
  updateRecordSet: ReturnType<typeof vi.fn>;
  deleteRecordSet: ReturnType<typeof vi.fn>;
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
  mockService.listRecordSetData.mockResolvedValue({
    data: { recordSets: [], nextId: undefined },
  });
  mockService.listRecordSetsByZone.mockResolvedValue({
    data: { recordSets: [], nextId: undefined },
  });
});

describe("useRecords", () => {
  describe("initial state", () => {
    it("returns an empty list before data loads", () => {
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });
      expect(result.current.records).toEqual([]);
    });

    it("populates records after a successful fetch", async () => {
      mockService.listRecordSetData.mockResolvedValueOnce({
        data: { recordSets: [aRecord], nextId: "n1" },
      });
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });
      act(() => result.current.search({ name: "ho", type: "A" }));
      await waitFor(() => expect(result.current.records).toHaveLength(1));
      expect(result.current.nextPageEnabled).toBe(true);
    });
  });

  describe("search", () => {
    it("stores the new filter values", async () => {
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.search({ name: "host", type: "A" }));

      await waitFor(() => expect(result.current.nameFilter).toBe("host"));
      expect(result.current.typeFilter).toBe("A");
    });

    it("forwards the filters to the service query", async () => {
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.search({ name: "web", type: "CNAME" }));

      await waitFor(() =>
        expect(mockService.listRecordSetData).toHaveBeenCalledWith(
          expect.any(Number),
          undefined,
          "web",
          "CNAME",
          "",
          "",
        ),
      );
    });
  });

  describe("createRecord", () => {
    it("alerts success on create", async () => {
      mockService.createRecordSet.mockResolvedValueOnce({ data: aRecord });
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.createRecord({ zoneId: "zone-id-1", record: aRecord }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record created successfully",
        ),
      );
    });

    it("alerts a parsed server error on failure", async () => {
      mockService.createRecordSet.mockRejectedValueOnce({
        response: {
          status: 422,
          statusText: "Unprocessable",
          data: { errors: ["bad name"] },
        },
      });
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.createRecord({ zoneId: "zone-id-1", record: aRecord }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          expect.stringContaining("bad name"),
        ),
      );
    });
  });

  describe("updateRecord", () => {
    it("alerts success on update", async () => {
      mockService.updateRecordSet.mockResolvedValueOnce({ data: aRecord });
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.updateRecord({
          zoneId: "zone-id-1",
          recordSetId: "recordset-id-1",
          record: aRecord,
        }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record updated successfully",
        ),
      );
    });
  });

  describe("deleteRecord", () => {
    it("alerts success on delete", async () => {
      mockService.deleteRecordSet.mockResolvedValueOnce({});
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.deleteRecord({
          zoneId: "zone-id-1",
          recordSetId: "recordset-id-1",
        }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record deleted successfully",
        ),
      );
    });
  });

  describe("page sizing", () => {
    it("updates the page size via setPageSize", async () => {
      const { result } = renderHook(() => useRecords(), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.setPageSize(50));

      await waitFor(() => expect(result.current.pageSize).toBe(50));
    });
  });
});

describe("useZoneRecords", () => {
  describe("initial state", () => {
    it("returns an empty list before data loads", () => {
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });
      expect(result.current.records).toEqual([]);
    });

    it("populates records after a successful fetch", async () => {
      mockService.listRecordSetsByZone.mockResolvedValueOnce({
        data: { recordSets: [aRecord], nextId: "n1" },
      });
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.records).toHaveLength(1));
    });

    it("does not query when zoneId is empty", () => {
      renderHook(() => useZoneRecords(""), { wrapper: makeWrapper() });
      expect(mockService.listRecordSetsByZone).not.toHaveBeenCalled();
    });
  });

  describe("search", () => {
    it("forwards the name and type filters to the service query", async () => {
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.search({ name: "host", type: "A" }));

      await waitFor(() =>
        expect(mockService.listRecordSetsByZone).toHaveBeenCalledWith(
          "zone-id-1",
          expect.any(Number),
          undefined,
          "host",
          "A",
        ),
      );
    });

    it("defaults filters to empty strings when omitted", async () => {
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      act(() => result.current.search({}));

      await waitFor(() =>
        expect(mockService.listRecordSetsByZone).toHaveBeenLastCalledWith(
          "zone-id-1",
          expect.any(Number),
          undefined,
          "",
          "",
        ),
      );
    });
  });

  describe("createRecord", () => {
    it("alerts success on create", async () => {
      mockService.createRecordSet.mockResolvedValueOnce({ data: aRecord });
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.createRecord(aRecord));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record created successfully",
        ),
      );
    });

    it("alerts a parsed server error on failure", async () => {
      mockService.createRecordSet.mockRejectedValueOnce({
        response: {
          status: 422,
          statusText: "Unprocessable",
          data: { errors: ["bad name"] },
        },
      });
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.createRecord(aRecord));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "danger",
          expect.stringContaining("bad name"),
        ),
      );
    });
  });

  describe("updateRecord", () => {
    it("alerts success on update", async () => {
      mockService.updateRecordSet.mockResolvedValueOnce({ data: aRecord });
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });

      act(() =>
        result.current.updateRecord({
          recordSetId: "recordset-id-1",
          record: aRecord,
        }),
      );

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record updated successfully",
        ),
      );
    });
  });

  describe("deleteRecord", () => {
    it("alerts success on delete", async () => {
      mockService.deleteRecordSet.mockResolvedValueOnce({});
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });

      act(() => result.current.deleteRecord("recordset-id-1"));

      await waitFor(() =>
        expect(mockAddAlert).toHaveBeenCalledWith(
          "success",
          "Record deleted successfully",
        ),
      );
    });
  });

  describe("pagination", () => {
    it("enables the next page when the service returns a nextId", async () => {
      mockService.listRecordSetsByZone.mockResolvedValueOnce({
        data: { recordSets: [aRecord], nextId: "n1" },
      });
      const { result } = renderHook(() => useZoneRecords("zone-id-1"), {
        wrapper: makeWrapper(),
      });
      await waitFor(() => expect(result.current.records).toHaveLength(1));

      act(() => result.current.nextPage());

      await waitFor(() => expect(result.current.prevPageEnabled).toBe(true));
    });
  });
});
