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
import { Link } from "react-router-dom";
import type { AclRule } from "../../types/zone";

export interface AclRulesModalProps {
  isOpen: boolean;
  rules: AclRule[];
  onClose: () => void;
}

export function AclRulesModal({ isOpen, rules, onClose }: AclRulesModalProps) {
  if (!isOpen) return null;

  return (
    <div
      className="modal d-block"
      style={{ backgroundColor: "rgba(0,0,0,0.55)" }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal-dialog modal-dialog-centered modal-lg"
        style={{ maxWidth: "820px" }}
      >
        <div className="modal-content">
          <div className="rhm-header">
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <div className="min-w-0">
                  <h5 className="m-0 fw-semibold text-white d-flex align-items-center gap-2">
                    <i className="bi bi-shield-lock" />
                    ACL Rules
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
            <div className="vds-zones-table-wrap">
              <table className="vds-zones-table">
                <thead>
                  <tr>
                    <th>User / Group</th>
                    <th>Access Level</th>
                    <th>Record Types</th>
                    <th>Record Mask</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map((rule, i) => (
                    <tr key={i}>
                      <td className="vds-table-primary">
                        {rule.groupId ? (
                          <Link
                            to={`/groups/${rule.groupId}`}
                            className="vds-table-link"
                          >
                            <i className="bi bi-people me-1" />
                            {rule.displayName ?? rule.groupId}
                          </Link>
                        ) : (
                          <span>
                            <i className="bi bi-person me-1" />
                            {rule.userName ?? rule.userId ?? "—"}
                          </span>
                        )}
                      </td>
                      <td>
                        <span
                          className={`vds-access-badge vds-access-badge--${(rule.accessLevel ?? "").toLowerCase().replace(" ", "-")}`}
                        >
                          {rule.accessLevel}
                        </span>
                      </td>
                      <td className="vds-table-secondary small">
                        {!rule.recordTypes?.length
                          ? "All Types"
                          : rule.recordTypes.join(", ")}
                      </td>
                      <td className="vds-table-secondary small vds-table-mono">
                        {rule.recordMask ?? "—"}
                      </td>
                      <td className="vds-table-secondary small">
                        {rule.description ?? "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
