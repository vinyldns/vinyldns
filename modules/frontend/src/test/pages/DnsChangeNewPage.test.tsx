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

// Stub out router navigation BEFORE importing the page component so the
// real implementation never runs.
const navigateMock = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

// Stub the DnsChangeForm so the page test stays focused on the page's own
// behavior (header, submit wiring, navigation, error routing) instead of the
// full form rendering.
vi.mock("../../components/dnsChanges/DnsChangeForm", () => ({
  DnsChangeForm: ({
    onSubmit,
    onCancel,
    serverRowErrors,
    onUnsavedChange,
  }: {
    onSubmit: (data: unknown, allowReview: boolean) => void;
    onCancel: () => void;
    serverRowErrors?: string[][];
    onUnsavedChange?: (hasUnsaved: boolean) => void;
  }) => (
    <div data-testid="mock-dns-change-form">
      <button
        type="button"
        data-testid="mock-submit"
        onClick={() =>
          onSubmit({ comments: "", changes: [{ changeType: "Add" }] }, false)
        }
      >
        submit
      </button>
      <button type="button" data-testid="mock-cancel" onClick={onCancel}>
        cancel
      </button>
      <button
        type="button"
        data-testid="mock-mark-dirty"
        onClick={() => onUnsavedChange?.(true)}
      >
        mark dirty
      </button>
      {serverRowErrors && serverRowErrors.some((e) => e.length > 0) && (
        <div data-testid="mock-row-errors">
          {serverRowErrors.flat().join("|")}
        </div>
      )}
    </div>
  ),
}));

// Capture the most recent createBatchChange invocation so each test can
// trigger the success or error callback at its own pace.
const createBatchChangeMock = vi.fn();
vi.mock("../../hooks/useDnsChanges", () => ({
  useDnsChanges: () => ({
    createBatchChange: createBatchChangeMock,
    isSubmitting: false,
  }),
}));

const addAlertMock = vi.fn();
vi.mock("../../contexts/AlertContext", async () => {
  const actual = await vi.importActual<
    typeof import("../../contexts/AlertContext")
  >("../../contexts/AlertContext");
  return {
    ...actual,
    useAlerts: () => ({
      addAlert: addAlertMock,
      alerts: [],
      removeAlert: vi.fn(),
    }),
  };
});

import { DnsChangeNewPage } from "../../pages/DnsChangeNewPage";
import { renderWithProviders } from "../utils/renderWithProviders";

describe("<DnsChangeNewPage />", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    createBatchChangeMock.mockReset();
    addAlertMock.mockReset();
  });

  it("renders the header and mounts the DnsChangeForm", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    expect(await screen.findByText("New Batch Change")).toBeInTheDocument();
    expect(screen.getByTestId("mock-dns-change-form")).toBeInTheDocument();
  });

  it("navigates back to /dnschanges when Back button is clicked", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(
      await screen.findByRole("button", { name: /Back to DNS Changes/i }),
    );
    expect(navigateMock).toHaveBeenCalledWith("/dnschanges");
  });

  it("shows the discard confirmation modal when leaving with unsaved changes", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-mark-dirty"));
    await userEvent.click(
      await screen.findByRole("button", { name: /Back to DNS Changes/i }),
    );

    expect(
      await screen.findByText("Discard batch change?"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Discard changes/i }),
    ).toBeInTheDocument();
  });

  it("navigates back when the form requests cancel", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-cancel"));
    expect(navigateMock).toHaveBeenCalledWith("/dnschanges");
  });

  it("navigates to /dnschanges on a successful createBatchChange", async () => {
    createBatchChangeMock.mockImplementation((_payload, { onSuccess }) => {
      onSuccess();
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));
    expect(createBatchChangeMock).toHaveBeenCalledTimes(1);
    expect(navigateMock).toHaveBeenCalledWith("/dnschanges");
  });

  it("routes per-row errors back to the form on a 400 with an array body", async () => {
    createBatchChangeMock.mockImplementation((_payload, { onError }) => {
      onError({
        response: {
          status: 400,
          data: [{ errors: ["Zone not found"] }, { errors: [] }],
        },
      });
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("mock-row-errors")).toHaveTextContent(
        "Zone not found",
      );
    });
    expect(addAlertMock).toHaveBeenCalledWith(
      "danger",
      expect.stringMatching(/Errors found in one or more rows/i),
    );
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it("does not raise an alert when a 400 response contains zero row errors", async () => {
    createBatchChangeMock.mockImplementation((_payload, { onError }) => {
      onError({
        response: {
          status: 400,
          data: [{ errors: [] }, { errors: [] }],
        },
      });
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));
    expect(addAlertMock).not.toHaveBeenCalled();
  });
  it("renders the page subtitle text describing the form purpose", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    expect(
      await screen.findByText(
        /Submit a new DNS batch change request for review and processing/i,
      ),
    ).toBeInTheDocument();
  });

  it("ignores non-400 errors (no row errors, no alert, no navigation)", async () => {
    createBatchChangeMock.mockImplementation((_payload, { onError }) => {
      onError({ response: { status: 500, data: { message: "boom" } } });
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));
    expect(addAlertMock).not.toHaveBeenCalled();
    expect(navigateMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId("mock-row-errors")).not.toBeInTheDocument();
  });

  it("ignores a 400 response whose body is not an array", async () => {
    createBatchChangeMock.mockImplementation((_payload, { onError }) => {
      onError({
        response: { status: 400, data: { message: "validation failed" } },
      });
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));
    expect(addAlertMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId("mock-row-errors")).not.toBeInTheDocument();
  });

  it("clears prior row errors when the next submit succeeds", async () => {
    // First call fails with row errors.
    createBatchChangeMock.mockImplementationOnce((_payload, { onError }) => {
      onError({
        response: {
          status: 400,
          data: [{ errors: ["Bad zone"] }],
        },
      });
    });
    renderWithProviders(<DnsChangeNewPage />);
    await userEvent.click(await screen.findByTestId("mock-submit"));
    await screen.findByTestId("mock-row-errors");

    // Second call succeeds — row errors should be reset and we navigate.
    createBatchChangeMock.mockImplementationOnce((_payload, { onSuccess }) => {
      onSuccess();
    });
    await userEvent.click(screen.getByTestId("mock-submit"));
    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/dnschanges");
    });
    // Alert should not have been called twice (only the first failing call).
    expect(addAlertMock).toHaveBeenCalledTimes(1);
  });

  it("mounts cleanly with the breadcrumb provider", async () => {
    renderWithProviders(<DnsChangeNewPage />);
    await screen.findByText("New Batch Change");
  });
});
