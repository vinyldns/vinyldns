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
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, waitFor, within, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// All external service modules MUST be mocked before importing the page
// component — otherwise the real axios-backed services will be loaded and
// throw network errors during render.
vi.mock("../../services/recordsService", () => ({
  recordsService: {
    listRecordSetData: vi
      .fn()
      .mockResolvedValue({ data: { recordSets: [], nextId: undefined } }),
    getRecordSuggestions: vi
      .fn()
      .mockResolvedValue({ data: { recordSets: [] } }),
    createRecordSet: vi.fn(),
    updateRecordSet: vi.fn(),
    deleteRecordSet: vi.fn(),
  },
}));
vi.mock("../../services/zonesService", () => ({
  zonesService: {
    getZoneDetails: vi.fn().mockResolvedValue({ data: { zone: {} } }),
  },
}));
vi.mock("../../services/groupsService", () => ({
  groupsService: {
    getGroups: vi.fn().mockResolvedValue({ data: { groups: [] } }),
  },
}));

import { RecordsPage } from "../../pages/RecordsPage";
import { renderWithProviders } from "../utils/renderWithProviders";
import { recordsService } from "../../services/recordsService";
import { groupsService } from "../../services/groupsService";

/** Helper: build a fully-populated record row for the API mock. */
function recordRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "rec-1",
    name: "host",
    type: "A",
    ttl: 300,
    fqdn: "host.example.com.",
    zoneName: "example.com.",
    zoneId: "zone-1",
    status: "Active",
    records: [{ address: "1.2.3.4" }],
    accessLevel: "Write",
    zoneShared: true,
    ...overrides,
  };
}

/** Helper: make the listRecordSetData mock return a fixed set of rows. */
function mockRecords(rows: ReturnType<typeof recordRow>[], nextId?: string) {
  (
    recordsService.listRecordSetData as ReturnType<typeof vi.fn>
  ).mockResolvedValue({
    data: { recordSets: rows, nextId },
  });
}

describe("<RecordsPage /> (Global RecordSet Search)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [], nextId: undefined },
    });
    (
      recordsService.getRecordSuggestions as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSets: [] } });
    (groupsService.getGroups as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { groups: [] },
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("renders the page header and the FQDN search input", async () => {
    renderWithProviders(<RecordsPage />);
    expect(
      await screen.findByText(/Global RecordSet Search/i),
    ).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search by FQDN/i)).toBeInTheDocument();
  });

  it("calls listRecordSetData after an FQDN search", async () => {
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host{Enter}");
    await waitFor(() => {
      expect(recordsService.listRecordSetData).toHaveBeenCalled();
    });
  });

  it("shows the empty state when the API returns zero records", async () => {
    renderWithProviders(<RecordsPage />);
    expect(await screen.findByText("No records found")).toBeInTheDocument();
  });

  it("renders rows when the API returns record sets", async () => {
    mockRecords([recordRow()]);
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host{Enter}");
    expect(await screen.findByText("host.example.com.")).toBeInTheDocument();
  });
});

/* -------------------------------------------------------------------------- */
/* FQDN search                                                                */
/* -------------------------------------------------------------------------- */
describe("<RecordsPage /> FQDN search", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [recordRow()], nextId: undefined },
    });
    (
      recordsService.getRecordSuggestions as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSets: [] } });
    (groupsService.getGroups as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { groups: [] },
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("submits the FQDN to the API when the user presses Enter", async () => {
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host{Enter}");
    await waitFor(() => {
      const calls = (
        recordsService.listRecordSetData as ReturnType<typeof vi.fn>
      ).mock.calls;
      // 3rd positional arg of listRecordSetData is the recordNameFilter.
      expect(calls.some((c) => c[2] === "host")).toBe(true);
    });
  });

  it("splits a 'fqdn | TYPE' entry into separate name and type filters", async () => {
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host.example.com. | A{Enter}");
    await waitFor(() => {
      const calls = (
        recordsService.listRecordSetData as ReturnType<typeof vi.fn>
      ).mock.calls;
      expect(
        calls.some((c) => c[2] === "host.example.com." && c[3] === "A"),
      ).toBe(true);
    });
  });
});

