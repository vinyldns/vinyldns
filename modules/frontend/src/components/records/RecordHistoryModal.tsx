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

import React, { useCallback, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { recordsService } from "../../services/recordsService";
import { copyToClipboard } from "../../utils/dateUtils";
import { Pagination } from "../common/Pagination";
import { LoadingSpinner } from "../common/LoadingSpinner";

interface RecordHistoryModalProps {
  record: any;
  onClose: () => void;
}

/**
 * Maps an API change type (Create/Delete/Update) to a CSS modifier class for
 * the change-type badge. Normalized to lowercase to tolerate mixed-case values
 * that may arrive from different API versions.
 */
export function changeTypeBadgeClass(type: string): string {
  const t = String(type ?? "").toLowerCase();
  if (t === "create") return "vds-change-type-badge--add";
  if (t === "delete") return "vds-change-type-badge--delete";
  if (t === "update") return "vds-change-type-badge--update";
  return "vds-change-type-badge--default";
}

export function statusBadgeClass(status: string): string {
  if (status === "Complete") return "vds-status-badge--success";
  if (status === "Failed") return "vds-status-badge--danger";
  return "vds-status-badge--warning";
}

/** Detects if dark theme is currently active */
function isDarkTheme(): boolean {
  return (
    document.documentElement.getAttribute("data-vds-theme") === "dark" ||
    window.matchMedia("(prefers-color-scheme: dark)").matches
  );
}

function historyStatusStyle(status: string): React.CSSProperties {
  const isDark = isDarkTheme();
  if (status === "Complete")
    return {
      background: isDark ? "rgba(6,78,59,0.25)" : "#ecfdf5",
      color: isDark ? "#34d399" : "#065f46",
      border: isDark ? "1px solid rgba(52,211,153,0.3)" : "1px solid #a7f3d0",
      boxShadow: isDark
        ? "0 1px 2px rgba(0,0,0,0.2)"
        : "0 1px 2px rgba(0,0,0,0.06)",
      fontWeight: 600,
    };
  if (status === "Failed")
    return {
      background: isDark ? "rgba(153,27,27,0.25)" : "#fef2f2",
      color: isDark ? "#f87171" : "#991b1b",
      border: isDark ? "1px solid rgba(248,113,113,0.3)" : "1px solid #fecaca",
      boxShadow: isDark
        ? "0 1px 2px rgba(0,0,0,0.2)"
        : "0 1px 2px rgba(0,0,0,0.06)",
      fontWeight: 600,
    };
  return {
    background: isDark ? "rgba(146,64,14,0.25)" : "#fffbeb",
    color: isDark ? "#fbbf24" : "#92400e",
    border: isDark ? "1px solid rgba(251,191,36,0.3)" : "1px solid #fde68a",
    boxShadow: isDark
      ? "0 1px 2px rgba(0,0,0,0.2)"
      : "0 1px 2px rgba(0,0,0,0.06)",
    fontWeight: 600,
  };
}

function changeTypeStyle(type: string): React.CSSProperties {
  const isDark = isDarkTheme();
  const t = String(type ?? "").toLowerCase();

  // create = Complete (green)
  if (t === "create")
    return {
      background: isDark ? "rgba(6,78,59,0.25)" : "#ecfdf5",
      color: isDark ? "#34d399" : "#065f46",
      border: isDark ? "1px solid rgba(52,211,153,0.3)" : "1px solid #a7f3d0",
      boxShadow: isDark
        ? "0 1px 2px rgba(0,0,0,0.2)"
        : "0 1px 2px rgba(0,0,0,0.06)",
      fontWeight: 600,
    };
  // delete = Failed (red)
  if (t === "delete")
    return {
      background: isDark ? "rgba(153,27,27,0.25)" : "#fef2f2",
      color: isDark ? "#f87171" : "#991b1b",
      border: isDark ? "1px solid rgba(248,113,113,0.3)" : "1px solid #fecaca",
      boxShadow: isDark
        ? "0 1px 2px rgba(0,0,0,0.2)"
        : "0 1px 2px rgba(0,0,0,0.06)",
      fontWeight: 600,
    };
  // update = Warning (yellow/amber)
  if (t === "update")
    return {
      background: isDark ? "rgba(146,64,14,0.25)" : "#fffbeb",
      color: isDark ? "#fbbf24" : "#92400e",
      border: isDark ? "1px solid rgba(251,191,36,0.3)" : "1px solid #fde68a",
      boxShadow: isDark
        ? "0 1px 2px rgba(0,0,0,0.2)"
        : "0 1px 2px rgba(0,0,0,0.06)",
      fontWeight: 600,
    };
  // default = info (blue)
  return {
    background: isDark ? "rgba(37,99,235,0.25)" : "#dbeafe",
    color: isDark ? "#60a5fa" : "#1e40af",
    border: isDark ? "1px solid rgba(96,165,250,0.3)" : "1px solid #bfdbfe",
    boxShadow: isDark
      ? "0 1px 2px rgba(0,0,0,0.2)"
      : "0 1px 2px rgba(0,0,0,0.06)",
    fontWeight: 600,
  };
}

/**
 * Small presentational component that generates an avatar from the first two
 * initials of a username. Splits on common username delimiters (dots, dashes,
 * underscores, @ for email-style usernames) so that "john.doe" becomes "JD".
 */
function UserAvatar({ name }: { name: string }) {
  const initials =
    name
      .split(/[._\-@]+/)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? "")
      .join("") || name.slice(0, 2).toUpperCase();
  return (
    <span
      className="vds-zone-avatar"
      style={{
        width: 26,
        height: 26,
        fontSize: "0.58rem",
        borderRadius: 6,
        flexShrink: 0,
      }}
    >
      {initials}
    </span>
  );
}

