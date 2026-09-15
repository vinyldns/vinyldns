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
import { copyToClipboard } from "../../utils/dateUtils";

// Visual sort indicator shared across table headers that support sort toggling.
// `null` renders a neutral double-arrow to signal the column is sortable.
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

// Converts API status strings to the appropriate badge CSS modifier.
export function recStatusClass(status: string): string {
  if (status === "Active") return "vds-status-badge--success";
  if (status === "PendingDelete") return "vds-status-badge--danger";
  if (status === "PendingUpdate") return "vds-status-badge--warning";
  if (status === "Pending") return "vds-status-badge--warning";
  if (status === "Inactive") return "vds-status-badge--secondary";
  return "vds-status-badge--secondary";
}
export function recStatusLabel(status: string): string {
  if (status === "PendingDelete") return "Pending Delete";
  if (status === "PendingUpdate") return "Pending Update";
  return status;
}

/** FQDN text span — wraps long domain names instead of truncating. */
function FqdnTooltipSpan({ fqdn }: { fqdn: string }) {
  return (
    <span
      className="small fw-semibold vds-table-primary"
      style={{ wordBreak: "break-all" }}
    >
      {fqdn}
    </span>
  );
}

/**
 * Renders a single DNS record value in a type-aware, human-readable format.
 * Returns plain text for both simple and multi-field records.
 */
function renderRecordValue(
  type: string,
  r: Record<string, unknown>,
): React.ReactNode {
  switch (type) {
    case "A":
    case "AAAA":
      return <span>{String(r.address ?? "—")}</span>;
    case "CNAME":
      return <span>{String(r.cname ?? "—")}</span>;
    case "PTR":
      return <span>{String(r.ptrdname ?? "—")}</span>;
    case "TXT":
    case "SPF":
      return <span>{String(r.text ?? "—")}</span>;
    case "NS":
      return <span>{String(r.nsdname ?? "—")}</span>;
    case "MX":
      return <span>{`Pref: ${String(r.preference ?? "—")}, Exchange: ${String(r.exchange ?? "—")}`}</span>;
    case "SRV":
      return <span>{`Priority: ${String(r.priority ?? "—")}, Weight: ${String(r.weight ?? "—")}, Port: ${String(r.port ?? "—")}, Target: ${String(r.target ?? "—")}`}</span>;
    case "NAPTR":
      return <span>{`Order: ${String(r.order ?? "—")}, Pref: ${String(r.preference ?? "—")}, Flags: ${String(r.flags ?? "—")}, Service: ${String(r.service ?? "—")}, Regexp: ${String(r.regexp ?? "—")}, Replace: ${String(r.replacement ?? "—")}`}</span>;
    case "DS":
      return <span>{`Keytag: ${String(r.keytag ?? "—")}, Algorithm: ${String(r.algorithm ?? "—")}, Digest Type: ${String(r.digestType ?? r.digesttype ?? "—")}, Digest: ${String(r.digest ?? "—")}`}</span>;
    case "SOA":
      return <span>{`Mname: ${String(r.mname ?? "—")}, Rname: ${String(r.rname ?? "—")}, Serial: ${String(r.serial ?? "—")}, Refresh: ${String(r.refresh ?? "—")}, Retry: ${String(r.retry ?? "—")}, Expire: ${String(r.expire ?? "—")}, Min: ${String(r.minimum ?? "—")}`}</span>;
    case "SSHFP":
      return <span>{`Algorithm: ${String(r.algorithm ?? "—")}, Type: ${String(r.type ?? "—")}, Fingerprint: ${String(r.fingerprint ?? "—")}`}</span>;
    default: {
      const val = (r.address ??
        r.cname ??
        r.ptrdname ??
        r.text ??
        r.nsdname ??
        r.exchange ??
        r.target ??
        r.value) as unknown;
      return <span>{String(val ?? "—")}</span>;
    }
  }
}

/** Maximum number of record values shown before the "Show more…" link appears. */
const SHOW_MAX = 4;

/**
 * Expandable record-data cell for a single record set row.
 *
 * Renders up to SHOW_MAX values by default and provides a "Show more…" /
 * Each value is rendered via `renderRecordValue` which handles type-specific
 * field names for all supported DNS record types including DS, SOA, SPF, and SSHFP.
 */
