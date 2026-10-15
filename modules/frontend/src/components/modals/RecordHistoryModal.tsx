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
import { log } from "console";

interface RecordHistoryModalProps {
  record: any;
  onClose: () => void;
}

/**
 * Maps an API change type (Create/Delete/Update) to a CSS modifier class for
 * the change-type badge. Normalized to lowercase to tolerate mixed-case values
 * that may arrive from different API versions.
 */
export function changeTypeStatusClass(type: string): string {
  const t = String(type ?? "").toLowerCase();
  if (t === "create") return "vds-status-text--success";
  if (t === "delete") return "vds-status-text--danger";
  if (t === "update") return "vds-status-text--warning";
  return "vds-status-text--secondary";
}

export function changeTypeBadgeClass(type: string): string {
  const t = String(type ?? "").toLowerCase();
  if (t === "create") return "vds-change-type-badge--add";
  if (t === "delete") return "vds-change-type-badge--delete";
  if (t === "update") return "vds-change-type-badge--update";
  return "vds-change-type-badge--default";
}

export function statusBadgeClass(status: string): string {
  if (status === "Complete") return "vds-status-text--success";
  if (status === "Failed") return "vds-status-text--danger";
  return "vds-status-text--warning";
}

/** Formats a timestamp as two lines: 'Jun 25, 2021' and '5:10 AM'. */
export function formatHistoryTime(ts: string): string {
  const date = new Date(ts);
  const datePart = date.toLocaleString("en-us", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  const timePart = date.toLocaleString("en-us", {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
  return `${datePart}\n${timePart}`;
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
  const [pageStack, setPageStack] = useState<(string | undefined)[]>([
    undefined,
  ]);
  const [pageIdx, setPageIdx] = useState(0);
  const [copied, setCopied] = useState<"record" | "zone" | null>(null);
  const [selectedInfo, setSelectedInfo] = useState<any | null>(null);
  const [expandedViews, setExpandedViews] = useState<Set<string>>(new Set());
  const [copiedPop, setCopiedPop] = useState<string | null>(null);
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

  const { data, isFetching, isError, error, refetch } = useQuery({
    queryKey: ["recordHistory", record.id, record.zoneId, cursor],
    queryFn: async () => {
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

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  useEffect(() => {
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, []);

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
        className="modal d-block rhm-backdrop vds-dark-modal-backdrop"
        onMouseDown={(e) => {
          if (e.target === e.currentTarget) onClose();
        }}
      >
        <div
          className="modal-dialog modal-dialog-scrollable modal-dialog-centered rhm-dialog"
          style={{ maxWidth: "75vw", width: "95vw", margin: "0 auto" }}
        >
          <div className="modal-content border-0 rhm-content vds-dark-modal-content">
            <div className="rhm-header vds-dark-modal-header">
              <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
                <div className="d-flex align-items-center gap-3 min-w-0">
                  <div className="min-w-0">
                    <h5 className="m-0 fw-semibold text-white vds-dark-modal-title">
                      Record Change History
                    </h5>
                    <div className="d-flex align-items-center gap-2 mt-1 flex-wrap">
                      <span className="small font-monospace px-2 vds-dark-modal-badge badge-fqdn">
                        {fqdn}
                      </span>
                      {record.type && (
                        <span className="vds-dark-modal-badge badge-type">
                          {String(record.type)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="d-flex align-items-center gap-2 flex-shrink-0">
                  <button
                    type="button"
                    aria-label="Refresh"
                    title="Refresh history"
                    onClick={() => void refetch()}
                    disabled={isFetching}
                    className="rhm-header-btn"
                  >
                    <i
                      className={`rhm-refresh-icon bi bi-arrow-clockwise ${isFetching ? "is-spinning" : ""}`}
                      style={{ fontSize: "0.95rem" }}
                    />
                  </button>

                  <button
                    type="button"
                    aria-label="Close"
                    onClick={onClose}
                    className="rhm-header-btn"
                  >
                    <i
                      className="rhm-close-icon bi bi-x-lg"
                      style={{ fontSize: "0.85rem" }}
                    />
                  </button>
                </div>
              </div>
            </div>
            {(record.id || record.zoneId) && (
              <div
                className="d-flex gap-2 px-2 pt-3 justify-content-between"
                style={{
                  borderTop: "1px solid #1c1c22",
                }}
              >
                <div className="d-flex gap-2">
                  {(
                    [
                      ["RECORD ID", record.id, "record", "bi-fingerprint"],
                      ["ZONE ID", record.zoneId, "zone", "bi-globe2"],
                    ] as [string, string, "record" | "zone", string][]
                  ).map(([label, value, key, icon]) =>
                    value ? (
                      <div
                        key={key}
                        className="d-flex align-items-center gap-2 px-2.5 py-1.5 vds-inner-card"
                      >
                        <div
                          className="d-flex flex-row gap-2 min-w-0"
                          style={{ lineHeight: 1.15 }}
                        >
                          <span
                            style={{
                              fontSize: "0.65rem",
                              fontWeight: 600,
                              letterSpacing: "0.05em",
                              color: "#a1a1aa",
                              textTransform: "uppercase",
                            }}
                          >
                            {label} {":"}
                          </span>
                          <code
                            title={String(value)}
                            style={{
                              fontSize: "0.75rem",
                              background: "transparent",
                              padding: 0,
                              whiteSpace: "nowrap",
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              maxWidth: 220,
                            }}
                          >
                            {String(value)}
                          </code>
                        </div>

                        <button
                          type="button"
                          title={`Copy ${label}`}
                          onClick={() => void handleCopy(key)}
                          className={`rhm-copy-btn ${copied === key ? "rhm-copy-btn--copied" : ""}`}
                          onMouseEnter={(e) =>
                            (e.currentTarget.style.opacity = "1")
                          }
                          onMouseLeave={(e) =>
                            (e.currentTarget.style.opacity =
                              copied === key ? "1" : "0.75")
                          }
                        >
                          {copied === key ? (
                            <i className="bi bi-check2" />
                          ) : (
                            <i className="bi bi-copy" />
                          )}
                        </button>
                      </div>
                    ) : null,
                  )}
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
              </div>
            )}
            <div className="modal-body p-2">
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
              ) : isFetching ? (
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
                  <div
                    className="vds-record-history-table-wrapper"
                    style={{
                      overflow: "auto",
                      maxHeight: "55vh",
                    }}
                  >
                    <div className="vds-record-history-table-scroll">
                      <table className="vds-record-history-table-modal">
                        <thead>
                          <tr>
                            <th style={{ whiteSpace: "nowrap" }}>TIME</th>
                            <th>RECORDSET NAME</th>
                            <th style={{ whiteSpace: "nowrap" }}>
                              RECORDSET TYPE
                            </th>
                            <th style={{ whiteSpace: "nowrap" }}>
                              CHANGE TYPE
                            </th>
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
                                <td className="vds-table-secondary small">
                                  {change.created
                                    ? formatHistoryTime(String(change.created))
                                    : "—"}
                                </td>
                                <td className="vds-table-secondary small fw-medium">
                                  {String(change.recordSet?.name ?? "—")}
                                </td>
                                <td>
                                  {change.recordSet?.type ? (
                                    <span
                                      className="vds-table-secondary"
                                      style={{
                                        fontSize: "0.68rem",
                                        padding: "1px 7px",
                                        fontWeight: "600",
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
                                    className={`vds-status-text ${changeTypeStatusClass(cType)}`}
                                  >
                                    {cType || "—"}
                                  </span>
                                </td>
                                <td>
                                  <div className="d-flex align-items-center gap-2">
                                    <span
                                      className="vds-table-secondary small fw-medium text-truncate"
                                      style={{ maxWidth: 140 }}
                                    >
                                      {String(change.userName ?? "System")}
                                    </span>
                                  </div>
                                </td>
                                <td>
                                  <span
                                    className={`vds-status-text ${statusBadgeClass(status)}`}
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
      {selectedInfo && (
        <div
          className="modal d-block"
          style={{
            backgroundColor: "rgba(66, 67, 70, 0.45)",
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
              <div className="rhm-header vds-dark-modal-header">
                <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
                  <div className="d-flex align-items-center gap-3 min-w-0">
                    <div className="min-w-0">
                      <div className="d-flex align-items-center gap-2 mt-1 flex-wrap">
                        <span
                          className={`vds-change-type-badge ${changeTypeBadgeClass(String(selectedInfo.changeType ?? ""))}`}
                          style={{
                            fontSize: "0.65rem",
                          }}
                        >
                          {String(selectedInfo.changeType ?? "—")}
                        </span>
                        {selectedInfo.recordSet?.type && (
                          <span className="vds-dark-modal-badge badge-type">
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
                      </div>
                    </div>
                  </div>

                  <div className="d-flex align-items-center gap-2 flex-shrink-0">
                    <button
                      type="button"
                      aria-label="Close"
                      title="Close"
                      onClick={handleInfoClose}
                      className="rhm-header-btn"
                    >
                      <i
                        className="rhm-close-icon bi bi-x-lg"
                        style={{ fontSize: "0.85rem" }}
                      />
                    </button>
                  </div>
                </div>
              </div>

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

              {(() => {
                const cType = String(selectedInfo.changeType ?? "");
                const oldRs = selectedInfo.updates as any;
                const newRs = selectedInfo.recordSet;

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
