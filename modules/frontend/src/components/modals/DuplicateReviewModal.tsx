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

import React, { useEffect } from "react";

export interface DuplicateReviewRecordData {
  address?: string;
  cname?: string;
  ptrdname?: string;
  text?: string;
  preference?: number;
  exchange?: string;
  nsdname?: string;
  priority?: number;
  weight?: number;
  port?: number;
  target?: string;
  order?: number;
  flags?: string;
  service?: string;
  regexp?: string;
  replacement?: string;
}

export interface DuplicateReviewChangeItem {
  changeType?: string;
  type?: string;
  inputName?: string;
  ttl?: number;
  record?: DuplicateReviewRecordData;
}

export interface DuplicateReviewModalState {
  changes: DuplicateReviewChangeItem[];
  groups: { signature: string; indices: number[] }[];
  keep: Set<number>;
}

export interface DuplicateReviewModalProps {
  state: DuplicateReviewModalState;
  onToggleKeep: (rowIdx: number) => void;
  onApply: () => void;
  onCancel: () => void;
}

export function formatRecordData(
  record: DuplicateReviewRecordData | undefined,
): string {
  if (!record) return "—";
  const parts: string[] = [];
  for (const [k, v] of Object.entries(record).sort(([a], [b]) =>
    a.localeCompare(b),
  )) {
    if (v === undefined || v === null) continue;
    if (typeof v === "string" && v.trim() === "") continue;
    parts.push(`${k}: ${v}`);
  }
  return parts.length ? parts.join(", ") : "—";
}

export function DuplicateReviewModal({
  state,
  onToggleKeep,
  onApply,
  onCancel,
}: DuplicateReviewModalProps) {
  const { changes, groups, keep } = state;
  const totalRows = changes.length;
  const willRemove = totalRows - keep.size;
  const willKeep = keep.size;

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="dup-review-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      className="modal d-block rhm-backdrop vds-dark-modal-backdrop"
    >
      <div className="modal-dialog modal-dialog-scrollable modal-dialog-centered rhm-dialog vds-dup-review-dialog">
        <div className="modal-content border-0 rhm-content vds-dark-modal-content">
          <div className="rhm-header vds-dark-modal-header vds-confirm-modal__header">
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <span className="vds-confirm-modal__header-icon">
                  <i
                    className="bi bi-exclamation-triangle-fill"
                    aria-hidden="true"
                  />
                </span>
                <div className="min-w-0">
                  <h5
                    id="dup-review-title"
                    className="m-0 fw-semibold text-white vds-dark-modal-title"
                  >
                    Duplicate records found
                  </h5>
                </div>
              </div>

              <div className="d-flex align-items-center gap-2 flex-shrink-0">
                <button
                  type="button"
                  aria-label="Close"
                  title="Close"
                  onClick={onCancel}
                  className="rhm-header-btn"
                >
                  <i className="rhm-close-icon bi bi-x-lg" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>

          <div className="modal-body vds-confirm-modal__body vds-dup-review-body">
            <p className="vds-confirm-modal__description vds-dup-review-description">
              Rows are considered duplicates when{" "}
              <strong>Change Type, Record Type, Input Name,</strong> and{" "}
              <strong>Record Data</strong> all match. Keep the rows you want to
              import; unchecked rows will be dropped before the form is
              populated.
            </p>

            {groups.map((group, gIdx) => {
              const sample = changes[group.indices[0]];
              return (
                <div
                  key={gIdx}
                  className="vds-inner-card mt-2 vds-dup-review-card"
                >
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <span className="vds-dark-modal-badge badge-type">
                      Group {gIdx + 1}
                    </span>
                    <span className="small text-muted vds-dup-review-meta">
                      {group.indices.length} identical rows
                    </span>
                  </div>

                  <div className="row g-2 mb-3 vds-dup-review-grid">
                    <div className="col-md-6">
                      <div className="small text-muted vds-dup-review-field-label">
                        Change Type
                      </div>
                      <div className="fw-semibold vds-dup-review-field-value">
                        {sample.changeType ?? "—"}
                      </div>
                    </div>
                    <div className="col-md-6">
                      <div className="small text-muted vds-dup-review-field-label">
                        Record Type
                      </div>
                      <div className="fw-semibold vds-dup-review-field-value">
                        {sample.type ?? "—"}
                      </div>
                    </div>
                    <div className="col-12">
                      <div className="small text-muted vds-dup-review-field-label">
                        Input Name
                      </div>
                      <div className="fw-semibold vds-dup-review-field-value">
                        {sample.inputName || "—"}
                      </div>
                    </div>
                    <div className="col-12">
                      <div className="small text-muted vds-dup-review-field-label">
                        Record Data
                      </div>
                      <div className="fw-semibold font-monospace small vds-dup-review-field-value vds-dup-review-record-value">
                        {formatRecordData(sample.record)}
                      </div>
                    </div>
                  </div>

                  <div className="d-flex flex-column gap-2">
                    {group.indices.map((rowIdx) => {
                      const row = changes[rowIdx];
                      const checked = keep.has(rowIdx);
                      return (
                        <label
                          key={rowIdx}
                          className={`d-flex align-items-center gap-2 px-2 py-2 rounded border vds-dup-review-row ${
                            checked ? "vds-dup-review-row--selected" : ""
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => onToggleKeep(rowIdx)}
                          />
                          <span className="fw-semibold small vds-dup-review-row-label">
                            Row #{rowIdx + 1}
                          </span>
                          <span className="badge vds-dup-review-ttl">
                            TTL {row.ttl !== undefined ? row.ttl : "—"}
                          </span>
                          <span className="flex-grow-1" />
                          {checked && (
                            <span className="badge bg-danger text-white">
                              Remove
                            </span>
                          )}
                        </label>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="modal-footer d-flex justify-content-between align-items-center w-100 vds-confirm-modal__footer">
            <div className="d-flex align-items-center gap-2">
              {willRemove > 0 && (
                <span className="small text-muted">
                  <strong className="text-danger">{willRemove}</strong>{" "}
                  duplicate{willRemove !== 1 ? "s" : ""} will be removed
                </span>
              )}
            </div>

            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--secondary"
                onClick={onCancel}
              >
                <i className="bi bi-x-circle me-1" />
                Cancel import
              </button>
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--primary"
                onClick={onApply}
                disabled={willKeep === 0}
              >
                <i className="bi bi-check2-circle me-1" />
                Apply &amp; import {willKeep} row{willKeep !== 1 ? "s" : ""}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
