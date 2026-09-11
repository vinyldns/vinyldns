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

interface PaginationProps {
  onPrev: () => void;
  onNext: () => void;
  prevEnabled: boolean;
  nextEnabled: boolean;
  panelTitle?: string;
  rangeLabel?: string;
  totalCount?: number;
}

interface PaginatedSectionProps extends PaginationProps {
  /** When false the pagination bars are hidden entirely */
  show: boolean;
  /** When false, hides the bottom pager and keeps only the top pager. */
  showBottom?: boolean;
  children: React.ReactNode;
}

/**
 * Renders a top Pagination bar, then children, then a bottom Pagination bar.
 * Both bars are only rendered when `show` is true.
 */
export function PaginatedSection({
  show,
  showBottom = true,
  children,
  ...paginationProps
}: PaginatedSectionProps) {
  return (
    <>
      {show && <Pagination {...paginationProps} />}
      {children}
      {show && showBottom && <Pagination {...paginationProps} />}
    </>
  );
}

export function Pagination({
  onPrev,
  onNext,
  prevEnabled,
  nextEnabled,
  panelTitle,
  rangeLabel,
  totalCount,
}: PaginationProps) {
  const label = rangeLabel ?? panelTitle;
  // Build the displayed label — append " of <total>" when a totalCount is given.
  const displayLabel = label
    ? totalCount != null
      ? `${label} of ${totalCount}`
      : label
    : undefined;

  return (
    <div
      className="d-flex justify-content-end align-items-center gap-2 my-2"
      style={{ minHeight: 32 }}
    >
      {prevEnabled && (
        <button className="vds-pagination-btn" onClick={onPrev}>
          <i className="bi bi-chevron-left" style={{ fontSize: "0.75rem" }} />
          <span>Previous</span>
        </button>
      )}
      {displayLabel && (
        <span className="vds-pagination-label">
          {!rangeLabel && (
            <i
              className="bi bi-layers"
              style={{ fontSize: "0.7rem", marginRight: 3 }}
            />
          )}
          {displayLabel}
        </span>
      )}
      {nextEnabled && (
        <button className="vds-pagination-btn" onClick={onNext}>
          <span>Next</span>
          <i className="bi bi-chevron-right" style={{ fontSize: "0.75rem" }} />
        </button>
      )}
    </div>
  );
}
