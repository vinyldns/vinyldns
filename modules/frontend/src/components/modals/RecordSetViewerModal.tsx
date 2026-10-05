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
import type { RecordSet } from "../../types/record";

export interface RecordSetViewerModalProps {
  isOpen: boolean;
  label: string;
  recordSet: RecordSet;
  statusClass: (status: string) => string;
  onClose: () => void;
}

export function RecordSetViewerModal({
  isOpen,
  label,
  recordSet,
  statusClass,
  onClose,
}: RecordSetViewerModalProps) {
  if (!isOpen) return null;

  return (
    <div
      className="modal d-block"
      style={{ backgroundColor: "rgba(0,0,0,0.55)" }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <div className="modal-content">
          <div className="rhm-header">
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <div className="min-w-0">
                  <h5 className="m-0 fw-semibold text-white d-flex align-items-center gap-2">
                    <i className="bi bi-file-earmark-text" />
                    {label}
                  </h5>
                </div>
              </div>

              <div className="d-flex align-items-center gap-2 flex-shrink-0">
                <button
                  type="button"
                  aria-label="Close"
                  title="Close"
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
          <div className="modal-body p-0">
            <div
              className="d-flex gap-3 flex-wrap px-4 py-3 vds-modal-summary-row"
              style={{
                background: "#f4f7fb",
                borderBottom: "1px solid #e3eaf4",
              }}
            >
              <div>
                <div
                  className="text-muted"
                  style={{
                    fontSize: "0.72rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.06em",
                  }}
                >
                  Name
                </div>
                <div className="fw-semibold">{recordSet.name}</div>
              </div>
              <div>
                <div
                  className="text-muted"
                  style={{
                    fontSize: "0.72rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.06em",
                  }}
                >
                  Type
                </div>
                <span className="vds-record-type-badge">{recordSet.type}</span>
              </div>
              <div>
                <div
                  className="text-muted"
                  style={{
                    fontSize: "0.72rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.06em",
                  }}
                >
                  TTL
                </div>
                <div className="fw-semibold">{recordSet.ttl}s</div>
              </div>
              <div>
                <div
                  className="text-muted"
                  style={{
                    fontSize: "0.72rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.06em",
                  }}
                >
                  Status
                </div>
                <span
                  className={`vds-zone-status-badge ${statusClass(recordSet.status)}`}
                >
                  {recordSet.status}
                </span>
              </div>
              {recordSet.id && (
                <div>
                  <div
                    className="text-muted"
                    style={{
                      fontSize: "0.72rem",
                      textTransform: "uppercase",
                      letterSpacing: "0.06em",
                    }}
                  >
                    ID
                  </div>
                  <code style={{ fontSize: "0.78rem" }}>{recordSet.id}</code>
                </div>
              )}
            </div>

            <div className="px-4 py-3">
              {(recordSet.ownerGroupId ||
                (recordSet.recordSetGroupChange?.requestedOwnerGroupId &&
                  recordSet.recordSetGroupChange.requestedOwnerGroupId !==
                    "null") ||
                recordSet.recordSetGroupChange?.ownershipTransferStatus) && (
                <div className="mb-3 pb-3 vds-view-record--data">
                  <div
                    className="fw-semibold mb-2"
                    style={{ fontSize: "0.85rem", color: "#3a5c8c" }}
                  >
                    <i className="bi bi-people-fill me-1" />
                    Ownership
                  </div>
                  <div className="d-flex flex-wrap gap-3">
                    {recordSet.ownerGroupId && (
                      <div>
                        <div
                          className="text-muted"
                          style={{
                            fontSize: "0.72rem",
                            textTransform: "uppercase",
                            letterSpacing: "0.06em",
                          }}
                        >
                          Record Owner Group
                        </div>
                        <div className="fw-semibold small">
                          {recordSet.ownerGroupName ?? recordSet.ownerGroupId}
                        </div>
                      </div>
                    )}
                    {recordSet.recordSetGroupChange?.requestedOwnerGroupId &&
                      recordSet.recordSetGroupChange.requestedOwnerGroupId !==
                        "null" && (
                        <div>
                          <div
                            className="text-muted"
                            style={{
                              fontSize: "0.72rem",
                              textTransform: "uppercase",
                              letterSpacing: "0.06em",
                            }}
                          >
                            Ownership Transfer Group
                          </div>
                          <div className="fw-semibold small">
                            {
                              recordSet.recordSetGroupChange
                                .requestedOwnerGroupId
                            }
                          </div>
                        </div>
                      )}
                    {recordSet.recordSetGroupChange
                      ?.ownershipTransferStatus && (
                      <div>
                        <div
                          className="text-muted"
                          style={{
                            fontSize: "0.72rem",
                            textTransform: "uppercase",
                            letterSpacing: "0.06em",
                          }}
                        >
                          Ownership Transfer Status
                        </div>
                        <div className="fw-semibold small">
                          {
                            recordSet.recordSetGroupChange
                              .ownershipTransferStatus
                          }
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}
              <div
                className="fw-semibold mb-2"
                style={{ fontSize: "0.85rem", color: "#3a5c8c" }}
              >
                <i className="bi bi-list-ul me-1" />
                Record Data ({recordSet.records.length})
              </div>
              {recordSet.records.length === 0 ? (
                <div className="text-muted small">No records</div>
              ) : (
                <div
                  className="vds-zones-table-wrap"
                  style={{ maxHeight: 320, overflowY: "auto" }}
                >
                  <table className="vds-zones-table">
                    <thead>
                      <tr>
                        <th>Field</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recordSet.records.map((rec, i) => (
                        <React.Fragment key={i}>
                          {i > 0 && (
                            <tr>
                              <td colSpan={2} className="vds-view-record">
                                — record {i + 1} —
                              </td>
                            </tr>
                          )}
                          {Object.entries(rec)
                            .filter(
                              ([, v]) =>
                                v !== undefined && v !== null && v !== "",
                            )
                            .map(([k, v]) => (
                              <tr key={k}>
                                <td
                                  className="vds-table-secondary"
                                  style={{
                                    fontWeight: 600,
                                    width: "30%",
                                    fontSize: "0.82rem",
                                  }}
                                >
                                  {k}
                                </td>
                                <td
                                  className="vds-table-primary"
                                  style={{
                                    fontFamily: "monospace",
                                    fontSize: "0.82rem",
                                    wordBreak: "break-all",
                                  }}
                                >
                                  {String(v)}
                                </td>
                              </tr>
                            ))}
                        </React.Fragment>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
          <div className="modal-footer d-flex justify-content-between align-items-center w-100">
            <div className="d-flex align-items-center gap-2" />

            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--secondary"
                onClick={onClose}
              >
                <i className="bi bi-x-circle me-1" />
                Close
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