function RecordDataCell({ rec }: { rec: any }) {
  const [expanded, setExpanded] = useState(false);
  const records: Record<string, unknown>[] = rec.records ?? [];

  if (records.length === 0) {
    return <span className="vds-table-secondary small">{rec.data ?? "—"}</span>;
  }

  const visibleRecords = expanded ? records : records.slice(0, SHOW_MAX);
  const hasMore = records.length > SHOW_MAX;

  return (
    <ul className="mb-0 ps-3 small" style={{ listStyle: "disc", margin: 0 }}>
      {visibleRecords.map((r, i) => (
        <li key={i} style={{ overflowWrap: "break-word" }}>
          {renderRecordValue(String(rec.type ?? ""), r)}
        </li>
      ))}
      {hasMore && !expanded && (
        <li style={{ listStyle: "none" }}>
          <button
            type="button"
            className="btn btn-link btn-sm p-0"
            style={{ fontSize: "0.78rem" }}
            onClick={() => setExpanded(true)}
          >
            Show more… (+{records.length - SHOW_MAX})
          </button>
        </li>
      )}
      {hasMore && expanded && (
        <li style={{ listStyle: "none" }}>
          <button
            type="button"
            className="btn btn-link btn-sm p-0"
            style={{ fontSize: "0.78rem" }}
            onClick={() => setExpanded(false)}
          >
            Show fewer…
          </button>
        </li>
      )}
    </ul>
  );
}

/**
 * Produces a compact plain-text summary of the first record value for use as
 * the tooltip title on the record data cell. Only the first value is included
 * so the tooltip remains readable; the full list is available via expansion.
 */
export function summarizeRecordData(rec: any): string {
  const records: Record<string, unknown>[] = rec.records ?? [];
  if (records.length === 0) return String(rec.data ?? "—");
  const first = records[0];
  const type = String(rec.type ?? "");
  let val: unknown;
  switch (type) {
    case "A":
    case "AAAA":
      val = first.address;
      break;
    case "CNAME":
      val = first.cname;
      break;
    case "PTR":
      val = first.ptrdname;
      break;
    case "TXT":
    case "SPF":
      val = first.text;
      break;
    case "NS":
      val = first.nsdname;
      break;
    case "MX":
      val = `Pref: ${String(first.preference ?? "")} Exchange: ${String(first.exchange ?? "")}`;
      break;
    case "SRV":
      val = `${String(first.priority ?? "")} ${String(first.weight ?? "")} ${String(first.port ?? "")} ${String(first.target ?? "")}`;
      break;
    case "DS":
      val = `Keytag: ${String(first.keytag ?? "")}`;
      break;
    case "SOA":
      val = `Mname: ${String(first.mname ?? "")}`;
      break;
    case "SSHFP":
      val = `${String(first.algorithm ?? "")} ${String(first.type ?? "")} ${String(first.fingerprint ?? "")}`;
      break;
    default:
      val =
        first.address ??
        first.cname ??
        first.ptrdname ??
        first.text ??
        first.nsdname ??
        first.exchange ??
        first.target ??
        first.value;
  }
  return records.length > 1
    ? `${String(val ?? "")} (+${records.length - 1} more)`
    : String(val ?? "—");
}

/**
 * @param records        - Pre-fetched (and optionally client-filtered) record sets.
 * @param onEdit         - When provided, renders an Edit action button per row.
 *                         Omit for read-only views (e.g. Global Search).
 * @param onDelete       - When provided, renders a Delete action button per row.
 * @param onViewHistory  - When provided, renders a History action button that
 *                         opens the RecordHistoryModal for the selected record.
 * @param showZone       - Toggles the Zone column; useful when the table is
 *                         already scoped to a single zone.
 * @param showOwnerGroup - Toggles the Owner Group column; not always relevant
 *                         in the global search context.
 * @param nameSort       - Current API sort direction ("ASC" | "DESC" | "").
 * @param onToggleSort   - Callback to flip the sort; clicking the FQDN header
 *                         cycles between ascending and descending.
 */
