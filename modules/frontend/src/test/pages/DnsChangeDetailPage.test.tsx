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
import { Routes, Route } from "react-router-dom";
import type { DnsChange } from "../../types/dnsChange";

// Profile context — DetailPage uses it for ownership/review gating. Each test
// overrides the returned profile through `setProfile()` below.
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

// Mock useDnsChanges — DetailPage only uses the mutation handles, not data.
const approveBatchChange = vi.fn();
const rejectBatchChange = vi.fn();
const cancelBatchChange = vi.fn();
vi.mock("../../hooks/useDnsChanges", () => ({
  useDnsChanges: () => ({
    approveBatchChange,
    rejectBatchChange,
    cancelBatchChange,
  }),
}));

vi.mock("../../services/dnsChangeService", () => ({
  dnsChangeService: {
    getBatchChange: vi.fn(),
    exportToCsv: vi.fn(),
  },
}));

import { DnsChangeDetailPage } from "../../pages/DnsChangeDetailPage";
import { renderWithProviders } from "../utils/renderWithProviders";
import { dnsChangeService } from "../../services/dnsChangeService";

const BATCH_ID = "abcdef12-3456-7890-abcd-ef1234567890";

function makeBatch(overrides: Partial<DnsChange> = {}): DnsChange {
  return {
    id: BATCH_ID,
    userId: "u-alice",
    userName: "alice",
    comments: "fix some records",
    createdTimestamp: "2024-01-15T12:00:00Z",
    status: "Complete",
    approvalStatus: "AutoApproved",
    changes: [
      {
        id: "sc-1",
        changeType: "Add",
        inputName: "host.example.",
        type: "A",
        ttl: 300,
        status: "Complete",
        record: { address: "1.2.3.4" },
      },
    ],
    ...overrides,
  };
}

/**
 * Render the DetailPage under a Routes tree so `useParams()` resolves the
 * dynamic :id segment exactly like the real app does.
 */
function renderAt(id: string = BATCH_ID) {
  return renderWithProviders(
    <Routes>
      <Route path="/dnschanges/:id" element={<DnsChangeDetailPage />} />
    </Routes>,
    { routerEntries: [`/dnschanges/${id}`] },
  );
}

describe("<DnsChangeDetailPage />", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
  });

  it("renders the loading spinner before data arrives", async () => {
    // Pending promise — never resolves during this test.
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockReturnValue(new Promise(() => {}));
    const { container } = renderAt();
    await waitFor(() => {
      expect(container.querySelector(".vds-loader-ring")).not.toBeNull();
    });
  });

  it("shows the page header, status badge and the batch ID after load", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch(),
    });
    renderAt();
    expect(await screen.findByText("DNS Change")).toBeInTheDocument();
    // Status string appears multiple times (batch badge + per-row badge); just
    // assert that at least one occurrence is rendered.
    expect(screen.getAllByText("Complete").length).toBeGreaterThan(0);
    expect(screen.getAllByText(BATCH_ID).length).toBeGreaterThan(0);
  });

  it("renders the submitter and comments", async () => {
    // Different userId/userName so the page treats current user as non-owner
    // (the Submitter tile is hidden when the viewer IS the owner).
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-bob",
        userName: "bob",
        comments: "review please",
      }),
    });
    renderAt();
    expect(await screen.findByText("bob")).toBeInTheDocument();
    expect(screen.getByText("review please")).toBeInTheDocument();
  });

  it("shows the empty state when the API resolves to a null change", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: null,
    });
    renderAt();
    expect(
      await screen.findByText("Batch change not found"),
    ).toBeInTheDocument();
  });

  it("humanises Partial Failure status from the API", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({ status: "PartialFailure" }),
    });
    renderAt();
    expect(await screen.findByText("Partial Failure")).toBeInTheDocument();
  });
});

describe("<DnsChangeDetailPage /> PendingReview notice", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
  });

  it("shows the manual-review banner for a PendingReview batch", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    expect(
      await screen.findByText("Your DNS Change requires further review."),
    ).toBeInTheDocument();
    // The banner should also contain the docs link with the canonical URL.
    const link = screen.getByRole("link", {
      name: /See the docs for more information\./i,
    });
    expect(link).toHaveAttribute(
      "href",
      "https://www.vinyldns.io/portal/manual-review-scheduling",
    );
  });

  it("hides the manual-review banner for AutoApproved batches", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: makeBatch() });
    renderAt();
    await screen.findByText("DNS Change");
    expect(
      screen.queryByText("Your DNS Change requires further review."),
    ).not.toBeInTheDocument();
  });

  it("renders the Review Status tile for ManuallyApproved batches", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        status: "Complete",
        approvalStatus: "ManuallyApproved",
      }),
    });
    renderAt();
    expect(await screen.findByText("Review Status")).toBeInTheDocument();
    // "Approved" is the human-readable form of ManuallyApproved.
    expect(screen.getByText("Approved")).toBeInTheDocument();
  });

  it("renders the Review Status tile for ManuallyRejected batches", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        status: "Rejected",
        approvalStatus: "ManuallyRejected",
      }),
    });
    renderAt();
    expect(await screen.findByText("Review Status")).toBeInTheDocument();
    // "Rejected" (the label) appears in both the batch badge and the review
    // tile, so just assert at least one occurrence.
    expect(screen.getAllByText("Rejected").length).toBeGreaterThan(0);
  });
});

