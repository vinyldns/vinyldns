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
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ProfileContext — mock with a setter so individual tests can flip between
// regular / super / support users.
let mockProfileValue: {
  profile: {
    id: string;
    userName: string;
    isSuper: boolean;
    isSupport: boolean;
  } | null;
  loading: boolean;
  error: unknown;
  refresh: () => void;
} = {
  profile: {
    id: "u-alice",
    userName: "alice",
    isSuper: false,
    isSupport: false,
  },
  loading: false,
  error: null,
  refresh: () => {},
};
function setProfile(p: typeof mockProfileValue.profile) {
  mockProfileValue = { ...mockProfileValue, profile: p };
}
vi.mock("../../contexts/ProfileContext", () => ({
  useProfile: () => mockProfileValue,
  ProfileProvider: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

vi.mock("../../services/dnsChangeService", () => ({
  dnsChangeService: {
    getBatchChanges: vi.fn().mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    }),
    getBatchChangeCount: vi.fn().mockResolvedValue({
      data: {
        total: 0,
        complete: 0,
        failed: 0,
        partialFailure: 0,
        rejected: 0,
        cancelled: 0,
        pendingReview: 0,
        scheduled: 0,
        pendingProcessing: 0,
      },
    }),
    cancelBatchChange: vi.fn().mockResolvedValue({ data: {} }),
    createBatchChange: vi.fn().mockResolvedValue({ data: {} }),
    approveBatchChange: vi.fn(),
    rejectBatchChange: vi.fn(),
  },
}));

import { DnsChangesPage } from "../../pages/DnsChangesPage";
import { renderWithProviders } from "../utils/renderWithProviders";
import { dnsChangeService } from "../../services/dnsChangeService";
import type { DnsChangeSummary, BatchChangeCount } from "../../types/dnsChange";

/** Build a DnsChangeSummary with sensible defaults. */
function summary(overrides: Partial<DnsChangeSummary> = {}): DnsChangeSummary {
  return {
    id: "abcdef12-3456-7890-abcd-ef1234567890",
    userId: "u-alice",
    userName: "alice",
    createdTimestamp: "2024-01-15T12:00:00Z",
    totalChanges: 2,
    status: "Complete",
    ...overrides,
  };
}

/** Build a BatchChangeCount with given overrides; missing fields default to 0. */
function counts(overrides: Partial<BatchChangeCount> = {}): BatchChangeCount {
  return {
    total: 0,
    complete: 0,
    failed: 0,
    partialFailure: 0,
    rejected: 0,
    cancelled: 0,
    pendingReview: 0,
    scheduled: 0,
    pendingProcessing: 0,
    ...overrides,
  } as BatchChangeCount;
}

describe("<DnsChangesPage />", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    // Restore default empty-list mocks each test.
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    });
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: counts() });
  });

  it("renders the page header", async () => {
    renderWithProviders(<DnsChangesPage />);
    expect(await screen.findByText("DNS Changes")).toBeInTheDocument();
  });

  it("renders the New DNS Change button (opens a modal, not a route)", async () => {
    renderWithProviders(<DnsChangesPage />);
    expect(
      await screen.findByRole("button", { name: /New DNS Change/i }),
    ).toBeInTheDocument();
  });

  it("fires the batch-change list query on mount", async () => {
    renderWithProviders(<DnsChangesPage />);
    await waitFor(() => {
      expect(dnsChangeService.getBatchChanges).toHaveBeenCalled();
    });
  });

  it("fires the batch-change count query on mount", async () => {
    renderWithProviders(<DnsChangesPage />);
    await waitFor(() => {
      expect(dnsChangeService.getBatchChangeCount).toHaveBeenCalled();
    });
  });

  it("shows the empty state when the API returns zero changes", async () => {
    renderWithProviders(<DnsChangesPage />);
    expect(await screen.findByText("No DNS changes found")).toBeInTheDocument();
  });

  it("renders each change row when results are returned", async () => {
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: {
        batchChanges: [
          summary({
            id: "11111111-1111-1111-1111-111111111111",
            status: "Complete",
            comments: "first change",
          }),
          summary({
            id: "22222222-2222-2222-2222-222222222222",
            status: "Failed",
            comments: "second change",
          }),
        ],
        nextId: undefined,
        maxItems: 10,
      },
    });
    renderWithProviders(<DnsChangesPage />);
    // The ID cell links to the full ID but displays its shortened form.
    await screen.findByText("11111111…");
    expect(screen.getByText("22222222…")).toBeInTheDocument();
    // Comments column renders the description text.
    expect(screen.getByText("first change")).toBeInTheDocument();
    expect(screen.getByText("second change")).toBeInTheDocument();
  });
});