interface RecordsTableProps {
  records: any[];
  onEdit?: (record: any) => void;
  onDelete?: (record: any) => void;
  onViewHistory?: (record: any) => void;
  showZone?: boolean;
  showOwnerGroup?: boolean;
  nameSort?: string;
  onToggleSort?: (sort: string) => void;
}

/**
 * Generates a two-character avatar initials string from an FQDN for the row
 * avatar badge. Splits on common DNS delimiters (dot, hyphen, underscore) and
 * takes the first character of the first two segments. Falls back to the raw
 * FQDN prefix if splitting yields empty strings (e.g. single-label names).
 */
const fqdnInitials = (fqdn: string): string =>
  fqdn
    .replace(/\.$/, "")
    .split(/[.\-_]+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("") || fqdn.slice(0, 2).toUpperCase();

const COPY_KEYFRAMES = `
  @keyframes vdsCopiedCheck {
    0%   { opacity: 0; transform: scale(0.4) rotate(-15deg); }
    60%  { transform: scale(1.3) rotate(5deg); }
    80%  { transform: scale(0.9) rotate(-2deg); }
    100% { opacity: 1; transform: scale(1) rotate(0deg); }
  }
`;

export function RecordsSearchTable({
  records,
  onEdit,
  onDelete,
  onViewHistory,
  showZone = true,
  showOwnerGroup = false,
  nameSort = "",
  onToggleSort,
}: RecordsTableProps) {
  // Tracks which row's FQDN was most recently copied (by row key) so the
  // "Copied!" confirmation only lights up the one button that was clicked.
  // Comparing by FQDN string is wrong when multiple rows share the same name;
  // using rec.id ?? idx gives a stable per-row identity.
  const [copiedRowKey, setCopiedRowKey] = useState<string | number | null>(
    null,
  );

  const handleCopyFqdn = (fqdn: string, rowKey: string | number) => {
    void copyToClipboard(fqdn).then(() => {
      setCopiedRowKey(rowKey);
      setTimeout(() => setCopiedRowKey(null), 2000);
    });
  };

  const handleSortToggle = () => {
    if (!onToggleSort) return;
    onToggleSort(nameSort === "ASC" ? "DESC" : "ASC");
  };

  const sortDir: SortDir =
    nameSort === "ASC" ? "asc" : nameSort === "DESC" ? "desc" : null;

  if (records.length === 0) {
    return (
      <div className="vds-empty-state">
        <i className="bi bi-search fs-1 mb-2" style={{ opacity: 0.4 }} />
        <p className="mb-0 fw-semibold">No records found</p>
        <small className="text-muted">
          Enter a FQDN above and press Enter.
        </small>
      </div>
    );
  }

  return (
    <div
      className="vds-zones-table-wrap"
      style={{ overflow: "auto", maxHeight: "65vh" }}
    >
      <style>{COPY_KEYFRAMES}</style>
      <table className="vds-zones-table" style={{ width: "100%" }}>
        <thead>
          <tr>
            <th
              onClick={handleSortToggle}
              style={{
                cursor: onToggleSort ? "pointer" : "default",
                userSelect: "none",
                whiteSpace: "nowrap",
              }}
            >
              FQDN <SortArrow dir={sortDir} />
            </th>
            <th style={{ whiteSpace: "nowrap" }}>TYPE</th>
            <th style={{ whiteSpace: "nowrap" }}>TTL</th>
            <th>RECORD DATA</th>
            {showZone && <th style={{ whiteSpace: "nowrap" }}>ZONE</th>}
            <th style={{ whiteSpace: "nowrap" }}>ZONE ACCESS</th>
            {showOwnerGroup && <th>OWNER GROUP</th>}
            <th>HISTORY</th>
          </tr>
        </thead>
        <tbody>
          {records.map((rec, idx) => {
            // `isShared` collapses the tri-state API value (true/false/absent)
            // into a typed union so the access badge can use a single switch-like
            // className without nested ternaries scattered through the JSX.
            const isShared =
              rec.zoneShared === true
                ? "shared"
                : rec.zoneShared === false
                  ? "private"
                  : null;

            // Prefer the pre-computed FQDN from the API; fall back to
            // constructing it from name + zone so the column is never empty.
            const fqdn =
              rec.fqdn ??
              (rec.name && rec.zoneName
                ? `${String(rec.name)}.${String(rec.zoneName)}`
                : (rec.name ?? "—"));

            return (
              <tr key={rec.id ?? idx}>
                {/* FQDN */}
                <td>
                  <div className="d-flex align-items-center gap-2">
                    <span
                      className="vds-zone-avatar"
                      style={{
                        fontSize: "0.6rem",
                        width: 28,
                        height: 28,
                        borderRadius: 6,
                        flexShrink: 0,
                      }}
                    >
                      {fqdnInitials(String(fqdn))}
                    </span>
                    <div className="d-flex align-items-center gap-1 min-width-0">
                      <FqdnTooltipSpan fqdn={String(fqdn)} />
                      <button
                        type="button"
                        className="btn btn-link btn-sm p-0 flex-shrink-0"
                        title="Copy FQDN"
                        style={{
                          lineHeight: 1,
                          fontSize: "0.78rem",
                          color:
                            copiedRowKey === (rec.id ?? idx)
                              ? "#16a34a"
                              : "#94a3b8",
                          transition: "color 0.15s",
                        }}
                        onClick={() =>
                          handleCopyFqdn(String(fqdn), rec.id ?? idx)
                        }
                      >
                        {copiedRowKey === (rec.id ?? idx) ? (
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
                </td>

                {/* TYPE */}
                <td
                  className="vds-table-secondary small fw-semibold"
                  style={{ whiteSpace: "nowrap" }}
                >
                  {String(rec.type ?? "—")}
                </td>

                {/* TTL */}
                <td
                  className="vds-table-secondary small"
                  style={{ whiteSpace: "nowrap" }}
                >
                  {rec.ttl != null ? `${String(rec.ttl)}s` : "—"}
                </td>

                {/* RECORD DATA */}
                <td
                  className="vds-table-secondary small"
                  style={{
                    wordBreak: "break-word",
                    overflowWrap: "break-word",
                  }}
                  title={summarizeRecordData(rec)}
                >
                  <RecordDataCell rec={rec} />
                </td>

                {/* ZONE */}
                {showZone && (
                  <td
                    className="vds-table-secondary small"
                    style={{ overflowWrap: "break-word" }}
                  >
                    {String(rec.zoneName ?? rec.zone ?? "—")}
                  </td>
                )}

                {/* ZONE ACCESS TYPE */}
                <td
                  className="vds-table-secondary small"
                  style={{ whiteSpace: "nowrap" }}
                >
                  {isShared ? (
                    <span
                      className={`vds-access-badge vds-access-badge--${isShared}`}
                    >
                      <i
                        className={`bi ${isShared === "shared" ? "bi-share-fill" : "bi-lock-fill"} me-1`}
                        style={{ fontSize: "0.65rem" }}
                      />
                      {isShared === "shared" ? "Shared" : "Private"}
                    </span>
                  ) : (
                    <span className="vds-table-secondary small">—</span>
                  )}
                </td>

                {/* OWNER GROUP */}
                {showOwnerGroup && (
                  <td
                    className="vds-table-secondary small"
                    style={{ overflowWrap: "break-word" }}
                  >
                    {String(rec.ownerGroupName ?? "—")}
                  </td>
                )}

                {/* HISTORY */}
                <td>
                  <div className="d-flex gap-1 flex-nowrap">
                    {onViewHistory && (
                      <button
                        type="button"
                        className="vds-action-btn vds-action-btn--view"
                        title="View history"
                        onClick={() => onViewHistory({ ...rec, fqdn: String(fqdn) })}
                      >
                        <i className="bi bi-clock-history" />
                      </button>
                    )}
                    {onEdit && (
                      <button
                        type="button"
                        className="vds-action-btn vds-action-btn--edit"
                        title="Edit record"
                        onClick={() => onEdit(rec)}
                      >
                        <i className="bi bi-pencil" />
                      </button>
                    )}
                    {onDelete && (
                      <button
                        type="button"
                        className="vds-action-btn vds-action-btn--delete"
                        title="Delete record"
                        onClick={() => onDelete(rec)}
                      >
                        <i className="bi bi-trash" />
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
