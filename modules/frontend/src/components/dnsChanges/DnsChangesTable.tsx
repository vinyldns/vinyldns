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

import React, { useState } from "react";
import { Link } from "react-router-dom";
import type { DnsChangeSummary } from "../../types/dnsChange";
import type { PagingState } from "../../types/common";
import { formatDateTime } from "../../utils/dateUtils";

type SortDir = "asc" | "desc" | null;

function SortArrow({ dir }: { dir: SortDir }) {
  if (dir === "asc")
    return (
      <i
        className="bi bi-arrow-up"
        style={{ fontSize: "0.7rem", color: "#2e5090", marginLeft: 3 }}
      />
    );
  if (dir === "desc")
    return (
      <i
        className="bi bi-arrow-down"
        style={{ fontSize: "0.7rem", color: "#2e5090", marginLeft: 3 }}
      />
    );
  return (
    <i
      className="bi bi-arrow-down-up"
      style={{
        fontSize: "0.65rem",
        color: "#898a8b",
        marginLeft: 3,
        opacity: 0.7,
      }}
    />
  );
}

/**
 * Props for DnsChangesTable.
 *
 * @param changes        - Page of batch change summaries from the API.
 * @param onCancel       - Optional cancel handler; when omitted the cancel
 *                         button is not rendered (e.g. detail page view).
 * @param ignoreAccess   - When true the Submitter column is shown, indicating
 *                         the component is in the admin/all-requests view.
 * @param currentUserId  - Logged-in user's internal ID; used to gate the
 *                         cancel action to the original submitter only.
 *                         Cancel is owner-only at the API level — even super
 *                         users get a 403 trying to cancel another user's batch.
 */
interface DnsChangesTableProps {
  changes: DnsChangeSummary[];
  onCancel?: (change: DnsChangeSummary) => void;
  ignoreAccess?: boolean;
  currentUserId?: string;
  fromTab?: "my" | "all";
  currentPaging?: PagingState;
}

/**
 * Maps an API `status` string to a VDS CSS modifier class for the status
 * badge. Unknown statuses fall back to the neutral secondary style so the UI
 * never renders a classless badge if the API introduces a new status value.
 */
export function changeStatusClass(status: string): string {
  if (status === "Complete") return "vds-status-badge--success";
  if (status === "Failed") return "vds-status-badge--danger";
  if (status === "PartialFailure") return "vds-status-badge--warning";
  if (status === "PendingProcessing") return "vds-status-badge--info";
  if (status === "PendingReview") return "vds-status-badge--warning";
  if (status === "Pending") return "vds-status-badge--info";
  if (status === "Rejected") return "vds-status-badge--danger";
  if (status === "Scheduled") return "vds-status-badge--info";
  if (status === "Cancelled") return "vds-status-badge--secondary";
  return "vds-status-badge--secondary";
}
/**
 * Converts machine-style status strings (e.g. `PendingReview`) to readable
 * display labels. Only compound-word values need entries; single-word statuses
 * are returned as-is via the map lookup fallback.
 */
export function changeStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PartialFailure: "Partial Failure",
    PendingProcessing: "Pending Processing",
    PendingReview: "Pending Review",
  };
  return map[status] ?? status;
}

const COPY_KEYFRAMES = `
  @keyframes vdsCopiedCheck {
    0%   { opacity: 0; transform: scale(0.4) rotate(-15deg); }
    60%  { transform: scale(1.3) rotate(5deg); }
    80%  { transform: scale(0.9) rotate(-2deg); }
    100% { opacity: 1; transform: scale(1) rotate(0deg); }
  }
`;

/**
 * Displays a paginated list of batch DNS change summaries.
 *
 * The component is intentionally stateless — all data fetching and pagination
 * live in the parent (DnsChangesPage / DnsChangeDetailPage) so this component
 * remains easy to render in isolation during tests or in the detail page sidebar.
 */