describe("<DnsChangeDetailPage /> Cancel action", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the Cancel Changes button only for the owner on PendingReview", async () => {
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    expect(
      await screen.findByRole("button", { name: /Cancel Changes/i }),
    ).toBeInTheDocument();
  });

  it("hides the Cancel Changes button when the viewer is not the owner", async () => {
    setProfile({
      id: "u-other",
      userName: "other",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        userName: "alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    await screen.findByText("DNS Change");
    expect(
      screen.queryByRole("button", { name: /Cancel Changes/i }),
    ).not.toBeInTheDocument();
  });

  it("hides the Cancel Changes button when the batch is not PendingReview", async () => {
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({ userId: "u-alice", status: "Complete" }),
    });
    renderAt();
    await screen.findByText("DNS Change");
    expect(
      screen.queryByRole("button", { name: /Cancel Changes/i }),
    ).not.toBeInTheDocument();
  });

  it("calls cancelBatchChange when the user confirms cancellation", async () => {
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    const cancelBtn = await screen.findByRole("button", {
      name: /Cancel Changes/i,
    });
    await userEvent.click(cancelBtn);
    // Modal opens with a primary "Cancel DNS Change" button.
    const confirm = await screen.findByRole("button", {
      name: /Cancel DNS Change/i,
    });
    await userEvent.click(confirm);
    expect(cancelBatchChange).toHaveBeenCalledTimes(1);
    expect(cancelBatchChange).toHaveBeenCalledWith(
      BATCH_ID,
      expect.any(Object),
    );
  });
});

describe("<DnsChangeDetailPage /> Review actions (super/support only)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("hides Approve/Reject for a non-reviewer on PendingReview", async () => {
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    await screen.findByText("DNS Change");
    expect(screen.queryByText("Review DNS Change")).not.toBeInTheDocument();
  });

  it("shows the review panel for super users on PendingReview", async () => {
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    expect(await screen.findByText("Review DNS Change")).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Add comments/i)).toBeInTheDocument();
  });

  it("requires two clicks before approveBatchChange is invoked", async () => {
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    await screen.findByText("Review DNS Change");
    const approveBtn = screen.getByRole("button", { name: /^Approve$/i });
    await userEvent.click(approveBtn);
    // After the first click the mutation must NOT have fired yet.
    expect(approveBatchChange).not.toHaveBeenCalled();
    // A confirm button should now be visible.
    const confirm = await screen.findByRole("button", {
      name: /Confirm Approval/i,
    });
    await userEvent.click(confirm);
    expect(approveBatchChange).toHaveBeenCalledTimes(1);
    expect(approveBatchChange).toHaveBeenCalledWith(
      expect.objectContaining({ id: BATCH_ID }),
      expect.any(Object),
    );
  });

  it("requires two clicks before rejectBatchChange is invoked", async () => {
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    await screen.findByText("Review DNS Change");
    const rejectBtn = screen.getByRole("button", { name: /^Reject$/i });
    await userEvent.click(rejectBtn);
    expect(rejectBatchChange).not.toHaveBeenCalled();
    
    // Rejection requires a comment, so enter one
    const commentInput = screen.getByPlaceholderText("Add comments");
    await userEvent.type(commentInput, "Invalid change");
    
    const confirm = await screen.findByRole("button", {
      name: /Confirm Rejection/i,
    });
    await userEvent.click(confirm);
    expect(rejectBatchChange).toHaveBeenCalledTimes(1);
  });

  it("passes the operator's comment through to approveBatchChange", async () => {
    setProfile({
      id: "u-admin",
      userName: "admin",
      isSuper: true,
      isSupport: false,
    });
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        userId: "u-alice",
        status: "PendingReview",
        approvalStatus: "PendingReview",
      }),
    });
    renderAt();
    await screen.findByText("Review DNS Change");
    const textarea = screen.getByPlaceholderText(/Add comments/i);
    await userEvent.type(textarea, "looks good");
    const approveBtn = screen.getByRole("button", { name: /^Approve$/i });
    await userEvent.click(approveBtn);
    const confirm = await screen.findByRole("button", {
      name: /Confirm Approval/i,
    });
    await userEvent.click(confirm);
    expect(approveBatchChange).toHaveBeenCalledWith(
      expect.objectContaining({ id: BATCH_ID, comment: "looks good" }),
      expect.any(Object),
    );
  });
});

