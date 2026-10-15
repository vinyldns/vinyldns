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
import { RecordForm } from "../../components/records/RecordForm";
import type { Group } from "../../types/group";
import type { RecordSet } from "../../types/record";

export interface RecordFormModalProps {
  isOpen: boolean;
  editRecord: RecordSet | null;
  zoneId: string;
  zoneName: string;
  isSharedZone: boolean;
  isReverseZone: boolean;
  groupsData: Group[];
  recordFormRef: React.RefObject<HTMLDivElement | null>;
  onClose: () => void;
  onCreate: (data: Partial<RecordSet>) => void;
  onUpdate: (data: Partial<RecordSet>) => void;
  isCreatePending: boolean;
  isUpdatePending: boolean;
}

export function RecordFormModal({
  isOpen,
  editRecord,
  zoneId,
  zoneName,
  isSharedZone,
  isReverseZone,
  groupsData,
  recordFormRef,
  onClose,
  onCreate,
  onUpdate,
  isCreatePending,
  isUpdatePending,
}: RecordFormModalProps) {
  if (!isOpen) return null;

  return (
    <>
      <div
        className="modal fade show d-block"
        tabIndex={-1}
        role="dialog"
        onClick={(e) => {
          if (e.target === e.currentTarget) onClose();
        }}
      >
        <div
          className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable"
          role="document"
        >
          <div className="modal-content" ref={recordFormRef}>
            <div className="rhm-header">
              <div className="d-flex align-items-center justify-content-between gap-3 min-h-100">
                <div className="d-flex align-items-center gap-3 min-w-0">
                  <div className="min-w-0">
                    <h5 className="m-0 fw-semibold text-white d-flex align-items-center gap-2">
                      <i
                        className={`bi ${editRecord ? "bi-pencil-square" : "bi-plus-circle"}`}
                      />
                      {editRecord
                        ? `Edit: ${editRecord.name} (${editRecord.type})`
                        : "Add DNS Record"}
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
              <RecordForm
                zoneId={zoneId}
                zoneName={zoneName}
                initialData={editRecord ?? undefined}
                onSubmit={editRecord ? onUpdate : onCreate}
                onCancel={onClose}
                mode={editRecord ? "edit" : "create"}
                isSharedZone={isSharedZone}
                isReverseZone={isReverseZone}
                isLoading={editRecord ? isUpdatePending : isCreatePending}
                allGroups={groupsData}
              />
            </div>
          </div>
        </div>
      </div>
      <div
        className="modal-backdrop fade show"
        style={{
          backdropFilter: "blur(4px)",
          WebkitBackdropFilter: "blur(4px)",
          opacity: 0.7,
        }}
      />
    </>
  );
}