/* -------------------------------------------------------------------------- */
/* Type filter gating                                                         */
/* -------------------------------------------------------------------------- */
describe("<RecordsPage /> Type filter gating", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (
      recordsService.getRecordSuggestions as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSets: [] } });
    (groupsService.getGroups as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { groups: [] },
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("disables the Type filter until a FQDN search returns results", async () => {
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [], nextId: undefined },
    });
    renderWithProviders(<RecordsPage />);
    const typeBtn = (await screen.findByText("Type")).closest("div");
    expect(typeBtn).toHaveStyle({ pointerEvents: "none" });
    // Tooltip-style hint on the wrapper
    expect(typeBtn).toHaveAttribute("title", "Search for a record name first");
  });

  it("disables the Type filter when a FQDN search returns zero records", async () => {
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [], nextId: undefined },
    });
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "missing{Enter}");
    await waitFor(() => {
      expect(recordsService.listRecordSetData).toHaveBeenCalled();
    });
    const typeBtnWrapper = (await screen.findByText("Type")).closest("div");
    expect(typeBtnWrapper).toHaveStyle({ pointerEvents: "none" });
  });

  it("enables the Type filter once an FQDN search returns results", async () => {
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [recordRow()], nextId: undefined },
    });
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host{Enter}");
    await waitFor(() => {
      const typeBtnWrapper = screen.getByText("Type").closest("div");
      expect(typeBtnWrapper).not.toHaveStyle({ pointerEvents: "none" });
    });
  });
});

/* -------------------------------------------------------------------------- */
/* Owner Group combobox (the new combobox replacing the broken fuzzy input)   */
/* -------------------------------------------------------------------------- */
describe("<RecordsPage /> Owner Group combobox", () => {
  const groups = [
    { id: "gid-eng", name: "Engineering Team", email: "eng@example.com" },
    { id: "gid-ops", name: "Operations Team", email: "ops@example.com" },
    { id: "gid-sec", name: "Security Team", email: "sec@example.com" },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    (groupsService.getGroups as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { groups },
    });
    // Provide a result so the combobox is enabled.
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [recordRow()], nextId: undefined },
    });
    (
      recordsService.getRecordSuggestions as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSets: [] } });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  /** Render and run an FQDN search so the combobox becomes enabled. */
  async function renderWithSearch() {
    const result = renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(input, "host{Enter}");
    await waitFor(() => {
      const wrapper = screen.getByPlaceholderText("Owner Group").closest("div");
      expect(wrapper).not.toHaveStyle({ pointerEvents: "none" });
    });
    return result;
  }

  it("renders the Owner Group input only when groups have loaded", async () => {
    renderWithProviders(<RecordsPage />);
    expect(
      await screen.findByPlaceholderText("Owner Group"),
    ).toBeInTheDocument();
  });

  it("disables the combobox before any FQDN search", async () => {
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [], nextId: undefined },
    });
    renderWithProviders(<RecordsPage />);
    const input = await screen.findByPlaceholderText("Owner Group");
    const wrapper = input.closest("div.input-group") as HTMLElement;
    expect(wrapper).toHaveStyle({ pointerEvents: "none" });
    expect(wrapper).toHaveAttribute("title", "Search for a record name first");
  });

  it("opens the dropdown with all groups, sorted alphabetically, on focus", async () => {
    await renderWithSearch();
    const input = screen.getByPlaceholderText("Owner Group");
    await userEvent.click(input);
    const items = await screen.findAllByText(/Team$/);
    // Sorted: Engineering, Operations, Security
    expect(items.map((el) => el.textContent)).toEqual([
      "Engineering Team",
      "Operations Team",
      "Security Team",
    ]);
  });

  it("filters the dropdown to matches when the user types", async () => {
    const { container } = await renderWithSearch();
    const input = screen.getByPlaceholderText("Owner Group");
    const wrapper = input.closest("div.input-group") as HTMLElement;
    const chevron = within(wrapper).getAllByRole("button").slice(-1)[0];
    fireEvent.mouseDown(chevron);
    expect(await screen.findByText("Operations Team")).toBeInTheDocument();
    // "ope" matches only Operations Team (substring search, case-insensitive).
    fireEvent.change(input, { target: { value: "ope" } });
    await waitFor(() => {
      const lis = [
        ...container.querySelectorAll("ul.vds-toolbar-filter-list li"),
      ].map((li) => li.textContent?.trim());
      expect(lis).toEqual(["Operations Team"]);
    });
  });

  it("shows 'No groups match' when nothing matches the typed text", async () => {
    await renderWithSearch();
    const input = screen.getByPlaceholderText("Owner Group");
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "zzzzz" } });
    expect(await screen.findByText("No groups match")).toBeInTheDocument();
  });

  it("sends the selected group ID (not name) to the API on click", async () => {
    await renderWithSearch();
    (recordsService.listRecordSetData as ReturnType<typeof vi.fn>).mockClear();
    const input = screen.getByPlaceholderText("Owner Group");
    await userEvent.click(input);
    await userEvent.click(await screen.findByText("Operations Team"));
    await waitFor(() => {
      const calls = (
        recordsService.listRecordSetData as ReturnType<typeof vi.fn>
      ).mock.calls;
      // 6th positional arg = ownerGroupFilter (a group ID)
      expect(calls.some((c) => c[5] === "gid-ops")).toBe(true);
    });
  });

  it("fills the input with the chosen group name after selection", async () => {
    await renderWithSearch();
    const input = screen.getByPlaceholderText(
      "Owner Group",
    ) as HTMLInputElement;
    await userEvent.click(input);
    await userEvent.click(await screen.findByText("Engineering Team"));
    expect(input.value).toBe("Engineering Team");
  });

  it("clears the filter (sends empty ownerGroup) when the user wipes the input", async () => {
    await renderWithSearch();
    const input = screen.getByPlaceholderText(
      "Owner Group",
    ) as HTMLInputElement;
    await userEvent.click(input);
    await userEvent.click(await screen.findByText("Engineering Team"));
    (recordsService.listRecordSetData as ReturnType<typeof vi.fn>).mockClear();
    await userEvent.clear(input);
    await waitFor(() => {
      const calls = (
        recordsService.listRecordSetData as ReturnType<typeof vi.fn>
      ).mock.calls;
      // 6th positional arg should be undefined or empty after clear
      expect(calls.some((c) => !c[5])).toBe(true);
    });
  });

  it("toggles the dropdown open and closed when the chevron is clicked", async () => {
    await renderWithSearch();
    // Find the chevron button by its sibling input
    const input = screen.getByPlaceholderText("Owner Group");
    const wrapper = input.closest("div.input-group") as HTMLElement;
    const buttons = within(wrapper).getAllByRole("button");
    const chevronBtn = buttons[buttons.length - 1]; // chevron is last button
    // Initially closed: list should not be rendered
    expect(screen.queryByText("Engineering Team")).not.toBeInTheDocument();
    // Open
    fireEvent.mouseDown(chevronBtn);
    expect(await screen.findByText("Engineering Team")).toBeInTheDocument();
    // Close
    fireEvent.mouseDown(chevronBtn);
    await waitFor(() => {
      expect(screen.queryByText("Engineering Team")).not.toBeInTheDocument();
    });
  });

  it("renders the (×) clear button only when the input has text", async () => {
    await renderWithSearch();
    const input = screen.getByPlaceholderText(
      "Owner Group",
    ) as HTMLInputElement;
    const wrapper = input.closest("div.input-group") as HTMLElement;
    // No clear button initially (only the chevron button)
    expect(within(wrapper).getAllByRole("button")).toHaveLength(1);
    await userEvent.type(input, "eng");
    // After typing, both clear + chevron buttons exist
    await waitFor(() => {
      expect(within(wrapper).getAllByRole("button")).toHaveLength(2);
    });
  });
});

