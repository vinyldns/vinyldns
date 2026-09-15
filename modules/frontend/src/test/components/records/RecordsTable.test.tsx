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
import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RecordsTable } from "../../../components/records/RecordsTable";
import { renderWithProviders } from "../../utils/renderWithProviders";
import type { RecordSet } from "../../../types/record";

function record(overrides: Partial<RecordSet> = {}): RecordSet {
  return {
    id: "rec-1",
    zoneId: "zone-1",
    zoneName: "example.com.",
    fqdn: "host.example.com.",
    name: "host",
    type: "A",
    status: "Active",
    ttl: 300,
    records: [{ address: "1.2.3.4" }],
    ...overrides,
  } as RecordSet;
}

describe("<RecordsTable />", () => {
  it("renders the empty state when no records are provided", () => {
    renderWithProviders(<RecordsTable records={[]} />);

    expect(screen.getByText("No records found")).toBeInTheDocument();
    expect(
      screen.getByText("Try adjusting the filter or add a new record."),
    ).toBeInTheDocument();
  });

  it("renders the core columns and row values for a private zone", () => {
    renderWithProviders(<RecordsTable records={[record()]} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Type")).toBeInTheDocument();
    expect(screen.getByText("TTL")).toBeInTheDocument();
    expect(screen.getByText("Record Data")).toBeInTheDocument();
    expect(screen.getByText("Status")).toBeInTheDocument();

    expect(screen.getByText("host")).toBeInTheDocument();
    expect(screen.getByText("A")).toBeInTheDocument();
    expect(screen.getByText("300s")).toBeInTheDocument();
    expect(screen.getByText("1.2.3.4")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("renders the private zone banner and pill when not shared", () => {
    renderWithProviders(
      <RecordsTable records={[record()]} isSharedZone={false} />,
    );

    expect(screen.getByText("Private Zone")).toBeInTheDocument();
    expect(
      screen.getByText(
        "This zone is private — ownership transfer is not available.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("Owner Group Name")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Ownership Transfer Status"),
    ).not.toBeInTheDocument();
  });

  it("renders the shared zone banner, pill and ownership columns when shared", () => {
    renderWithProviders(<RecordsTable records={[record()]} isSharedZone />);

    expect(screen.getByText("Shared Zone")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Ownership columns are shown because this zone is shared.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Owner Group Name")).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: /ownership transfer\s*status/i }),
    ).toBeInTheDocument();
  });

  it("shows an unassigned owner label when the record has no owner group", () => {
    renderWithProviders(<RecordsTable records={[record()]} isSharedZone />);

    expect(screen.getByText("Unassigned")).toBeInTheDocument();
  });

  it("shows the owner group name when the record has an owner group", () => {
    renderWithProviders(
      <RecordsTable
        records={[
          record({ ownerGroupId: "grp-1", ownerGroupName: "Platform Team" }),
        ]}
        isSharedZone
      />,
    );

    expect(screen.getByText("Platform Team")).toBeInTheDocument();
  });

  it("formats the record data for multiple record types", () => {
    renderWithProviders(
      <RecordsTable
        records={[
          record({
            id: "r-cname",
            type: "CNAME",
            records: [{ cname: "alias.example.com." }],
          }),
          record({
            id: "r-mx",
            type: "MX",
            records: [{ preference: 10, exchange: "mail.example.com." }],
          }),
          record({ id: "r-txt", type: "TXT", records: [{ text: "hello" }] }),
        ]}
      />,
    );

    expect(screen.getByText("alias.example.com.")).toBeInTheDocument();
    expect(screen.getByText("10 mail.example.com.")).toBeInTheDocument();
    expect(screen.getByText('"hello"')).toBeInTheDocument();
  });

  it("collapses record data beyond three entries into a more indicator", () => {
    renderWithProviders(
      <RecordsTable
        records={[
          record({
            records: [
              { address: "1.1.1.1" },
              { address: "2.2.2.2" },
              { address: "3.3.3.3" },
              { address: "4.4.4.4" },
            ],
          }),
        ]}
      />,
    );

    expect(screen.getByText("+1 more")).toBeInTheDocument();
  });

  it("renders an em dash when a record has no data", () => {
    renderWithProviders(<RecordsTable records={[record({ records: [] })]} />);

    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("invokes onEdit when the edit button is clicked", async () => {
    const onEdit = vi.fn();
    const rec = record();
    renderWithProviders(<RecordsTable records={[rec]} onEdit={onEdit} />);

    await userEvent.click(screen.getByTitle("Edit record"));

    expect(onEdit).toHaveBeenCalledWith(rec);
  });

  it("invokes onDelete when the delete button is clicked", async () => {
    const onDelete = vi.fn();
    const rec = record();
    renderWithProviders(<RecordsTable records={[rec]} onDelete={onDelete} />);

    await userEvent.click(screen.getByTitle("Delete record"));

    expect(onDelete).toHaveBeenCalledWith(rec);
  });

  it("omits the actions column when no action handlers are provided", () => {
    renderWithProviders(<RecordsTable records={[record()]} />);

    expect(screen.queryByText("Actions")).not.toBeInTheDocument();
  });

  it("renders a claim button when a shared record has no owner group", async () => {
    const onRequestOwnership = vi.fn();
    const rec = record();
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        onRequestOwnership={onRequestOwnership}
      />,
    );

    const claimButton = screen.getByTitle("Claim ownership of this record");
    await userEvent.click(claimButton);

    expect(onRequestOwnership).toHaveBeenCalledWith(rec);
  });

  it("renders a request button when a shared record is owned by another group", async () => {
    const onRequestOwnership = vi.fn();
    const rec = record({ ownerGroupId: "grp-owner" });
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        userGroupIds={["grp-mine"]}
        onRequestOwnership={onRequestOwnership}
      />,
    );

    const requestButton = screen.getByTitle("Request ownership transfer");
    await userEvent.click(requestButton);

    expect(onRequestOwnership).toHaveBeenCalledWith(rec);
  });

  it("hides ownership actions when the user's only group already owns the record", () => {
    renderWithProviders(
      <RecordsTable
        records={[record({ ownerGroupId: "grp-mine" })]}
        isSharedZone
        userGroupIds={["grp-mine"]}
        onRequestOwnership={vi.fn()}
      />,
    );

    expect(
      screen.queryByTitle("Request ownership transfer"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle("Claim ownership of this record"),
    ).not.toBeInTheDocument();
  });

  it("renders a disabled pending button for a pending review without permissions", () => {
    renderWithProviders(
      <RecordsTable
        records={[
          record({
            ownerGroupId: "grp-owner",
            recordSetGroupChange: { ownershipTransferStatus: "PendingReview" },
          }),
        ]}
        isSharedZone
        userGroupIds={[]}
        onRequestOwnership={vi.fn()}
      />,
    );

    expect(
      screen.getByTitle("Ownership request is pending review"),
    ).toBeDisabled();
  });

  it("lets a requestor cancel their pending ownership request", async () => {
    const onCloseOwnershipRequest = vi.fn();
    const rec = record({
      ownerGroupId: "grp-owner",
      recordSetGroupChange: {
        ownershipTransferStatus: "PendingReview",
        requestedOwnerGroupId: "grp-mine",
      },
    });
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        userGroupIds={["grp-mine"]}
        onRequestOwnership={vi.fn()}
        onCloseOwnershipRequest={onCloseOwnershipRequest}
      />,
    );

    await userEvent.click(
      screen.getByTitle("Your request is pending — click to cancel"),
    );
    await userEvent.click(screen.getByText("Cancel Request"));

    expect(onCloseOwnershipRequest).toHaveBeenCalledWith(rec);
  });

  it("lets an owner approve a pending ownership request", async () => {
    const onApproveOwnership = vi.fn();
    const rec = record({
      ownerGroupId: "grp-owner",
      recordSetGroupChange: {
        ownershipTransferStatus: "PendingReview",
        requestedOwnerGroupId: "grp-other",
      },
    });
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        userGroupIds={["grp-owner"]}
        onApproveOwnership={onApproveOwnership}
        onRejectOwnership={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByTitle("Ownership actions"));
    await userEvent.click(screen.getByText("Approve"));

    expect(onApproveOwnership).toHaveBeenCalledWith(rec);
  });

  it("lets an owner reject a pending ownership request", async () => {
    const onRejectOwnership = vi.fn();
    const rec = record({
      ownerGroupId: "grp-owner",
      recordSetGroupChange: {
        ownershipTransferStatus: "PendingReview",
        requestedOwnerGroupId: "grp-other",
      },
    });
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        userGroupIds={["grp-owner"]}
        onApproveOwnership={vi.fn()}
        onRejectOwnership={onRejectOwnership}
      />,
    );

    await userEvent.click(screen.getByTitle("Ownership actions"));
    await userEvent.click(screen.getByText("Reject"));

    expect(onRejectOwnership).toHaveBeenCalledWith(rec);
  });

  it("gives a super user approve, reject and cancel actions on a pending request", async () => {
    const onApproveOwnership = vi.fn();
    const onCloseOwnershipRequest = vi.fn();
    const rec = record({
      ownerGroupId: "grp-owner",
      recordSetGroupChange: {
        ownershipTransferStatus: "PendingReview",
        requestedOwnerGroupId: "grp-other",
      },
    });
    renderWithProviders(
      <RecordsTable
        records={[rec]}
        isSharedZone
        isSuper
        onApproveOwnership={onApproveOwnership}
        onRejectOwnership={vi.fn()}
        onCloseOwnershipRequest={onCloseOwnershipRequest}
      />,
    );

    await userEvent.click(screen.getByTitle("Ownership actions"));
    expect(screen.getByText("Approve")).toBeInTheDocument();
    expect(screen.getByText("Reject")).toBeInTheDocument();
    expect(screen.getByText("Cancel Request")).toBeInTheDocument();

    await userEvent.click(screen.getByText("Cancel Request"));

    expect(onCloseOwnershipRequest).toHaveBeenCalledWith(rec);
  });

  it("renders the status badge text for an inactive record", () => {
    renderWithProviders(
      <RecordsTable records={[record({ status: "Inactive" })]} />,
    );

    expect(screen.getByText("Inactive")).toBeInTheDocument();
  });
});
