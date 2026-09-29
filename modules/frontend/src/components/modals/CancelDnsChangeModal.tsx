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
import { DiscardChangesModal } from "./DiscardChangesModal";
import { formatDateTime } from "../../utils/dateUtils";

export interface CancelDnsChangeModalProps {
  isOpen: boolean;
  changeId: string;
  submittedAt: string;
  comments?: string;
  onClose: () => void;
  onConfirm: () => void;
}

export function CancelDnsChangeModal({
  isOpen,
  changeId,
  submittedAt,
  comments,
  onClose,
  onConfirm,
}: CancelDnsChangeModalProps) {
  return (
    <DiscardChangesModal
      isOpen={isOpen}
      title="Cancel DNS Change"
      description="Are you sure you want to cancel this DNS Change? All pending records in this batch will be cancelled."
      cancelLabel="Keep DNS Change"
      confirmLabel="Cancel DNS Change"
      confirmIcon="bi-x-circle-fill"
      cancelButtonClassName="vds-modal__btn vds-modal__btn--secondary"
      confirmButtonClassName="vds-modal__btn vds-modal__btn--danger"
      onClose={onClose}
      onConfirm={onConfirm}
    >
      <div className="vds-confirm-modal__info-box">
        <div className="vds-confirm-modal__info-id">{changeId}</div>
        <div className="vds-confirm-modal__info-text">
          Submitted {formatDateTime(submittedAt)}
        </div>
        {comments && (
          <div className="vds-confirm-modal__info-text vds-confirm-modal__info-text--sm">
            {comments}
          </div>
        )}
      </div>
    </DiscardChangesModal>
  );
}
