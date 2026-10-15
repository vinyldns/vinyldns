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

export interface AbandonZoneModalProps {
  isOpen: boolean;
  zoneName: string;
  zoneId: string;
  isPending: boolean;
  onClose: () => void;
  onConfirm: () => void;
  onNavigateToRecords: () => void;
}

export function AbandonZoneModal({
  isOpen,
  zoneName,
  zoneId,
  isPending,
  onClose,
  onConfirm,
  onNavigateToRecords,
}: AbandonZoneModalProps) {
  if (!isOpen) return null;

  return (
    <div
      className="modal d-block"
      style={{ backgroundColor: "rgba(0,0,0,0.5)" }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content">
          <div className="rhm-header">
            <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
              <div className="d-flex align-items-center gap-3 min-w-0">
                <div className="min-w-0">
                  <h5 className="m-0 fw-semibold text-white d-flex align-items-center gap-2">
                    <i className="bi bi-exclamation-triangle-fill" />
                    Abandon Zone?
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
          <div className="modal-body">
            <p className="mb-0">
              Are you sure you want to abandon <strong>{zoneName}</strong>? This disconnects the zone from VinylDNS but DNS records will still exist unless deleted from <Link to={`/zones/${zoneId}`} onClick={() => { onClose(); onNavigateToRecords(); }} className="fw-semibold">Manage Records</Link>.
            </p>
          </div>
          <div className="modal-footer">
            <button
              className="btn btn-outline-secondary"
              onClick={onClose}
            >
              Close
            </button>
            <button
              className="btn btn-danger d-flex align-items-center gap-1"
              disabled={isPending}
              onClick={onConfirm}
            >
              {isPending ? (
                <>
                  <i className="bi bi-hourglass-split vds-spin" />
                  Abandoning…
                </>
              ) : (
                <>
                  <i className="bi bi-box-arrow-left" />
                  Abandon
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