/* -------------------------------------------------------------------------- */
/* Clear All                                                                  */
/* -------------------------------------------------------------------------- */
describe("<RecordsPage /> Clear All", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (groupsService.getGroups as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        groups: [
          { id: "gid-eng", name: "Engineering Team", email: "e@example.com" },
        ],
      },
    });
    (
      recordsService.listRecordSetData as ReturnType<typeof vi.fn>
    ).mockResolvedValue({
      data: { recordSets: [recordRow()], nextId: undefined },
    });
    (
      recordsService.getRecordSuggestions as ReturnType<typeof vi.fn>
    ).mockResolvedValue({ data: { recordSets: [] } });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("resets the owner-group filter and re-issues a no-filter search", async () => {
    renderWithProviders(<RecordsPage />);
    // Trigger a FQDN search so the combobox becomes enabled
    const fqdn = await screen.findByPlaceholderText(/Search by FQDN/i);
    await userEvent.type(fqdn, "host{Enter}");
    await waitFor(() => {
      expect(recordsService.listRecordSetData).toHaveBeenCalled();
    });

    // Select an owner group
    const og = screen.getByPlaceholderText("Owner Group") as HTMLInputElement;
    await userEvent.click(og);
    await userEvent.click(await screen.findByText("Engineering Team"));
    expect(og.value).toBe("Engineering Team");

    // Now click Clear All
    (recordsService.listRecordSetData as ReturnType<typeof vi.fn>).mockClear();
    await userEvent.click(screen.getByTitle("Clear all filters"));

    // Input should be empty; the next API call should have no owner-group param.
    await waitFor(() => {
      expect(og.value).toBe("");
      const calls = (
        recordsService.listRecordSetData as ReturnType<typeof vi.fn>
      ).mock.calls;
      expect(calls.some((c) => !c[5])).toBe(true);
    });
  });
});
