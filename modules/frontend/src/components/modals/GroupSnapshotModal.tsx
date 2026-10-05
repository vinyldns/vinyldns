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
import type { Group, GroupMember } from "../../types/group";

export interface GroupSnapshotModalProps {
  isOpen: boolean;
  title: string;
  group: Group;
  onClose: () => void;
}

export function GroupSnapshotModal({
  isOpen,
  title,
  group,
  onClose,
}: GroupSnapshotModalProps) {
  if (!isOpen) return null;

  return (
    <div className="modal d-block vds-group-modal-backdrop" onClick={onClose}>
      <div
        className="modal-dialog modal-dialog-centered"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-content">
          <div className="rhm-header">
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <div className="d-flex align-items-center gap-2 min-w-0">
                  <i
                    className="bi bi-info-circle-fill"
                    style={{ fontSize: "0.95rem", color: "#93c5fd" }}
                  />
                  <div className="min-w-0">
                    <h5 className="m-0 fw-semibold text-white">{title}</h5>
                  </div>
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
          <div className="modal-body px-4 py-3">
            {(
              [
                { label: "Group ID", value: group.id },
                { label: "Group Name", value: group.name },
                { label: "Email", value: group.email },
                {
                  label: "Description",
                  value: group.description ?? null,
                },
                { label: "Status", value: group.status ?? null },
                {
                  label: "Created",
                  value: group.created
                    ? new Date(group.created).toLocaleString()
                    : null,
                },
                {
                  label: "Member IDs",
                  value:
                    group.members?.map((m: GroupMember) => m.id).join("\n") ??
                    null,
                  mono: true,
                },
                {
                  label: "Admin IDs",
                  value:
                    group.admins?.map((a: GroupMember) => a.id).join("\n") ??
                    null,
                  mono: true,
                },
              ] as Array<{
                label: string;
                value: string | null;
                mono?: boolean;
              }>
            )
              .filter((row) => row.value)
              .map((row) => (
                <div key={row.label} className="mb-3">
                  <div className="vds-group-modal-label">{row.label}</div>
                  {row.mono ? (
                    <textarea
                      readOnly
                      className="form-control form-control-sm vds-group-modal-textarea"
                      rows={Math.min(row.value!.split("\n").length, 4)}
                      value={row.value!}
                    />
                  ) : (
                    <div className="fw-semibold vds-group-modal-value">
                      {row.value}
                    </div>
                  )}
                </div>
              ))}
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