describe("<DnsChangeDetailPage /> CSV export", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
  });

  it("calls exportToCsv when the user clicks Export CSV", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: makeBatch() });
    renderAt();
    const btn = await screen.findByRole("button", { name: /Export CSV/i });
    await userEvent.click(btn);
    expect(dnsChangeService.exportToCsv).toHaveBeenCalledTimes(1);
    // Default — no active filter — passes `rows: undefined` so all rows export.
    const args = (dnsChangeService.exportToCsv as ReturnType<typeof vi.fn>).mock
      .calls[0];
    expect(args[0]).toEqual(expect.objectContaining({ id: BATCH_ID }));
    expect(args[1]).toEqual({ rows: undefined });
  });

  it("scopes exportToCsv to filtered rows when a search is active", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        changes: [
          {
            id: "sc-1",
            changeType: "Add",
            inputName: "alpha.example.",
            type: "A",
            ttl: 300,
            status: "Complete",
            record: { address: "1.2.3.4" },
          },
          {
            id: "sc-2",
            changeType: "Add",
            inputName: "beta.example.",
            type: "A",
            ttl: 300,
            status: "Complete",
            record: { address: "5.6.7.8" },
          },
        ],
      }),
    });
    renderAt();
    const search = await screen.findByPlaceholderText("Search");
    await userEvent.type(search, "alpha");
    const exportBtn = screen.getByRole("button", { name: /Export CSV/i });
    await userEvent.click(exportBtn);
    expect(dnsChangeService.exportToCsv).toHaveBeenCalledTimes(1);
    const args = (dnsChangeService.exportToCsv as ReturnType<typeof vi.fn>).mock
      .calls[0];
    expect(Array.isArray(args[1].rows)).toBe(true);
    expect(args[1].rows).toHaveLength(1);
    expect(args[1].rows[0]).toEqual(
      expect.objectContaining({ inputName: "alpha.example." }),
    );
  });
});

describe("<DnsChangeDetailPage /> per-row info & filtering", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
  });

  it('renders "No further action is required." for AutoApproved + Complete rows', async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: makeBatch() });
    renderAt();
    expect(
      await screen.findByText("No further action is required."),
    ).toBeInTheDocument();
  });

  it("renders validation errors in red for failed rows", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        status: "Failed",
        // Validation errors are only surfaced when the batch is NOT
        // AutoApproved (mirrors the source `buildAdditionalInfoText` rule).
        approvalStatus: "PendingReview",
        changes: [
          {
            id: "sc-1",
            changeType: "Add",
            inputName: "bad.example.",
            type: "A",
            ttl: 300,
            status: "Failed",
            validationErrors: [{ message: "Conflicting record exists" }],
          },
        ],
      }),
    });
    renderAt();
    const cell = await screen.findByText(/Conflicting record exists/);
    expect(cell).toHaveClass("text-danger");
  });

  it("filters rows by inputName when the user types in the search box", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        changes: [
          {
            id: "sc-1",
            changeType: "Add",
            inputName: "alpha.example.",
            type: "A",
            ttl: 300,
            status: "Complete",
            record: { address: "1.1.1.1" },
          },
          {
            id: "sc-2",
            changeType: "Add",
            inputName: "beta.example.",
            type: "A",
            ttl: 300,
            status: "Complete",
            record: { address: "2.2.2.2" },
          },
        ],
      }),
    });
    renderAt();
    await screen.findByText("alpha.example.");
    expect(screen.getByText("beta.example.")).toBeInTheDocument();
    const search = screen.getByPlaceholderText("Search");
    await userEvent.type(search, "alpha");
    await waitFor(() => {
      expect(screen.queryByText("beta.example.")).not.toBeInTheDocument();
    });
    expect(screen.getByText("alpha.example.")).toBeInTheDocument();
  });
});

describe("<DnsChangeDetailPage /> Owner Group tile", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setProfile({
      id: "u-alice",
      userName: "alice",
      isSuper: false,
      isSupport: false,
    });
  });

  it("renders the owner group name when present", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({
        ownerGroupId: "g-1",
        ownerGroupName: "Engineering Team",
      }),
    });
    renderAt();
    expect(await screen.findByText("Owner Group")).toBeInTheDocument();
    expect(screen.getByText("Engineering Team")).toBeInTheDocument();
  });

  it("shows a 'Group deleted' warning when only the id is present", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: makeBatch({ ownerGroupId: "g-removed" }),
    });
    renderAt();
    expect(await screen.findByText("Owner Group")).toBeInTheDocument();
    expect(screen.getByText("Group deleted")).toBeInTheDocument();
  });

  it("does not render the Owner Group tile when neither id nor name is set", async () => {
    (
      dnsChangeService.getBatchChange as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: makeBatch() });
    renderAt();
    await screen.findByText("DNS Change");
    expect(screen.queryByText("Owner Group")).not.toBeInTheDocument();
  });
});
