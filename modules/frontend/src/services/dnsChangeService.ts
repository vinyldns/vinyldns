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

import api, { urlBuilder } from "./api";
import type {
  BatchChangeCount,
  DnsChange,
  DnsChangeListResponse,
  CreateDnsChangeRequest,
} from "../types/dnsChange";

const BASE = "/dnschanges";

export const dnsChangeService = {
  getBatchChange(id: string) {
    return api.get<DnsChange>(`${BASE}/${id}`);
  },

  /**
   * Submits a new batch change to the API.
   *
   * `allowManualReview` maps to the `allowManualReview` query param, which
   * lets the submitter opt in to manual review even when the server would
   * otherwise auto-process the change. Defaults to false (server default).
   */
  createBatchChange(data: CreateDnsChangeRequest, allowManualReview?: boolean) {
    const url = urlBuilder(BASE, {
      allowManualReview: allowManualReview,
    });
    return api.post<DnsChange>(url, data);
  },

  /**
   * Fetches a paginated list of batch changes.
   *
   * When `ignoreAccess` is true the API returns changes from all users, not
   * just the authenticated user — this is the "All Requests" admin view.
   * The optional `userName`, `approvalStatus`, and date-range params are
   * only meaningful in that context; passing them on the "My Requests" view
   * has no effect but wastes cache space, so callers should omit them.
   */
  getBatchChanges(
    maxItems?: number,
    startFrom?: number,
    ignoreAccess?: boolean,
    approvalStatus?: string,
    userName?: string,
    dateTimeRangeStart?: string,
    dateTimeRangeEnd?: string,
  ) {
    const params = {
      maxItems,
      startFrom,
      ignoreAccess,
      approvalStatus: approvalStatus || undefined,
      userName: userName || undefined,
      dateTimeRangeStart: dateTimeRangeStart || undefined,
      dateTimeRangeEnd: dateTimeRangeEnd || undefined,
    };
    return api.get<DnsChangeListResponse>(urlBuilder(BASE, params));
  },

  cancelBatchChange(id: string) {
    return api.post(`${BASE}/${id}/cancel`, {});
  },

  /**
   * Returns accurate per-status counts for batch changes from the
   * dedicated count API — not limited by any page-size cap.
   */
  getBatchChangeCount(
    ignoreAccess?: boolean,
    approvalStatus?: string,
    userName?: string,
    dateTimeRangeStart?: string,
    dateTimeRangeEnd?: string,
  ) {
    const params = {
      ignoreAccess,
      approvalStatus: approvalStatus || undefined,
      userName: userName || undefined,
      dateTimeRangeStart: dateTimeRangeStart || undefined,
      dateTimeRangeEnd: dateTimeRangeEnd || undefined,
    };
    return api.get<BatchChangeCount>(urlBuilder(`${BASE}/count`, params));
  },

  approveBatchChange(id: string, reviewComment?: string) {
    const data = reviewComment ? { reviewComment } : {};
    return api.post(`${BASE}/${id}/approve`, data);
  },

  rejectBatchChange(id: string, reviewComment?: string) {
    const data = reviewComment ? { reviewComment } : {};
    return api.post(`${BASE}/${id}/reject`, data);
  },

  /**
   * Generates and immediately triggers a CSV download for the given change.
   *
   * A temporary object URL is created from a Blob, clicked programmatically,
   * then revoked to avoid memory leaks. The download filename includes the
   * change ID so exported files stay traceable back to their API record.
   *
   * Pass `options.rows` to export a subset (e.g. the rows currently visible
   * after a search). When `rows` is omitted every row in `change.changes` is
   * exported, preserving the legacy "export everything" behavior.
   */
  exportToCsv(change: DnsChange, options?: { rows?: DnsChange["changes"] }) {
    const changes = options?.rows ?? change.changes ?? [];
    const header =
      "Change Type,Input Name,Recordset Name,Zone Name,Record Type,Record Data,TTL,Status,Additional Info";
    const rows = changes.map((c) => {
      const changeType = c.changeType ?? "";
      const inputName = c.inputName ?? "";
      const recordName = c.recordName ?? "";
      const zoneName = c.zoneName ?? "";
      const recordType = c.type ?? "";
      const recordData = c.record ? JSON.stringify(c.record) : "";
      const ttl = c.ttl != null ? String(c.ttl) : "";
      const status = c.status ?? "";
      const additionalInfo =
        c.systemMessage ??
        (c.validationErrors ?? [])
          .map((e: unknown) =>
            e && typeof e === "object" && "message" in e
              ? String((e as { message: unknown }).message)
              : String(e),
          )
          .filter(Boolean)
          .join("; ");
      return [
        changeType,
        inputName,
        recordName,
        zoneName,
        recordType,
        recordData,
        ttl,
        status,
        additionalInfo,
      ]
        .map((v) => `"${v.replace(/"/g, '""')}"`)
        .join(",");
    });
    const csv = [header, ...rows].join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `dns-change-${change.id}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  },
};
