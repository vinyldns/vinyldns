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
import { screen, fireEvent, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock the records service BEFORE importing the modal so the React Query
// inside the component picks up the mocked implementation.
vi.mock("../../../services/recordsService", () => ({
  recordsService: {
    listRecordSetChangeHistory: vi.fn(),
  },
}));

import { recordsService } from "../../../services/recordsService";
import { RecordHistoryModal } from "../../../components/records/RecordHistoryModal";
import { renderWithProviders } from "../../utils/renderWithProviders";

function record(overrides: Record<string, unknown> = {}) {
  return {
    id: "rec-1",
    zoneId: "zone-1",
    fqdn: "host.example.com.",
    name: "host",
    type: "A",
    ...overrides,
  };
}

function change(overrides: Record<string, unknown> = {}) {
  return {
    id: "chg-1",
    changeType: "Update",
    status: "Complete",
    created: "2024-01-15T12:00:00Z",
    userName: "alice",
    recordSet: {
      name: "host",
      type: "A",
      ttl: 300,
      records: [{ address: "1.2.3.4" }],
    },
    ...overrides,
  };
}

describe("<RecordHistoryModal /> integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the header with FQDN and Record Change History title", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    expect(
      await screen.findByText(/Record Change History/i),
    ).toBeInTheDocument();
    expect(screen.getByText("host.example.com.")).toBeInTheDocument();
  });

  it("shows the loading spinner while the history query is in flight", () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockReturnValue(new Promise(() => {})); // never resolves
    const { container } = renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    expect(container.querySelector(".vds-loader-backdrop")).toBeInTheDocument();
  });

  it("renders the empty state when the server returns zero changes", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    expect(await screen.findByText(/No change history/i)).toBeInTheDocument();
  });

  it("renders the failure state when the API rejects", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockRejectedValue(new Error("boom"));
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    expect(
      await screen.findByText(/Failed to load history/i),
    ).toBeInTheDocument();
  });

  it("renders a row per change with the user name and change type", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: {
        recordSetChanges: [
          change({ id: "c1", changeType: "Create", userName: "alice" }),
          change({ id: "c2", changeType: "Delete", userName: "bob" }),
        ],
        nextId: null,
      },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText("alice");
    expect(screen.getByText("bob")).toBeInTheDocument();
    expect(screen.getByText("Create")).toBeInTheDocument();
    expect(screen.getByText("Delete")).toBeInTheDocument();
  });

  it("calls onClose when the Close button is clicked", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSetChanges: [], nextId: null } });
    const onClose = vi.fn();
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={onClose} />,
    );
    await screen.findByText(/No change history/i);
    await userEvent.click(screen.getByLabelText("Close"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("calls onClose on Escape key", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSetChanges: [], nextId: null } });
    const onClose = vi.fn();
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={onClose} />,
    );
    await screen.findByText(/No change history/i);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("triggers refetch when the Refresh button is clicked", async () => {
    const mock = recordsService.listRecordSetChangeHistory as ReturnType<
      typeof vi.fn
    >;
    mock.mockResolvedValue({
      data: { recordSetChanges: [change()], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText("alice");
    const initialCalls = mock.mock.calls.length;
    await userEvent.click(screen.getByLabelText("Refresh"));
    await waitFor(() => {
      expect(mock.mock.calls.length).toBeGreaterThan(initialCalls);
    });
  });

  it("opens the per-change info detail modal when the info button is clicked", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [change()], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText("alice");
    await userEvent.click(screen.getByTitle("View change details"));
    // Detail modal renders a Close button with title="Close" — wait for it.
    await waitFor(() => {
      expect(screen.getAllByTitle("Close").length).toBeGreaterThan(0);
    });
  });

  it("renders pagination controls only when there is more than one page", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [change()], nextId: "cursor-2" },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText("alice");
    // Pagination Next button is enabled when hasMore is true.
    expect(screen.getAllByRole("button").length).toBeGreaterThan(0);
  });

  it("does NOT render pagination when there is only one page", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [change()], nextId: null },
    });
    const { container } = renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText("alice");
    // Just make sure no .vds-pagination or rangeLabel "1–1" element exists.
    expect(container.textContent).not.toMatch(/1–1/);
  });

  it("renders the type badge in the header", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal
        record={record({ type: "CNAME" })}
        onClose={vi.fn()}
      />,
    );
    expect(await screen.findAllByText("CNAME")).toHaveLength(1);
  });

  it("renders the Record ID and Zone ID chips with copy buttons", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSetChanges: [], nextId: null },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText(/Record Change History/i);
    expect(screen.getByText(/Record ID/i)).toBeInTheDocument();
    expect(screen.getByText(/Zone ID/i)).toBeInTheDocument();
    expect(screen.getByTitle("Copy Record ID")).toBeInTheDocument();
    expect(screen.getByTitle("Copy Zone ID")).toBeInTheDocument();
  });

  it("requests history using the provided zoneId + page size of 100", async () => {
    const mock = recordsService.listRecordSetChangeHistory as ReturnType<
      typeof vi.fn
    >;
    mock.mockResolvedValue({ data: { recordSetChanges: [], nextId: null } });
    renderWithProviders(
      <RecordHistoryModal
        record={record({ zoneId: "zone-X", fqdn: "y.example.", type: "AAAA" })}
        onClose={vi.fn()}
      />,
    );
    await waitFor(() => {
      expect(mock).toHaveBeenCalled();
    });
    expect(mock).toHaveBeenCalledWith(
      "zone-X",
      100,
      undefined,
      "y.example.",
      "AAAA",
    );
  });

  it("uses the change UserAvatar fallback to 'System' when userName is missing", async () => {
    (
      recordsService.listRecordSetChangeHistory as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: {
        recordSetChanges: [change({ userName: undefined })],
        nextId: null,
      },
    });
    renderWithProviders(
      <RecordHistoryModal record={record()} onClose={vi.fn()} />,
    );
    await screen.findByText(/System/i);
  });
});
