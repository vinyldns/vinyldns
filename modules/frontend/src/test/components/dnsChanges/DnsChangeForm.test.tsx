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

// Mock the groups service BEFORE importing the form so the owner-group
// dropdown query never hits the network.
vi.mock("../../../services/groupsService", () => ({
  groupsService: {
    getGroups: vi.fn().mockResolvedValue({ data: { groups: [] } }),
  },
}));

import { DnsChangeForm } from "../../../components/dnsChanges/DnsChangeForm";
import { groupsService } from "../../../services/groupsService";
import { renderWithProviders } from "../../utils/renderWithProviders";

/**
 * Focused render / interaction tests for the DnsChangeForm.
 *
 * CSV import + duplicate review is covered in
 * `DnsChangeForm.duplicates.test.tsx`; pure helpers are tested in
 * `DnsChangeForm.helpers.test.ts`. This file targets the page-level UX:
 * default row, add / remove, submit two-step confirmation, and serverRowErrors.
 *
 * Note: the form initializes with ONE default row already present.
 */
describe("<DnsChangeForm /> render + interaction", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function renderForm(overrides?: {
    onSubmit?: (data: unknown, allow: boolean) => void;
    onCancel?: () => void;
    isSubmitting?: boolean;
    serverRowErrors?: string[][];
  }) {
    const onSubmit = overrides?.onSubmit ?? vi.fn();
    const onCancel = overrides?.onCancel ?? vi.fn();
    const utils = renderWithProviders(
      <DnsChangeForm
        onSubmit={onSubmit}
        onCancel={onCancel}
        isSubmitting={overrides?.isSubmitting ?? false}
        serverRowErrors={overrides?.serverRowErrors}
      />,
    );
    return { ...utils, onSubmit, onCancel };
  }

  /** Fills row 0 with a valid A-type record so handleSubmit can pass. */
  async function fillRowAsValidA(container: HTMLElement) {
    // Switch row 0's record type from A+PTR (default) to A so we only
    // need an IPv4 address (no reverse-PTR requirement to satisfy).
    const typeSelect = container.querySelector(
      "select[name='changes.0.type']",
    ) as HTMLSelectElement | null;
    expect(typeSelect).not.toBeNull();
    await userEvent.selectOptions(typeSelect!, "A");

    const inputName = container.querySelector(
      "input[name='changes.0.inputName']",
    ) as HTMLInputElement | null;
    expect(inputName).not.toBeNull();
    await userEvent.type(inputName!, "host.example.");

    const ipInput = container.querySelector(
      "input[placeholder='e.g. 1.1.1.1']",
    ) as HTMLInputElement | null;
    expect(ipInput).not.toBeNull();
    await userEvent.type(ipInput!, "1.2.3.4");
  }

  it("starts with one default row and an enabled Add Change button", async () => {
    const { container } = renderForm();
    // The default row is present immediately on mount.
    const rows = container.querySelectorAll("button[title='Remove row']");
    expect(rows.length).toBe(1);
    expect(
      await screen.findByRole("button", { name: /Add Change/i }),
    ).not.toBeDisabled();
  });

  it("enables Submit Batch Change while at least one row exists", async () => {
    renderForm();
    const submit = await screen.findByRole("button", {
      name: /Submit Batch Change/i,
    });
    expect(submit).not.toBeDisabled();
  });

  it("clicking Add Change appends another row", async () => {
    const { container } = renderForm();
    await userEvent.click(
      await screen.findByRole("button", { name: /Add Change/i }),
    );
    await waitFor(() => {
      expect(
        container.querySelectorAll("button[title='Remove row']").length,
      ).toBe(2);
    });
  });

  it("clicking Remove on a row deletes it", async () => {
    const { container } = renderForm();
    // Start with 1; add 2 more for 3 total, then remove the first.
    const addBtn = await screen.findByRole("button", { name: /Add Change/i });
    await userEvent.click(addBtn);
    await userEvent.click(addBtn);
    await waitFor(() => {
      expect(
        container.querySelectorAll("button[title='Remove row']").length,
      ).toBe(3);
    });
    const removeButtons = container.querySelectorAll(
      "button[title='Remove row']",
    );
    await userEvent.click(removeButtons[0]);
    await waitFor(() => {
      expect(
        container.querySelectorAll("button[title='Remove row']").length,
      ).toBe(2);
    });
  });

  it("calls onCancel when the footer Cancel button is clicked", async () => {
    const onCancel = vi.fn();
    renderForm({ onCancel });
    const cancel = await screen.findByRole("button", { name: /^Cancel$/i });
    await userEvent.click(cancel);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("shows the Submitting… label when isSubmitting is true", async () => {
    renderForm({ isSubmitting: true });
    const submittingLabels = await screen.findAllByText(/Submitting/i);
    expect(submittingLabels.length).toBeGreaterThan(0);
  });

  it("submitting a valid row enters the confirmation panel", async () => {
    const onSubmit = vi.fn();
    const { container } = renderForm({ onSubmit });
    await fillRowAsValidA(container);
    await userEvent.click(
      screen.getByRole("button", { name: /Submit Batch Change/i }),
    );
    await waitFor(
      () => {
        expect(
          screen.getByRole("button", { name: /Confirm.*Submit/i }),
        ).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("confirming the staged submit calls onSubmit with the payload", async () => {
    const onSubmit = vi.fn();
    const { container } = renderForm({ onSubmit });
    await fillRowAsValidA(container);
    await userEvent.click(
      screen.getByRole("button", { name: /Submit Batch Change/i }),
    );
    const confirmBtn = await screen.findByRole(
      "button",
      { name: /Confirm.*Submit/i },
      { timeout: 3000 },
    );
    await userEvent.click(confirmBtn);
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1);
    });
    const [payload, allowReview] = onSubmit.mock.calls[0];
    expect(allowReview).toBe(false);
    expect(payload).toMatchObject({
      changes: expect.arrayContaining([
        expect.objectContaining({
          changeType: "Add",
          type: "A",
          inputName: "host.example.",
        }),
      ]),
    });
  });

  it("Back to Edit returns to the editing footer without calling onSubmit", async () => {
    const onSubmit = vi.fn();
    const { container } = renderForm({ onSubmit });
    await fillRowAsValidA(container);
    await userEvent.click(
      screen.getByRole("button", { name: /Submit Batch Change/i }),
    );
    await screen.findByRole(
      "button",
      { name: /Confirm.*Submit/i },
      { timeout: 3000 },
    );
    await userEvent.click(
      screen.getByRole("button", { name: /Back to Edit/i }),
    );
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /Submit Batch Change/i }),
      ).toBeInTheDocument();
    });
    expect(
      screen.queryByRole("button", { name: /Confirm.*Submit/i }),
    ).not.toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("displays inline serverRowErrors against the matching row", async () => {
    const { rerender } = renderForm();
    rerender(
      <DnsChangeForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
        serverRowErrors={[["Zone not found for host.example."]]}
      />,
    );
    await waitFor(() => {
      expect(
        screen.getByText(/Zone not found for host\.example\./i),
      ).toBeInTheDocument();
    });
  });

  it("does not advance to confirmation when the row is invalid (empty fqdn)", async () => {
    const onSubmit = vi.fn();
    renderForm({ onSubmit });
    // Default row has empty inputName — inputName is required. Clicking
    // Submit should NOT advance to the confirmation panel.
    await userEvent.click(
      screen.getByRole("button", { name: /Submit Batch Change/i }),
    );
    // Give react-hook-form a tick to run validation.
    await new Promise((r) => setTimeout(r, 100));
    expect(
      screen.queryByRole("button", { name: /Confirm.*Submit/i }),
    ).not.toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("shows a record-data validation message when the value is empty on submit", async () => {
    const onSubmit = vi.fn();
    const { container } = renderForm({ onSubmit });

    const typeSelect = container.querySelector(
      "select[name='changes.0.type']",
    ) as HTMLSelectElement | null;
    await userEvent.selectOptions(typeSelect!, "A");

    const inputName = container.querySelector(
      "input[name='changes.0.inputName']",
    ) as HTMLInputElement | null;
    await userEvent.type(inputName!, "host.example.");

    await userEvent.click(
      screen.getByRole("button", { name: /Submit Batch Change/i }),
    );

    await waitFor(() => {
      expect(screen.getByText("Record data is required")).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("does not show the shared-zone placeholder when owner groups are available", async () => {
    vi.mocked(groupsService.getGroups).mockResolvedValue({
      data: { groups: [{ id: "g-1", name: "Alpha Team" }] },
    } as never);

    renderForm();

    expect(
      screen.queryByPlaceholderText("Required for shared zone records"),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByRole("option", { name: "Alpha Team" }),
    ).toBeInTheDocument();
  });
});
