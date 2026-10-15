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

export interface OwnershipTransferModalState {
  mode: "claim" | "request";
  record: RecordSet;
}

export interface OwnershipTransferModalProps {
  modal: OwnershipTransferModalState | null;
  myGroupsData: Array<{ id: string; name: string; description?: string }>;
  ownershipGroupId: string;
  setOwnershipGroupId: (value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
  isPending: boolean;
}

export function OwnershipTransferModal({
  modal,
  myGroupsData,
  ownershipGroupId,
  setOwnershipGroupId,
  onClose,
  onSubmit,
  isPending,
}: OwnershipTransferModalProps) {
  if (!modal) return null;

  return (
    <div
      className="modal d-block"
      style={{ backgroundColor: "rgba(0,0,0,0.5)" }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div className="modal-dialog modal-dialog-centered">
        <div
          className="modal-content"
          style={{ borderRadius: "1rem", overflow: "hidden" }}
        >
          <div
            className={`rhm-header ${modal.mode === "claim" ? "rhm-header--claim-records" : ""}`}
          >
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <div className="min-w-0">
                  <h5 className="m-0 fw-semibold text-white d-flex align-items-center gap-2">
                    <i
                      className={`bi ${modal.mode === "claim" ? "bi-person-plus-fill" : "bi-arrow-left-right"}`}
                    />
                    {modal.mode === "claim"
                      ? "Claim Record Ownership"
                      : "Request Ownership Transfer"}
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

          <div className="modal-body px-4 py-3">
            <div className="d-flex align-items-center gap-3 mb-4 p-3 rounded-3 vds-record--info">
              <div>
                <div
                  className="fw-bold text-primary"
                  style={{ fontSize: "1rem" }}
                >
                  {modal.record.name}
                </div>
                <div className="d-flex align-items-center gap-2 mt-1">
                  <span className="vds-record-type-badge">
                    {modal.record.type}
                  </span>
                  {modal.record.ownerGroupId && (
                    <span
                      className="vds-owner-group-chip"
                      style={{ fontSize: "0.7rem" }}
                    >
                      <i className="bi bi-people-fill" />
                      Current:{" "}
                      {modal.record.ownerGroupName ??
                        modal.record.ownerGroupId.slice(0, 10) + "…"}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label fw-semibold">
                {modal.mode === "claim"
                  ? "Assign ownership to"
                  : "Request transfer to"}
                <span className="text-danger ms-1">*</span>
              </label>
              <select
                className="form-select"
                value={ownershipGroupId}
                onChange={(e) => setOwnershipGroupId(e.target.value)}
                style={{ borderRadius: "0.6rem" }}
              >
                <option value="">— Select your group —</option>
                {(myGroupsData ?? [])
                  .filter(
                    (g) =>
                      modal.mode === "claim" ||
                      g.id !== modal.record.ownerGroupId,
                  )
                  .map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
              </select>
              <div className="form-text">
                <i className="bi bi-info-circle me-1" />
                {modal.mode === "claim"
                  ? "The selected group will become the owner of this record."
                  : "A transfer request will be sent to the current owner group for approval."}
              </div>
            </div>
          </div>

          <div className="modal-footer d-flex justify-content-between align-items-center w-100 vds-record--footer">
            <div className="d-flex align-items-center gap-2" />

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
                className={`vds-modal__btn ${
                  modal.mode === "claim"
                    ? "vds-modal__btn--ownership-claim"
                    : "vds-modal__btn--ownership-request"
                }`}
                disabled={!ownershipGroupId || isPending}
                onClick={onSubmit}
              >
                {isPending ? (
                  <>
                    <i className="bi bi-hourglass-split me-1 vds-spin" />
                    Processing…
                  </>
                ) : modal.mode === "claim" ? (
                  <>
                    <i className="bi bi-person-plus-fill me-1" />
                    Claim Ownership
                  </>
                ) : (
                  <>
                    <i className="bi bi-arrow-left-right me-1" />
                    Request Transfer
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
