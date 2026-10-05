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

export interface AclRuleForm {
  mode: "create" | "edit";
  ruleIndex?: number;
  rule: {
    priority: "User" | "Group";
    userName?: string;
    groupId?: string;
    accessLevel: string;
    recordTypes: string[];
    recordMask?: string;
    description?: string;
  };
}

export interface AclRuleModalProps {
  modal: AclRuleForm | null;
  groupsData: Array<{ id: string; name: string; description?: string }>;
  onClose: () => void;
  onChange: React.Dispatch<React.SetStateAction<AclRuleForm | null>>;
  onSave: () => void;
  isSaving: boolean;
}

export function AclRuleModal({
  modal,
  groupsData,
  onClose,
  onChange,
  onSave,
  isSaving,
}: AclRuleModalProps) {
  if (!modal) return null;

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
                    <i className="bi bi-shield-plus" />
                    {modal.mode === "create"
                      ? "Create ACL Rule"
                      : "Update ACL Rule"}
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
          <div className="modal-body p-4">
            <div className="row g-3">
              <div className="col-12">
                <label className="vds-zone-form__label">Apply Rule to</label>
                <div className="d-flex gap-4">
                  {(["User", "Group"] as const).map((p) => (
                    <div key={p} className="form-check">
                      <input
                        type="radio"
                        className="form-check-input"
                        id={`acl-priority-${p}`}
                        name="aclPriority"
                        checked={modal.rule.priority === p}
                        onChange={() =>
                          onChange((prev) =>
                            prev
                              ? {
                                  ...prev,
                                  rule: {
                                    ...prev.rule,
                                    priority: p,
                                    userName: undefined,
                                    groupId: undefined,
                                  },
                                }
                              : null,
                          )
                        }
                      />
                      <label
                        className="form-check-label fw-semibold"
                        htmlFor={`acl-priority-${p}`}
                      >
                        {p}
                      </label>
                    </div>
                  ))}
                </div>
                <p
                  className="text-muted mb-0 mt-1"
                  style={{ fontSize: "0.78rem" }}
                >
                  The more specific a rule is the more precedence it has. User
                  rules will have a higher priority than Group, which will have
                  a higher priority than All.
                </p>
              </div>

              {modal.rule.priority === "User" ? (
                <div className="col-md-6">
                  <label className="vds-zone-form__label">
                    User NTID <span className="text-danger">*</span>
                  </label>
                  <input
                    type="text"
                    className="form-control vds-zone-form__input"
                    placeholder="Enter username / NTID"
                    value={modal.rule.userName ?? ""}
                    onChange={(e) =>
                      onChange((prev) =>
                        prev
                          ? {
                              ...prev,
                              rule: {
                                ...prev.rule,
                                userName: e.target.value,
                              },
                            }
                          : null,
                      )
                    }
                  />
                  <p
                    className="text-muted mb-0 mt-1"
                    style={{ fontSize: "0.78rem" }}
                  >
                    NTID of the user this rule applies to.
                  </p>
                </div>
              ) : (
                <div className="col-md-6">
                  <label className="vds-zone-form__label">
                    Group <span className="text-danger">*</span>
                  </label>
                  <select
                    className="form-select vds-zone-form__input"
                    value={modal.rule.groupId ?? ""}
                    onChange={(e) =>
                      onChange((prev) =>
                        prev
                          ? {
                              ...prev,
                              rule: {
                                ...prev.rule,
                                groupId: e.target.value,
                              },
                            }
                          : null,
                      )
                    }
                  >
                    <option value="">— Select a group —</option>
                    {(groupsData ?? [])
                      .slice()
                      .sort((a, b) => a.name.localeCompare(b.name))
                      .map((g) => (
                        <option key={g.id} value={g.id}>
                          {g.name}
                          {g.description ? ` (${g.description})` : ""}
                        </option>
                      ))}
                  </select>
                  <p
                    className="text-muted mb-0 mt-1"
                    style={{ fontSize: "0.78rem" }}
                  >
                    Group this rule applies to.
                  </p>
                </div>
              )}

              <div className="col-md-6">
                <label className="vds-zone-form__label">Access Level</label>
                <select
                  className="form-select vds-zone-form__input"
                  value={modal.rule.accessLevel}
                  onChange={(e) =>
                    onChange((prev) =>
                      prev
                        ? {
                            ...prev,
                            rule: { ...prev.rule, accessLevel: e.target.value },
                          }
                        : null,
                    )
                  }
                >
                  {[
                    { label: "Read", value: "Read" },
                    { label: "Write", value: "Write" },
                    { label: "Delete", value: "Delete" },
                    { label: "No Access", value: "NoAccess" },
                  ].map(({ label, value }) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <p
                  className="text-muted mb-0 mt-1"
                  style={{ fontSize: "0.78rem" }}
                >
                  The access level that the selected user or group will be given
                  within this zone.
                </p>
              </div>

              <div className="col-12">
                <label className="vds-zone-form__label">
                  Record Type(s){" "}
                  <span className="text-muted fw-normal">
                    (empty = all types)
                  </span>
                </label>
                <div className="d-flex flex-wrap gap-2 mb-1">
                  {[
                    "A",
                    "AAAA",
                    "CNAME",
                    "DS",
                    "MX",
                    "NS",
                    "PTR",
                    "SRV",
                    "NAPTR",
                    "SSHFP",
                    "TXT",
                  ].map((t) => {
                    const checked = (modal.rule.recordTypes ?? []).includes(t);
                    return (
                      <label
                        key={t}
                        className={`vds-acl-type-chip${checked ? " vds-acl-type-chip--active" : ""}`}
                      >
                        <input
                          type="checkbox"
                          className="visually-hidden"
                          checked={checked}
                          onChange={(e) =>
                            onChange((prev) => {
                              if (!prev) return null;
                              const types = prev.rule.recordTypes ?? [];
                              return {
                                ...prev,
                                rule: {
                                  ...prev.rule,
                                  recordTypes: e.target.checked
                                    ? [...types, t]
                                    : types.filter((x) => x !== t),
                                },
                              };
                            })
                          }
                        />
                        {t}
                      </label>
                    );
                  })}
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary px-2 py-0"
                    style={{ fontSize: "0.75rem", height: 28 }}
                    onClick={() =>
                      onChange((prev) =>
                        prev
                          ? {
                              ...prev,
                              rule: { ...prev.rule, recordTypes: [] },
                            }
                          : null,
                      )
                    }
                  >
                    Clear
                  </button>
                </div>
                <p className="text-muted mb-0" style={{ fontSize: "0.78rem" }}>
                  This rule will apply only to the selected record types. If no
                  types are selected then the rule will apply to all record
                  types.
                </p>
              </div>

              <div className="col-md-6">
                <label className="vds-zone-form__label">
                  Record Mask{" "}
                  <span className="text-muted fw-normal">(optional)</span>
                </label>
                <input
                  type="text"
                  className="form-control vds-zone-form__input"
                  placeholder="e.g. .* or 192.168.0.0/24"
                  value={modal.rule.recordMask ?? ""}
                  onChange={(e) =>
                    onChange((prev) =>
                      prev
                        ? {
                            ...prev,
                            rule: { ...prev.rule, recordMask: e.target.value },
                          }
                        : null,
                    )
                  }
                />
                <p
                  className="text-muted mb-0 mt-1"
                  style={{ fontSize: "0.78rem" }}
                >
                  Record masks further refine the types of records this record
                  applies to. For non-PTR records, any valid regex will be
                  accepted. For PTR records, please input a CIDR rule. If no
                  mask is entered, the rule will apply to all.
                </p>
              </div>

              <div className="col-md-6">
                <label className="vds-zone-form__label">
                  Description{" "}
                  <span className="text-muted fw-normal">(optional)</span>
                </label>
                <textarea
                  className="form-control vds-zone-form__input"
                  rows={3}
                  value={modal.rule.description ?? ""}
                  onChange={(e) =>
                    onChange((prev) =>
                      prev
                        ? {
                            ...prev,
                            rule: { ...prev.rule, description: e.target.value },
                          }
                        : null,
                    )
                  }
                />
              </div>
            </div>
          </div>
          <div className="modal-footer d-flex justify-content-between align-items-center w-100">
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--utility"
                onClick={() =>
                  onChange((prev) =>
                    prev
                      ? {
                          ...prev,
                          rule: {
                            priority: "User",
                            accessLevel: "Read",
                            recordTypes: [],
                            userName: undefined,
                            groupId: undefined,
                            recordMask: undefined,
                            description: undefined,
                          },
                        }
                      : null,
                  )
                }
              >
                <i className="bi bi-arrow-counterclockwise me-1" />
                Clear Form
              </button>
            </div>

            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--secondary"
                onClick={onClose}
              >
                <i className="bi bi-x-circle me-1" />
                Cancel
              </button>
              <button
                type="button"
                className="vds-modal__btn vds-modal__btn--primary d-flex align-items-center gap-1"
                disabled={
                  isSaving ||
                  (modal.rule.priority === "User" &&
                    !modal.rule.userName?.trim()) ||
                  (modal.rule.priority === "Group" && !modal.rule.groupId)
                }
                onClick={onSave}
              >
                {isSaving ? (
                  <>
                    <i className="bi bi-hourglass-split vds-spin" />
                    Saving…
                  </>
                ) : (
                  <>
                    <i className="bi bi-check-circle" />
                    Save Rule
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