/** Formats a timestamp as 'Sun May 12 2019 08:59:49' (day dow mon date year time). */
export function formatHistoryTime(ts: string): string {
  return new Date(ts).toString().split(" ").slice(0, 5).join(" ");
}

/**
 * Extracts human-readable record values from a recordSet's records array,
 * normalising across all common DNS record types.
 */
export function formatRecordValues(recordSet: any): string[] {
  if (!recordSet?.records?.length) return [];
  return (recordSet.records as any[]).map((r) => {
    if (r.address != null) return String(r.address);
    if (r.cname != null) return String(r.cname);
    if (r.ptrdname != null) return String(r.ptrdname);
    if (r.exchange != null) return `${r.preference ?? 0} ${r.exchange}`;
    if (r.nsdname != null) return String(r.nsdname);
    if (r.text != null) return String(r.text);
    if (r.target != null)
      return `${r.priority ?? 0} ${r.weight ?? 0} ${r.port ?? 0} ${r.target}`;
    if (r.mname != null) return `${r.mname} ${r.rname}`;
    return Object.entries(r)
      .filter(([, v]) => v != null)
      .map(([k, v]) => `${k}: ${v}`)
      .join(", ");
  });
}

/**
 * Modal that displays the audit change history for a single record set.
 *
 * Pagination is managed client-side using a "page stack" pattern: each
 * page's `nextId` cursor is pushed onto `pageStack` as the user advances,
 * and going back simply decrements `pageIdx` to replay the previous cursor.
 * This avoids storing a flat list of all history in memory and works cleanly
 * with React Query's cursor-keyed caching.
 *
 * @param record  - The record set to show history for. Expects at minimum
 *                  `id`, `zoneId`, `fqdn` (or `name`), and `type`.
 * @param onClose - Called when the modal should be dismissed.
 */
