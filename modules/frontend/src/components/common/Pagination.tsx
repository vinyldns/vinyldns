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

import React from 'react';

interface PaginationProps {
  onPrev: () => void;
  onNext: () => void;
  prevEnabled: boolean;
  nextEnabled: boolean;
  panelTitle?: string;
}

export function Pagination({ onPrev, onNext, prevEnabled, nextEnabled, panelTitle }: PaginationProps) {
  return (
    <div className="d-flex align-items-center gap-2 mt-3">
      {panelTitle && <span className="text-muted small">{panelTitle}</span>}
      <button
        className="btn btn-outline-secondary btn-sm"
        onClick={onPrev}
        disabled={!prevEnabled}
      >
        <i className="bi bi-chevron-left" /> Previous
      </button>
      <button
        className="btn btn-outline-secondary btn-sm"
        onClick={onNext}
        disabled={!nextEnabled}
      >
        Next <i className="bi bi-chevron-right" />
      </button>
    </div>
  );
}
