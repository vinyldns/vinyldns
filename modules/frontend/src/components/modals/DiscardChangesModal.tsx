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

interface DiscardChangesModalProps {
  isOpen: boolean;
  title?: string;
  description?: string;
  cancelLabel?: string;
  confirmLabel?: string;
  confirmIcon?: string;
  cancelButtonClassName?: string;
  confirmButtonClassName?: string;
  children?: React.ReactNode;
  onClose: () => void;
  onConfirm: () => void;
}

export function DiscardChangesModal({
  isOpen,
  title = "Discard batch change?",
  description = "All changes entered so far will be lost. This action cannot be undone.",
  cancelLabel = "Keep editing",
  confirmLabel = "Discard changes",
  confirmIcon = "bi-trash3-fill",
  cancelButtonClassName = "vds-modal__btn vds-modal__btn--secondary",
  confirmButtonClassName = "vds-modal__btn vds-modal__btn--danger",
  children,
  onClose,
  onConfirm,
}: DiscardChangesModalProps) {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="discard-change-title"
      className="vds-confirm-modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="vds-confirm-modal">
        <div className="rhm-header vds-dark-modal-header vds-confirm-modal__header">
          <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
            <div className="d-flex align-items-center gap-3 min-w-0">
              <span className="vds-confirm-modal__header-icon">
                <i className="bi bi-exclamation-triangle-fill" />
              </span>
              <div className="min-w-0">
                <h5
                  id="discard-change-title"
                  className="m-0 fw-semibold text-white vds-dark-modal-title"
                >
                  {title}
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

        <div className="vds-confirm-modal__body">
          {description && (
            <p className="vds-confirm-modal__description">{description}</p>
          )}
          {children}
        </div>

        <div className="modal-footer d-flex justify-content-between align-items-center w-100 vds-confirm-modal__footer">
          <div className="d-flex align-items-center gap-2" />

          <div className="d-flex align-items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className={cancelButtonClassName}
            >
              <i className="bi bi-x-circle me-1" />
              {cancelLabel}
            </button>
            <button
              type="button"
              onClick={onConfirm}
              className={confirmButtonClassName}
            >
              <i className={`bi ${confirmIcon} me-1`} />
              {confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