export function RecordHistoryModal({
  record,
  onClose,
}: RecordHistoryModalProps) {
  // pageStack holds the sequence of startFrom cursors navigated so far.
  // pageStack[0] is always `undefined` (first page, no cursor).
  const [pageStack, setPageStack] = useState<(string | undefined)[]>([
    undefined,
  ]);
  const [pageIdx, setPageIdx] = useState(0);
  // `copied` tracks which ID was just copied so the button can briefly show a
  // checkmark before reverting — without needing a separate boolean per field.
  const [copied, setCopied] = useState<"record" | "zone" | null>(null);

  // INFO detail modal state (click-to-open)
  const [selectedInfo, setSelectedInfo] = useState<any | null>(null);
  // tracks which "View recordset" sections are expanded inside the detail modal
  const [expandedViews, setExpandedViews] = useState<Set<string>>(new Set());
  // tracks which copy button in the info modal just fired (shows animated checkmark)
  const [copiedPop, setCopiedPop] = useState<string | null>(null);
  // force re-render when theme changes
  const [, setThemeRefresh] = useState(false);

  const copyPop = useCallback((key: string, val: string) => {
    void navigator.clipboard.writeText(val);
    setCopiedPop(key);
    setTimeout(() => setCopiedPop(null), 2000);
  }, []);

  const handleInfoClick = useCallback((change: any) => {
    setExpandedViews(new Set(["new", "old"]));
    setSelectedInfo(change);
  }, []);

  const handleInfoClose = useCallback(() => {
    setSelectedInfo(null);
  }, []);

  const fqdn = String(record.fqdn ?? record.name ?? "");
  const cursor = pageStack[pageIdx];

  // 30 s staleTime avoids hitting the API on every modal open when the user
  // closes and reopens history for the same record within the same session.
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["recordHistory", record.id, record.zoneId, cursor],
    queryFn: async () => {
      console.debug("[RecordHistoryModal] fetching history with", {
        zoneId: record.zoneId,
        fqdn,
        type: record.type,
        cursor,
      });
      const res = await recordsService.listRecordSetChangeHistory(
        String(record.zoneId ?? ""),
        100,
        cursor,
        fqdn,
        String(record.type ?? ""),
      );
      console.debug("[RecordHistoryModal] raw response", res.data);
      return res.data;
    },
    staleTime: 30 * 1000,
  });

  const changes: any[] = (data as any)?.recordSetChanges ?? [];
  const hasMore: boolean = (data as any)?.nextId != null;
  const hasPrev = pageIdx > 0;

  // Close on Escape so the modal is keyboard accessible without needing a
  // visible close button to be in focus.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  // Lock body scroll while the modal is open to prevent the background page
  // from scrolling independently of the modal content.
  useEffect(() => {
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, []);

  // Listen for theme changes and re-render to apply dark mode status badge colors
  useEffect(() => {
    const observer = new MutationObserver(() => {
      setThemeRefresh((prev) => !prev);
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-vds-theme"],
    });
    return () => observer.disconnect();
  }, []);

  // Advance to the next page by pushing the next cursor onto the stack.
  // The API returns `nextId` as an integer; convert to string for the URL
  // query param since `startFrom` is passed as a string in the service layer.
  const handleNext = () => {
    const rawNextId = (data as any)?.nextId;
    const nextId: string | undefined =
      rawNextId != null ? String(rawNextId) : undefined;
    if (!nextId) return;
    const newStack = [...pageStack.slice(0, pageIdx + 1), nextId];
    setPageStack(newStack);
    setPageIdx(pageIdx + 1);
  };

  const handlePrev = () => {
    if (pageIdx > 0) setPageIdx(pageIdx - 1);
  };

  // Briefly shows a checkmark on the copy button after a successful write to
  // give the user clear visual confirmation without a toast notification.
  const handleCopy = async (type: "record" | "zone") => {
    const val = type === "record" ? record.id : record.zoneId;
    if (!val) return;
    await copyToClipboard(String(val));
    setCopied(type);
    setTimeout(() => setCopied(null), 2000);
  };

  return (
    <>
      <div
        className="modal d-block rhm-backdrop"
        style={{
          backgroundColor: "rgba(13,27,62,0.55)",
          zIndex: 1050,
          backdropFilter: "blur(2px)",
        }}
        onMouseDown={(e) => {
          if (e.target === e.currentTarget) onClose();
        }}
      >
        <div
          className="modal-dialog modal-dialog-scrollable modal-dialog-centered rhm-dialog"
          style={{ maxWidth: "95vw", width: "95vw", margin: "0 auto" }}
        >
          <div
            className="modal-content border-0 rhm-content"
            style={{
              borderRadius: 14,
              overflow: "hidden",
              boxShadow:
                "0 24px 64px rgba(13,27,62,0.22), 0 4px 16px rgba(0,0,0,0.1)",
              border: "1px solid #dde4ef",
            }}
          >
            {/* ── Header — matches vds-page-header gradient ── */}
            <div
              className="rhm-header"
              style={{
                background: "linear-gradient(135deg, #f0f4fa 0%, #ffffff 100%)",
                borderBottom: "1px solid #dde4ef",
                padding: "20px 24px 0",
              }}
            >
              <div className="d-flex align-items-start gap-3 pb-3">
                {/* Icon — matches vds-page-header__icon style */}
                <div
                  style={{
                    width: 44,
                    height: 44,
                    borderRadius: 10,
                    background: "linear-gradient(90deg, #1e5fa8, #0d1b3e)",
                    boxShadow: "0 4px 12px rgba(13,27,62,0.35)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  <i
                    className="bi bi-clock-history"
                    style={{ color: "#fff", fontSize: "1.15rem" }}
                  />
                </div>

                {/* Title + subtitle */}
                <div className="flex-grow-1 min-w-0">
                  <h5
                    className="mb-1 fw-bold"
                    style={{
                      color: "#0d1b2a",
                      fontSize: "1rem",
                      letterSpacing: "-0.01em",
                    }}
                  >
                    Record Change History
                  </h5>
                  <div className="d-flex align-items-center gap-2 flex-wrap">
                    <span
                      className="small rhm-fqdn"
                      style={{ color: "#475569" }}
                    >
                      {fqdn}
                    </span>
                    {record.type && (
                      <span
                        className="vds-type-badge"
                        style={{ fontSize: "0.68rem", padding: "1px 7px" }}
                      >
                        {String(record.type)}
                      </span>
                    )}
                  </div>
                </div>

                {/* Refresh button */}
                <button
                  type="button"
                  className="rhm-header-btn"
                  aria-label="Refresh"
                  title="Refresh history"
                  onClick={() => void refetch()}
                  disabled={isLoading}
                >
                  <i
                    className={`bi bi-arrow-clockwise rhm-refresh-icon${isLoading ? " rhm-refresh-icon-loading" : ""}`}
                  />
                </button>

                {/* Close button */}
                <button
                  type="button"
                  className="rhm-header-btn"
                  aria-label="Close"
                  onClick={onClose}
                >
                  <i className="bi bi-x-lg rhm-close-icon" />
                </button>
              </div>

              {/* ID chips — nestled at the bottom of the header above the border */}
              {(record.id || record.zoneId) && (
                <div
                  className="d-flex gap-3 flex-wrap pb-3 rhm-chips-row"
                  style={{
                    borderTop: "1px solid rgba(13,27,62,0.08)",
                    paddingTop: 10,
                  }}
                >
                  {(
                    [
                      ["Record ID", record.id, "record", "bi-fingerprint"],
                      ["Zone ID", record.zoneId, "zone", "bi-globe2"],
                    ] as [string, string, "record" | "zone", string][]
                  ).map(([label, value, key, icon]) =>
                    value ? (
                      <div
                        key={key}
                        className={`rhm-id-card rhm-id-card--${key}`}
                      >
                        {/* Gradient icon bubble */}
                        <div className="rhm-id-card__icon-wrap">
                          <i className={`bi ${icon}`} />
                        </div>
                        {/* Stacked label + value */}
                        <div className="rhm-id-card__text">
                          <span className="rhm-id-card__label">{label}</span>
                          <code
                            className="rhm-id-card__value"
                            title={String(value)}
                          >
                            {String(value)}
                          </code>
                        </div>
                        {/* Copy button — unchanged */}
                        <div className="rhm-id-card__copy">
                          <button
                            type="button"
                            className="rhm-copy-btn"
                            title={`Copy ${label}`}
                            style={{
                              color: copied === key ? "#16a34a" : "#94a3b8",
                              fontSize: "0.78rem",
                            }}
                            onClick={() => void handleCopy(key)}
                          >
                            {copied === key ? (
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
                      </div>
                    ) : null,
                  )}
                </div>
              )}
            </div>

            {/* ── Body ── */}
            <div className="modal-body p-0">
              {isError ? (
                <div className="vds-empty-state py-5">
                  <i
                    className="bi bi-exclamation-triangle fs-1 mb-2"
                    style={{ opacity: 0.5, color: "#dc2626" }}
                  />
                  <p className="mb-0 fw-semibold" style={{ color: "#dc2626" }}>
                    Failed to load history
                  </p>
                  <small className="text-muted">
                    {String(
                      (error as any)?.response?.data ??
                        (error as any)?.message ??
                        "Unknown error",
                    )}
                  </small>
                  <small className="text-muted d-block mt-1">
                    zoneId: {String(record.zoneId ?? "(none)")}
                    &nbsp;·&nbsp;fqdn: {fqdn}&nbsp;·&nbsp;type:{" "}
                    {String(record.type ?? "(none)")}
                  </small>
                </div>
              ) : isLoading ? (
                <LoadingSpinner />
              ) : changes.length === 0 ? (
                <div className="vds-empty-state py-5">
                  <i
                    className="bi bi-clock-history fs-1 mb-2"
                    style={{ opacity: 0.35 }}
                  />
                  <p className="mb-0 fw-semibold">No change history</p>
                  <small className="text-muted">
                    No recorded changes found for this record set.
                  </small>
                </div>
              ) : (
                <>
                  {(hasPrev || hasMore) && (
                    <div className="px-3">
                      <Pagination
                        onPrev={handlePrev}
                        onNext={handleNext}
                        prevEnabled={hasPrev}
                        nextEnabled={hasMore}
                        rangeLabel={`${pageIdx * 100 + 1}–${pageIdx * 100 + changes.length}`}
                      />
                    </div>
                  )}
                  <div
                    className="vds-zones-table-wrap"
                    style={{
                      borderRadius: 0,
                      boxShadow: "none",
                      border: "none",
                      overflow: "auto",
                      maxHeight: "55vh",
                    }}
                  >
                    <table className="vds-zones-table">
                      <thead>
                        <tr>
                          <th style={{ whiteSpace: "nowrap" }}>TIME</th>
                          <th>RECORDSET NAME</th>
                          <th style={{ whiteSpace: "nowrap" }}>
                            RECORDSET TYPE
                          </th>
                          <th style={{ whiteSpace: "nowrap" }}>CHANGE TYPE</th>
                          <th>USER</th>
                          <th>STATUS</th>
                          <th style={{ width: 44, textAlign: "center" }}>
                            INFO
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {changes.map((change: any, idx: number) => {
                          const cType = String(change.changeType ?? "");
                          const status = String(change.status ?? "");
                          return (
                            <tr key={idx} className="rhm-row">
                              <td
                                className="vds-table-secondary small"
                                style={{ whiteSpace: "nowrap" }}
                              >
                                {change.created
                                  ? formatHistoryTime(String(change.created))
                                  : "—"}
                              </td>
                              <td className="vds-table-primary small fw-medium">
                                {String(change.recordSet?.name ?? "—")}
                              </td>
                              <td>
                                {change.recordSet?.type ? (
                                  <span
                                    className="vds-type-badge"
                                    style={{
                                      fontSize: "0.68rem",
                                      padding: "1px 7px",
                                    }}
                                  >
                                    {String(change.recordSet.type)}
                                  </span>
                                ) : (
                                  "—"
                                )}
                              </td>
                              <td>
                                <span
                                  className={`vds-change-type-badge ${changeTypeBadgeClass(cType)}`}
                                  style={changeTypeStyle(cType)}
                                >
                                  {cType || "—"}
                                </span>
                              </td>
                              <td>
                                <div className="d-flex align-items-center gap-2">
                                  <UserAvatar
                                    name={String(change.userName ?? "S")}
                                  />
                                  <span
                                    className="vds-table-primary small fw-medium text-truncate"
                                    style={{ maxWidth: 140 }}
                                  >
                                    {String(change.userName ?? "System")}
                                  </span>
                                </div>
                              </td>
                              <td>
                                <span
                                  className={`vds-status-badge ${statusBadgeClass(status)}`}
                                  style={historyStatusStyle(status)}
                                >
                                  {status || "—"}
                                </span>
                              </td>
                              <td style={{ width: 44, textAlign: "center" }}>
                                <button
                                  type="button"
                                  className="rhm-info-trigger"
                                  title="View change details"
                                  onClick={() => handleInfoClick(change)}
                                >
                                  <i className="bi bi-info-circle-fill" />
                                </button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  {(hasPrev || hasMore) && (
                    <div className="px-3">
                      <Pagination
                        onPrev={handlePrev}
                        onNext={handleNext}
                        prevEnabled={hasPrev}
                        nextEnabled={hasMore}
                        rangeLabel={`${pageIdx * 100 + 1}–${pageIdx * 100 + changes.length}`}
                      />
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      </div>
      {/* ── INFO detail modal ── */}
      {selectedInfo && (
        <div
          className="modal d-block"
          style={{
            backgroundColor: "rgba(13,27,62,0.45)",
            zIndex: 1060,
            backdropFilter: "blur(2px)",
          }}
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) handleInfoClose();
          }}
        >
          <div
            className="modal-dialog modal-dialog-centered modal-dialog-scrollable"
            style={{ maxWidth: "min(860px, 95vw)", margin: "0 auto" }}
          >
            <div
              className="modal-content border-0"
              style={{
                borderRadius: 14,
                overflow: "hidden",
                boxShadow:
                  "0 24px 64px rgba(13,27,62,0.25), 0 4px 16px rgba(0,0,0,0.12)",
                border: "1px solid #dde4ef",
              }}
            >
              {/* Detail modal header */}
              <div
                className="rhm-pop-header"
                style={{
                  padding: "14px 18px",
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                }}
              >
                <span
                  className={`vds-change-type-badge ${changeTypeBadgeClass(String(selectedInfo.changeType ?? ""))}`}
                  style={{
                    fontSize: "0.65rem",
                    ...changeTypeStyle(String(selectedInfo.changeType ?? "")),
                  }}
                >
                  {String(selectedInfo.changeType ?? "—")}
                </span>
                {selectedInfo.recordSet?.type && (
                  <span
                    className="vds-type-badge"
                    style={{ fontSize: "0.65rem", padding: "1px 6px" }}
                  >
                    {String(selectedInfo.recordSet.type)}
                  </span>
                )}
                {selectedInfo.recordSet?.status && (
                  <span
                    className={`vds-status-badge ${statusBadgeClass(String(selectedInfo.recordSet.status))}`}
                    style={{ fontSize: "0.62rem", padding: "1px 6px" }}
                  >
                    {String(selectedInfo.recordSet.status)}
                  </span>
                )}
                {selectedInfo.recordSet?.ttl != null && (
                  <span
                    style={{
                      fontSize: "0.65rem",
                      color: "#64748b",
                      letterSpacing: "0.02em",
                    }}
                  >
                    TTL&nbsp;{selectedInfo.recordSet.ttl}s
                  </span>
                )}
                <button
                  type="button"
                  className="rhm-header-btn"
                  onClick={handleInfoClose}
                  aria-label="Close"
                  title="Close"
                  style={{ marginLeft: "auto" }}
                >
                  <i className="bi bi-x-lg rhm-close-icon" />
                </button>
              </div>

              {/* Change metadata: IDs, zone, FQDN */}
              {(() => {
                const zoneName =
                  selectedInfo.zone?.name ?? selectedInfo.recordSet?.zoneName;
                const fqdn = selectedInfo.recordSet?.fqdn;
                const changeId = selectedInfo.id;
                const rsId = selectedInfo.recordSet?.id;
                const rows: [string, string, string?][] = [];
                if (fqdn) rows.push(["FQDN", String(fqdn)]);
                if (zoneName) rows.push(["Zone", String(zoneName)]);
                if (changeId)
                  rows.push(["Change ID", String(changeId), "change"]);
                if (rsId) rows.push(["Record Set ID", String(rsId), "rs"]);
                if (!rows.length) return null;
                return (
                  <div className="rhm-pop-section rhm-pop-section--meta">
                    <div className="rhm-pop-meta-grid">
                      {rows.map(([key, val, copyKey]) => (
                        <React.Fragment key={key}>
                          <span className="rhm-pop-meta-key">{key}</span>
                          <span className="rhm-pop-meta-val">
                            {val}
                            {copyKey && (
                              <button
                                type="button"
                                className="rhm-pop-copy-mini"
                                title={`Copy ${key}`}
                                style={{
                                  color:
                                    copiedPop === copyKey
                                      ? "#16a34a"
                                      : undefined,
                                }}
                                onClick={() => copyPop(copyKey, val)}
                              >
                                {copiedPop === copyKey ? (
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
                            )}
                          </span>
                        </React.Fragment>
                      ))}
                    </div>
                  </div>
                );
              })()}

              {/* ── View recordset actions (Angular parity) ── */}
              {(() => {
                const cType = String(selectedInfo.changeType ?? "");
                const oldRs = selectedInfo.updates as any;
                const newRs = selectedInfo.recordSet;

                // Inline record detail renderer
                const RsDetail = ({
                  rs,
                  variant,
                }: {
                  rs: any;
                  variant: "new" | "old";
                }) => {
                  if (!rs)
                    return (
                      <span
                        style={{
                          fontSize: "0.72rem",
                          opacity: 0.5,
                          fontStyle: "italic",
                        }}
                      >
                        No data available
                      </span>
                    );
                  const vals = formatRecordValues(rs);
                  const fields: [string, string][] = [];
                  if (rs.name) fields.push(["Name", String(rs.name)]);
                  if (rs.fqdn) fields.push(["FQDN", String(rs.fqdn)]);
                  if (rs.type) fields.push(["Type", String(rs.type)]);
                  if (rs.ttl != null)
                    fields.push(["TTL", String(rs.ttl) + "s"]);
                  if (rs.status) fields.push(["Status", String(rs.status)]);
                  return (
                    <div
                      className={`rhm-pop-rs-detail${variant === "old" ? " rhm-pop-rs-detail--old" : ""}`}
                    >
                      {fields.map(([k, v]) => (
                        <div key={k} className="rhm-pop-rs-field">
                          <span className="rhm-pop-rs-field-key">{k}</span>
                          <span className="rhm-pop-rs-field-val">{v}</span>
                        </div>
                      ))}
                      {vals.length > 0 && (
                        <>
                          <div className="rhm-pop-rs-divider" />
                          {vals.map((v, i) => (
                            <div key={i} className="rhm-pop-record-row">
                              <span
                                className={`rhm-pop-dot${variant === "old" ? " rhm-pop-dot--prev" : ""}`}
                              />
                              <span className="rhm-pop-rs-record-val">{v}</span>
                            </div>
                          ))}
                        </>
                      )}
                    </div>
                  );
                };

                type ViewAction = {
                  label: string;
                  key: string;
                  rs: any;
                  variant: "new" | "old";
                };
                const actions: ViewAction[] = [];
                if (cType === "Create") {
                  actions.push({
                    label: "View created recordset",
                    key: "new",
                    rs: newRs,
                    variant: "new",
                  });
                } else if (cType === "Delete") {
                  actions.push({
                    label: "View deleted recordset",
                    key: "new",
                    rs: newRs,
                    variant: "new",
                  });
                } else if (cType === "Update") {
                  actions.push({
                    label: "View new recordset",
                    key: "new",
                    rs: newRs,
                    variant: "new",
                  });
                  actions.push({
                    label: "View old recordset",
                    key: "old",
                    rs: oldRs,
                    variant: "old",
                  });
                }
                if (!actions.length) return null;

                return (
                  <div
                    className="rhm-pop-section"
                    style={{ paddingTop: 6, paddingBottom: 6 }}
                  >
                    {actions.map(({ label, key, rs, variant }) => (
                      <div key={key}>
                        <button
                          type="button"
                          className={`rhm-pop-view-btn${variant === "old" ? " rhm-pop-view-btn--old" : ""}`}
                          onClick={() =>
                            setExpandedViews((prev) => {
                              const next = new Set(prev);
                              if (next.has(key)) next.delete(key);
                              else next.add(key);
                              return next;
                            })
                          }
                        >
                          <i
                            className={`bi bi-chevron-${expandedViews.has(key) ? "down" : "right"}`}
                            style={{
                              fontSize: "0.6rem",
                              transition: "transform 0.15s",
                            }}
                          />
                          {label}
                        </button>
                        {expandedViews.has(key) && (
                          <RsDetail rs={rs} variant={variant} />
                        )}
                      </div>
                    ))}
                  </div>
                );
              })()}

              {/* Batch change IDs — shown when this change was part of a batch */}
              {(() => {
                const ids: string[] = selectedInfo.singleBatchChangeIds ?? [];
                if (!ids.length) return null;
                return (
                  <div className="rhm-pop-section rhm-pop-section--batch">
                    <div className="rhm-pop-label rhm-pop-label--batch">
                      <i className="bi bi-stack me-1" />
                      Batch Change IDs ({ids.length})
                    </div>
                    {ids.map((id: string, i: number) => (
                      <div key={i} className="rhm-pop-record-row">
                        <span
                          className="rhm-pop-dot"
                          style={{ background: "#059669" }}
                        />
                        <span
                          className="rhm-pop-val"
                          style={{ fontSize: "0.7rem" }}
                        >
                          {id}
                          <button
                            type="button"
                            className="rhm-pop-copy-mini"
                            title="Copy batch change ID"
                            style={{
                              color:
                                copiedPop === `batch-${i}`
                                  ? "#16a34a"
                                  : undefined,
                            }}
                            onClick={() => copyPop(`batch-${i}`, id)}
                          >
                            {copiedPop === `batch-${i}` ? (
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
                        </span>
                      </div>
                    ))}
                  </div>
                );
              })()}

              {/* System message */}
              {selectedInfo.systemMessage && (
                <div className="rhm-pop-section rhm-pop-section--msg">
                  <div className="rhm-pop-label rhm-pop-label--msg">
                    <i className="bi bi-exclamation-circle me-1" />
                    Message
                  </div>
                  <div className="rhm-pop-val rhm-pop-val--msg">
                    {String(selectedInfo.systemMessage)}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