export function DnsChangesTable({
  changes,
  onCancel,
  ignoreAccess,
  currentUserId,
  fromTab,
  currentPaging,
}: DnsChangesTableProps) {
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [sortState, setSortState] = useState<{ dir: "asc" | "desc" } | null>(
    null,
  );

  const handleCopyId = (id: string) => {
    void navigator.clipboard.writeText(id).then(() => {
      setCopiedId(id);
      setTimeout(() => setCopiedId((cur) => (cur === id ? null : cur)), 2000);
    });
  };

  const toggleSort = () =>
    setSortState((prev) =>
      prev === null
        ? { dir: "asc" }
        : prev.dir === "asc"
          ? { dir: "desc" }
          : null,
    );

  const sortDir: SortDir = sortState === null ? null : sortState.dir;

  const sortedChanges = sortState
    ? [...changes].sort((a, b) => {
        const dir = sortState.dir === "asc" ? 1 : -1;
        return (
          dir *
          (new Date(a.createdTimestamp).getTime() -
            new Date(b.createdTimestamp).getTime())
        );
      })
    : changes;

  if (changes.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-list-ol fs-1 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">No DNS changes found</p>
        <small className="text-muted">
          Create a new batch change to get started.
        </small>
      </div>
    );
  }

  return (
    <div
      className="vds-zones-table-wrap"
      style={{
        maxHeight: "calc(100vh - 260px)",
        overflowY: "auto",
        overflowX: "auto",
      }}
    >
      <style>{COPY_KEYFRAMES}</style>
      <table className="vds-zones-table">
        <thead>
          <tr>
            <th>ID</th>
            {ignoreAccess && <th>SUBMITTER</th>}
            <th>CHANGES</th>
            <th>STATUS</th>
            <th>DESCRIPTION</th>
            <th
              onClick={toggleSort}
              style={{
                cursor: "pointer",
                userSelect: "none",
                whiteSpace: "nowrap",
              }}
            >
              SUBMITTED <SortArrow dir={sortDir} />
            </th>
            <th>ACTIONS</th>
          </tr>
        </thead>
        <tbody>
          {sortedChanges.map((change) => {
            // Cancel is owner-only — the API rejects cancel attempts from
            // anyone other than the original submitter, even super users.
            const isPendingReview =
              change.approvalStatus === "PendingReview" ||
              change.status === "PendingReview";
            const isOwner =
              Boolean(currentUserId) && change.userId === currentUserId;
            const canCancel = isPendingReview && isOwner;

            return (
              <tr key={change.id}>
                {/* Full UUID is preserved in the title attr and available via
                    the clipboard button; showing only 8 chars keeps the table
                    readable without wrapping on smaller viewports. */}
                <td style={{ whiteSpace: "nowrap" }}>
                  <div className="d-flex align-items-center gap-1">
                    <Link
                      to={`/dnschanges/${change.id}`}
                      state={
                        fromTab || currentPaging
                          ? { fromTab, paging: currentPaging }
                          : undefined
                      }
                      className="text-decoration-none small fw-semibold vds-table-primary"
                      title={change.id}
                      style={{ whiteSpace: "nowrap", fontFamily: "inherit" }}
                    >
                      {change.id.substring(0, 8)}
                      {"\u2026"}
                    </Link>
                    <button
                      type="button"
                      className="btn btn-link btn-sm p-0"
                      title="Copy ID"
                      style={{
                        lineHeight: 1,
                        fontSize: "0.78rem",
                        color: copiedId === change.id ? "#16a34a" : "#94a3b8",
                        transition: "color 0.15s",
                      }}
                      onClick={() => handleCopyId(change.id)}
                    >
                      {copiedId === change.id ? (
                        <i
                          key="check"
                          className="bi bi-check2"
                          style={{
                            display: "inline-block",
                            animation:
                              "vdsCopiedCheck 0.35s cubic-bezier(0.175,0.885,0.32,1.275) forwards",
                          }}
                        />
                      ) : (
                        <i key="copy" className="bi bi-copy" />
                      )}
                    </button>
                  </div>
                </td>
                {ignoreAccess && (
                  <td style={{ whiteSpace: "nowrap" }}>
                    <span className="d-flex align-items-center gap-1">
                      <i
                        className="bi bi-person-circle vds-table-secondary"
                        style={{ fontSize: "0.9rem" }}
                      />
                      <span className="small vds-table-secondary">
                        {change.userName}
                      </span>
                    </span>
                  </td>
                )}
                <td className="vds-table-secondary small">
                  {change.totalChanges}
                </td>
                <td
                  style={{
                    overflowWrap: "break-word",
                    maxWidth: 250,
                    wordBreak: "break-word",
                    display: "table-cell",
                  }}
                >
                  <span
                    className={`vds-status-text ${changeStatusClass(
                      change.status,
                    ).replace("vds-status-badge", "vds-status-text")}`}
                    style={{ display: "inline-block", maxWidth: "100%" }}
                  >
                    {changeStatusLabel(change.status)}
                  </span>
                </td>
                <td
                  className="vds-table-secondary small"
                  style={{ overflowWrap: "break-word", maxWidth: 250 }}
                >
                  {change.comments || (
                    <span className="vds-table-placeholder">{"\u2014"}</span>
                  )}
                </td>
                <td
                  className="vds-table-secondary small"
                  style={{
                    overflowWrap: "break-word",
                    maxWidth: 250,
                  }}
                >
                  {formatDateTime(change.createdTimestamp)}
                </td>
                <td>
                  <div className="d-flex gap-1 flex-nowrap">
                    <Link
                      to={`/dnschanges/${change.id}`}
                      state={
                        fromTab || currentPaging
                          ? { fromTab, paging: currentPaging }
                          : undefined
                      }
                      className="vds-ubtn vds-ubtn--secondary vds-ubtn--sm"
                      title="View"
                    >
                      <i className="bi bi-eye" />
                      <span>View</span>
                    </Link>
                    {canCancel && onCancel && (
                      <button
                        type="button"
                        className="vds-ubtn vds-ubtn--danger-outline vds-ubtn--sm"
                        title="Cancel"
                        onClick={() => onCancel(change)}
                      >
                        <i className="bi bi-x-circle" />
                        <span>Cancel</span>
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