describe("<DnsChangesPage /> stat cards", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    });
  });

  it("renders Complete / Pending / Cancelled totals from the count endpoint", async () => {
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: counts({
        total: 12,
        complete: 8,
        failed: 1,
        partialFailure: 1,
        cancelled: 2,
        pendingReview: 1,
        pendingProcessing: 1,
      }),
    });
    renderWithProviders(<DnsChangesPage />);
    // Wait for the count data to load; the loading skeleton shows "\u2026"
    // while pending, so finding the actual 12 confirms hydration is done.
    await waitFor(() => {
      expect(screen.getByText("12")).toBeInTheDocument();
    });
    // Labels for the four insight cards.
    expect(screen.getByText("Complete")).toBeInTheDocument();
    expect(screen.getAllByText("Pending").length).toBeGreaterThan(0);
    expect(screen.getByText("Cancelled")).toBeInTheDocument();
    // Complete value (8) should render somewhere on the page.
    expect(screen.getAllByText("8").length).toBeGreaterThan(0);
  });

  it("falls back to 0s when the count endpoint resolves to empty data", async () => {
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: counts() });
    renderWithProviders(<DnsChangesPage />);
    // Wait until the cards stop showing the loading skeleton ("\u2026")
    // and render the actual 0 totals from the API.
    await waitFor(() => {
      const zeros = screen.queryAllByText("0");
      expect(zeros.length).toBeGreaterThan(0);
    });
  });
});

describe("<DnsChangesPage /> toolbar visibility", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    });
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: counts({ total: 12, complete: 8 }) });
  });

  it("hides insight cards and their toggle for regular users", async () => {
    renderWithProviders(<DnsChangesPage />);

    expect(await screen.findByText("Open Only")).toBeInTheDocument();
    expect(screen.queryByText("Hide Cards")).not.toBeInTheDocument();
    expect(screen.queryByText("Requests")).not.toBeInTheDocument();
  });

  it("keeps filter controls visible and removes the filter toggle", async () => {
    renderWithProviders(<DnsChangesPage />);

    expect(await screen.findByText("Open Only")).toBeInTheDocument();
    expect(screen.getByText("All Time")).toBeInTheDocument();
    expect(screen.queryByText("Hide Filters")).not.toBeInTheDocument();
    expect(screen.queryByText("Show Filters")).not.toBeInTheDocument();
  });
});

describe("<DnsChangesPage /> All Requests tab (super user)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    });
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: counts() });
  });

  it("renders the My/All toggle for reviewers", async () => {
    renderWithProviders(<DnsChangesPage />);
    expect(
      await screen.findByRole("button", { name: /My Requests/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /All Requests/i }),
    ).toBeInTheDocument();
  });

  it("re-queries with ignoreAccess=true after switching to All Requests", async () => {
    renderWithProviders(<DnsChangesPage />);
    // Wait for the initial query so we can count subsequent calls.
    await waitFor(() => {
      expect(
        (dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>).mock
          .calls.length,
      ).toBeGreaterThanOrEqual(1);
    });
    const beforeCount = (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mock.calls.length;
    const allTab = screen.getByRole("button", { name: /All Requests/i });
    await userEvent.click(allTab);
    await waitFor(() => {
      const afterCount = (
        dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
      ).mock.calls.length;
      expect(afterCount).toBeGreaterThan(beforeCount);
    });
    // Some call after the switch should have passed ignoreAccess=true.
    const calls = (dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>)
      .mock.calls;
    const hadIgnoreAccess = calls.some((c: any[]) =>
      JSON.stringify(c).includes("true"),
    );
    expect(hadIgnoreAccess).toBe(true);
  });
});

describe("<DnsChangesPage /> filters", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { batchChanges: [], nextId: undefined, maxItems: 10 },
    });
    (
      dnsChangeService.getBatchChangeCount as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: counts() });
  });

  it("re-queries with PendingReview when the toggle is enabled", async () => {
    renderWithProviders(<DnsChangesPage />);
    const toggle = await screen.findByRole("button", { name: /Open Only/i });
    await userEvent.click(toggle);
    await waitFor(() => {
      const calls = (
        dnsChangeService.getBatchChanges as ReturnType<typeof vi.fn>
      ).mock.calls;
      const hasPR = calls.some((c: any[]) =>
        c.some(
          (arg) => typeof arg === "string" && arg.includes("PendingReview"),
        ),
      );
      expect(hasPR).toBe(true);
    });
  });
});
