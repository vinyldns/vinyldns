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

import React, { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { DnsChangesTable } from "../components/dnsChanges/DnsChangesTable";
import { PaginatedSection } from "../components/common/Pagination";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { TimeFilterDropdown } from "../components/common/TimeFilterDropdown";
import type { TimeRange } from "../components/common/TimeFilterDropdown";
import { useDnsChanges } from "../hooks/useDnsChanges";
import { useProfile } from "../contexts/ProfileContext";
import { useAlerts } from "../contexts/AlertContext";
import { dnsChangeService } from "../services/dnsChangeService";
import { formatDateTime } from "../utils/dateUtils";
import type { BatchChangeCount, DnsChangeSummary } from "../types/dnsChange";
import type { PagingState } from "../types/common";

/** Returns true when the document is currently using the dark VDS theme. */
function isDarkTheme(): boolean {
  return (
    document.documentElement.getAttribute("data-vds-theme") === "dark" ||
    window.matchMedia("(prefers-color-scheme: dark)").matches
  );
}

/**
 * DNS Changes page — lists batch change requests submitted to VinylDNS.
 *
 * Regular users see only their own requests. Super/support users get an
 * additional "All Requests" tab with extra filters (submitter, date range,
 * approval status). The two views reuse the same `useDnsChanges` hook by
 * toggling the `ignoreAccess` flag, which maps to the API's `ignoreAccess`
 * query parameter.
 */
export function DnsChangesPage() {
  const { profile } = useProfile();
  const location = useLocation();
  const navigate = useNavigate();
  const savedState = location.state as {
    tab?: "my" | "all";
    paging?: PagingState;
  } | null;

  const canReview = Boolean(profile?.isSuper || profile?.isSupport);

  const [activeTab, setActiveTab] = useState<"my" | "all">(
    savedState?.tab ?? "my",
  );
  const ignoreAccess = activeTab === "all";

  const [submitterName, setSubmitterName] = useState("");
  const [approvalStatus, setApprovalStatus] = useState("");

  // ── Toolbar visibility ───────────────────────────────────────────────────
  const [showCards, setShowCards] = useState(true);

  // ── Time filter (client-side) ─────────────────────────────────────────────
  const [changeTimeRange, setChangeTimeRange] = useState<TimeRange>("all");
  const [changeDateFrom, setChangeDateFrom] = useState("");
  const [changeDateTo, setChangeDateTo] = useState("");

  const {
    dnsChanges,
    isLoading,
    isFetching,
    refetch,
    nextPage,
    prevPage,
    nextPageEnabled,
    prevPageEnabled,
    getPanelTitle,
    cancelBatchChange,
    createBatchChange,
    isSubmitting,
    pageSize,
    setPageSize,
    pageSizes,
    currentPage,
    paging,
  } = useDnsChanges(
    ignoreAccess,
    ignoreAccess ? submitterName || undefined : undefined,
    approvalStatus || undefined,
    undefined,
    undefined,
    savedState?.paging,
  );

  const { addAlert } = useAlerts();

  const handleCancel = (change: DnsChangeSummary) => {
    setCancelTarget(change);
  };

  const [cancelTarget, setCancelTarget] = useState<DnsChangeSummary | null>(
    null,
  );

  const handleConfirmCancel = () => {
    if (!cancelTarget) return;
    cancelBatchChange(cancelTarget.id);
    setCancelTarget(null);
  };

  // Dedicated count query — calls GET /dnschange/count which
  // uses SQL COUNT queries server-side. No page-size limit; returns exact
  // totals for all matching batch changes without fetching full records.
  const { data: countData, isLoading: isCountLoading } =
    useQuery<BatchChangeCount>({
      queryKey: [
        "dnschanges-count",
        ignoreAccess,
        ignoreAccess ? submitterName : undefined,
        approvalStatus,
        undefined,
        undefined,
      ],
      queryFn: async () => {
        const res = await dnsChangeService.getBatchChangeCount(
          ignoreAccess,
          approvalStatus || undefined,
          ignoreAccess ? submitterName || undefined : undefined,
          undefined,
          undefined,
        );
        return res.data;
      },
      retry: false, // don't retry on 404 (endpoint not yet deployed)
      refetchOnWindowFocus: false, // don't refetch when switching tabs
      staleTime: 120_000, // reuse cached counts for 2 min between navigations
      gcTime: 180_000, // keep in memory for 3 min after last subscriber
    });

  const currentUserId = profile?.id ?? "";

  const cardComplete = countData?.complete ?? 0;
  const cardFailed = countData?.failed ?? 0;
  const cardPartialFailure = countData?.partialFailure ?? 0;
  const cardRejected = countData?.rejected ?? 0;
  const cardCancelled = countData?.cancelled ?? 0;
  const cardPendingReview = countData?.pendingReview ?? 0;
  const cardScheduled = countData?.scheduled ?? 0;
  const cardPendingProcessing = countData?.pendingProcessing ?? 0;
  const cardTotal = countData?.total ?? 0;
  const cardPendingTotal =
    cardPendingReview + cardScheduled + cardPendingProcessing;
  const cardIssuesTotal = cardFailed + cardPartialFailure + cardRejected;
  const cardSuccessRate =
    cardTotal > 0 ? Math.round((cardComplete / cardTotal) * 100) : 0;
  const cardPendingPct =
    cardTotal > 0 ? Math.round((cardPendingTotal / cardTotal) * 100) : 0;
  const cardIssuesPct =
    cardTotal > 0 ? Math.round((cardIssuesTotal / cardTotal) * 100) : 0;

  const isCardsLoading = isCountLoading;

  // ── Client-side time filter (same logic as ZonesPage) ───────────────────────
  const isWithinRange = (
    dateStr: string | undefined,
    range: TimeRange,
    from: string,
    to: string,
  ): boolean => {
    if (range === "all") return true;
    if (!dateStr) return true;
    const ts = new Date(dateStr).getTime();
    const now = Date.now();
    if (range === "1d") return ts >= now - 86400000;
    if (range === "7d") return ts >= now - 7 * 86400000;
    if (range === "30d") return ts >= now - 30 * 86400000;
    if (range === "90d") return ts >= now - 90 * 86400000;
    if (range === "custom") {
      if (from && ts < new Date(from + "T00:00:00").getTime()) return false;
      if (to && ts > new Date(to + "T23:59:59").getTime()) return false;
    }
    return true;
  };
  const displayedChanges =
    changeTimeRange !== "all"
      ? dnsChanges.filter((c) =>
          isWithinRange(
            (c as unknown as Record<string, string>).createdTimestamp,
            changeTimeRange,
            changeDateFrom,
            changeDateTo,
          ),
        )
      : dnsChanges;

  const skeletonBlue = (
    <span className="vds-insight-skeleton vds-insight-skeleton--blue" />
  );
  const skeletonTeal = (
    <span className="vds-insight-skeleton vds-insight-skeleton--teal" />
  );
  const skeletonAmber = (
    <span className="vds-insight-skeleton vds-insight-skeleton--amber" />
  );
  const skeletonPurple = (
    <span className="vds-insight-skeleton vds-insight-skeleton--purple" />
  );

  return (
    <div>
      <div className="rounded-3 mb-4 d-flex justify-content-between align-items-center vds-page-header">
        <div className="d-flex align-items-center gap-3">
          <div className="rounded-3 d-flex align-items-center justify-content-center vds-page-header__icon">
            <i className="bi bi-list-ol text-white fs-5" />
          </div>
          <div>
            <h4 className="mb-0 fw-bold vds-page-header__title">DNS Changes</h4>
            <small className="text-muted">
              View and manage batch DNS change requests
            </small>
          </div>
        </div>
        <button
          type="button"
          className="btn btn-primary d-flex align-items-center gap-2 vds-btn-primary-shadow vds-btn-nav"
          onClick={() => void navigate("/dnschanges/new")}
        >
          <i className="bi bi-plus-circle-fill" />
          New DNS Change
        </button>
      </div>

      <div className="card mb-3 vds-toolbar-card">
        <div className="card-body py-2 px-3">
          {/* ── Toolbar: request scope left; filters and actions right ─────── */}
          <div
            className="d-flex align-items-center gap-2"
            style={{ minWidth: 0, width: "100%" }}
          >
            {canReview && (
              <div className="vds-pill-toggle" style={{ flex: "0 0 auto" }}>
                <button
                  type="button"
                  className={`vds-pill-toggle__btn${activeTab === "my" ? " vds-pill-toggle__btn--active" : ""}`}
                  onClick={() => setActiveTab("my")}
                >
                  <i className="bi bi-person-fill" />
                  My Requests
                </button>
                <button
                  type="button"
                  className={`vds-pill-toggle__btn${activeTab === "all" ? " vds-pill-toggle__btn--active" : ""}`}
                  onClick={() => setActiveTab("all")}
                >
                  <i className="bi bi-people-fill" />
                  All Requests
                </button>
              </div>
            )}

            <div
              className="ms-auto d-flex align-items-center justify-content-end gap-2"
              style={{ minWidth: 0, flex: "1 1 auto", overflow: "hidden" }}
            >
              {ignoreAccess && (
                <div
                  className="vds-search-group input-group input-group-sm"
                  style={{
                    flex: "1 1 120px",
                    minWidth: 120,
                    overflow: "hidden",
                  }}
                >
                  <span className="input-group-text border-0 bg-transparent pe-1">
                    <i className="bi bi-person text-muted" />
                  </span>
                  <input
                    type="text"
                    className="form-control border-0 ps-0 shadow-none bg-transparent"
                    placeholder="Search by username"
                    value={submitterName}
                    onChange={(e) => setSubmitterName(e.target.value)}
                  />
                  {submitterName && (
                    <button
                      type="button"
                      className="input-group-text border-0 bg-transparent pe-1"
                      style={{ cursor: "pointer" }}
                      onClick={() => setSubmitterName("")}
                    >
                      <i className="bi bi-x text-muted" />
                    </button>
                  )}
                </div>
              )}

              <button
                type="button"
                className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${approvalStatus === "PendingReview" ? " vds-btn-flat--active" : ""}`}
                onClick={() =>
                  setApprovalStatus(
                    approvalStatus === "PendingReview" ? "" : "PendingReview",
                  )
                }
                style={{ flex: "0 0 auto", whiteSpace: "nowrap" }}
              >
                <i className="bi bi-hourglass-split" />
                <span className="vds-btn-flat__label">Open Only</span>
                {approvalStatus === "PendingReview" && (
                  <span className="vds-filter-chip--accent">On</span>
                )}
              </button>

              <TimeFilterDropdown
                value={changeTimeRange}
                dateFrom={changeDateFrom}
                dateTo={changeDateTo}
                onChange={setChangeTimeRange}
                onDateFromChange={setChangeDateFrom}
                onDateToChange={setChangeDateTo}
              />

              {canReview && (
                <button
                  type="button"
                  className="vds-cards-toggle-btn"
                  onClick={() => setShowCards((v) => !v)}
                  style={{ flex: "0 0 auto", whiteSpace: "nowrap" }}
                >
                  <span className="vds-cards-toggle-btn__icon">
                    <i
                      className={`bi ${showCards ? "bi-grid-fill" : "bi-grid"}`}
                    />
                  </span>
                  <span>{showCards ? "Hide Cards" : "Show Cards"}</span>
                  <span
                    className={`vds-cards-toggle-btn__dot${showCards ? "" : " vds-cards-toggle-btn__dot--off"}`}
                  />
                </button>
              )}
              <button
                type="button"
                className="btn btn-sm vds-btn-flat d-flex align-items-center justify-content-center"
                style={{
                  width: 32,
                  height: 32,
                  padding: 0,
                  flexShrink: 0,
                  borderRadius: "50%",
                }}
                title="Refresh"
                onClick={() => void refetch()}
              >
                <i
                  className="bi bi-arrow-clockwise"
                  style={{ fontSize: "1rem" }}
                />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Insight cards ── */}
      {canReview && showCards && (
        <div className="row g-2 mb-3 align-items-stretch">
          {/* Card 1: Total Requests */}
          <div className="col-6 col-sm-4 col-xl d-flex">
            <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--blue">
              <div className="d-flex align-items-center gap-2 mb-1">
                <div className="rounded-2 vds-insight-icon vds-insight-icon--blue">
                  <i className="bi bi-list-ol" />
                </div>
                <span className="vds-insight-label vds-insight-label--blue">
                  Requests
                  <span className="vds-card-ctx-chip vds-card-ctx-chip--blue ms-1">
                    {ignoreAccess ? "All" : "Mine"}
                  </span>
                </span>
                <span className="vds-insight-value vds-insight-value--blue">
                  {isCardsLoading ? skeletonBlue : cardTotal}
                </span>
              </div>
              <div
                className="vds-insight-body vds-insight-body--blue"
                style={{ rowGap: 6 }}
              >
                <div className="vds-insight-stat-label">Issues</div>
                <div className="vds-insight-stat-label vds-insight-stat-label--right">
                  Pending
                </div>
                <div className="vds-insight-stat-value vds-insight-stat-value--blue">
                  {isCardsLoading ? "…" : cardIssuesTotal}
                </div>
                <div className="vds-insight-stat-value vds-insight-stat-value--blue vds-insight-stat-value--right">
                  {isCardsLoading ? "…" : cardPendingTotal}
                </div>
                <div
                  className="vds-insight-footnote"
                  style={{ gridColumn: "1 / -1" }}
                >
                  <i className="bi bi-check2-circle me-1 vds-icon-blue-dim" />
                  {isCardsLoading
                    ? "…"
                    : `${cardSuccessRate}% success rate · ${cardComplete} complete`}
                </div>
              </div>
            </div>
          </div>

          {/* Card 2: Complete */}
          <div className="col-6 col-sm-4 col-xl d-flex">
            <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--teal">
              <div className="d-flex align-items-center gap-2 mb-1">
                <div className="rounded-2 vds-insight-icon vds-insight-icon--teal">
                  <i className="bi bi-check-circle-fill" />
                </div>
                <span className="vds-insight-label vds-insight-label--teal">
                  Complete
                </span>
                <span className="vds-insight-value vds-insight-value--teal">
                  {isCardsLoading ? skeletonTeal : cardComplete}
                </span>
              </div>
              {!isCardsLoading && cardTotal > 0 ? (
                <>
                  <div
                    className="vds-insight-progress vds-insight-progress--teal"
                    style={{ height: 6, marginBottom: 2 }}
                  >
                    <div
                      className="vds-insight-progress__fill vds-insight-progress__fill--teal"
                      style={{ width: `${cardSuccessRate}%` }}
                    />
                  </div>
                  <div
                    style={{
                      fontSize: "0.67rem",
                      color: "#0ca678",
                      fontWeight: 700,
                      marginBottom: 3,
                    }}
                  >
                    {cardSuccessRate}%{" "}
                    <span style={{ fontWeight: 400, color: "#8099b8" }}>
                      of all requests
                    </span>
                  </div>
                </>
              ) : (
                <div style={{ height: 4 }} />
              )}
              <div className="vds-insight-body vds-insight-body--teal">
                <div className="vds-insight-stat-label">Cancelled</div>
                <div className="vds-insight-stat-label vds-insight-stat-label--right">
                  Rate
                </div>
                <div className="vds-insight-stat-value vds-insight-stat-value--teal">
                  {isCardsLoading ? "…" : cardCancelled}
                </div>
                <div className="vds-insight-stat-value vds-insight-stat-value--teal vds-insight-stat-value--right">
                  {isCardsLoading ? "…" : `${cardSuccessRate}%`}
                </div>
              </div>
            </div>
          </div>

          {/* Card 3: Pending */}
          <div className="col-6 col-sm-4 col-xl d-flex">
            <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--amber">
              <div className="d-flex align-items-center gap-2 mb-1">
                <div className="rounded-2 vds-insight-icon vds-insight-icon--amber">
                  <i className="bi bi-hourglass-split" />
                </div>
                <span className="vds-insight-label vds-insight-label--amber">
                  Pending
                </span>
                <span className="vds-insight-value vds-insight-value--amber">
                  {isCardsLoading ? skeletonAmber : cardPendingTotal}
                </span>
              </div>
              <div
                className="vds-insight-body vds-insight-body--amber"
                style={{ display: "flex", flexDirection: "column", gap: 4 }}
              >
                <div style={{ display: "flex", gap: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Review</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--amber">
                      {isCardsLoading ? "…" : cardPendingReview}
                    </div>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Scheduled</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--amber">
                      {isCardsLoading ? "…" : cardScheduled}
                    </div>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Processing</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--amber">
                      {isCardsLoading ? "…" : cardPendingProcessing}
                    </div>
                  </div>
                </div>
                <div className="vds-insight-footnote">
                  <i className="bi bi-hourglass-split me-1 vds-icon-amber" />
                  {isCardsLoading
                    ? "…"
                    : cardPendingTotal > 0
                      ? `${cardPendingTotal} request${cardPendingTotal === 1 ? "" : "s"} awaiting action`
                      : "No pending requests"}
                </div>
              </div>
            </div>
          </div>

          {/* Card 4: Issues */}
          <div className="col-6 col-sm-4 col-xl d-flex">
            <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--purple">
              <div className="d-flex align-items-center gap-2 mb-1">
                <div className="rounded-2 vds-insight-icon vds-insight-icon--purple">
                  <i className="bi bi-exclamation-triangle-fill" />
                </div>
                <span className="vds-insight-label vds-insight-label--purple">
                  Issues
                </span>
                <span className="vds-insight-value vds-insight-value--purple">
                  {isCardsLoading ? skeletonPurple : cardIssuesTotal}
                </span>
              </div>
              <div
                className="vds-insight-body vds-insight-body--purple"
                style={{ display: "flex", flexDirection: "column", gap: 4 }}
              >
                <div style={{ display: "flex", gap: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Failed</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--purple">
                      {isCardsLoading ? "…" : cardFailed}
                    </div>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Partial</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--purple">
                      {isCardsLoading ? "…" : cardPartialFailure}
                    </div>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div className="vds-insight-stat-label">Rejected</div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--purple">
                      {isCardsLoading ? "…" : cardRejected}
                    </div>
                  </div>
                </div>
                <div className="vds-insight-footnote">
                  <i className="bi bi-exclamation-circle me-1 vds-icon-purple-dim" />
                  {isCardsLoading
                    ? "…"
                    : cardIssuesTotal > 0
                      ? `${cardIssuesTotal} request${cardIssuesTotal === 1 ? "" : "s"} need attention`
                      : "No issues"}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {isLoading || isFetching ? (
        <LoadingSpinner message="Loading changes…" />
      ) : (
        <PaginatedSection
          show={dnsChanges.length > 0}
          onPrev={prevPage}
          onNext={nextPage}
          prevEnabled={prevPageEnabled}
          nextEnabled={nextPageEnabled}
          rangeLabel={
            dnsChanges.length > 0
              ? `${(currentPage - 1) * pageSize + 1}–${(currentPage - 1) * pageSize + dnsChanges.length} of ${cardTotal > 0 ? cardTotal : (currentPage - 1) * pageSize + dnsChanges.length}`
              : undefined
          }
        >
          <DnsChangesTable
            changes={displayedChanges}
            onCancel={handleCancel}
            ignoreAccess={ignoreAccess}
            currentUserId={currentUserId}
            fromTab={activeTab}
            currentPaging={paging}
          />
        </PaginatedSection>
      )}
      {cancelTarget && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="list-cancel-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setCancelTarget(null);
          }}
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(15,23,42,0.65)",
            backdropFilter: "blur(3px)",
            zIndex: 1080,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "1.5rem",
          }}
        >
          <div
            style={{
              background: isDarkTheme() ? "#1e293b" : "#ffffff",
              border: `1px solid ${isDarkTheme() ? "#2d4163" : "#e8ecf0"}`,
              borderRadius: "0.85rem",
              boxShadow: "0 25px 60px rgba(0,0,0,0.45)",
              width: "min(460px, 100%)",
              overflow: "hidden",
            }}
          >
            {/* Header */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.85rem",
                padding: "1.1rem 1.4rem",
                borderTop: `2px solid ${isDarkTheme() ? "#475569" : "#cbd5e1"}`,
                borderBottom: `1px solid ${isDarkTheme() ? "#2d4163" : "#e8ecf0"}`,
                background: isDarkTheme()
                  ? "linear-gradient(90deg,#1e293b,#162032)"
                  : "linear-gradient(90deg,#ffffff,#f8fafd)",
              }}
            >
              <span
                style={{
                  width: 38,
                  height: 38,
                  borderRadius: "50%",
                  background: isDarkTheme() ? "#3b2f0d" : "#fff7e0",
                  color: "#d97706",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "1.05rem",
                  flexShrink: 0,
                }}
              >
                <i className="bi bi-exclamation-triangle-fill" />
              </span>
              <div style={{ flex: 1 }}>
                <h6
                  id="list-cancel-modal-title"
                  style={{
                    margin: 0,
                    fontSize: "1rem",
                    fontWeight: 700,
                    color: isDarkTheme() ? "#e2e8f0" : "#0d1b3e",
                  }}
                >
                  Cancel DNS Change
                </h6>
                <div
                  style={{
                    marginTop: 2,
                    fontSize: "0.75rem",
                    color: isDarkTheme() ? "#94a3b8" : "#64748b",
                  }}
                >
                  This action cannot be undone
                </div>
              </div>
              <button
                type="button"
                onClick={() => setCancelTarget(null)}
                aria-label="Close"
                style={{
                  background: "transparent",
                  border: "none",
                  color: isDarkTheme() ? "#94a3b8" : "#64748b",
                  fontSize: "1rem",
                  cursor: "pointer",
                  padding: "0.25rem 0.5rem",
                  borderRadius: "0.4rem",
                }}
              >
                <i className="bi bi-x-lg" />
              </button>
            </div>

            {/* Body */}
            <div
              style={{
                padding: "1.25rem 1.4rem",
                fontSize: "0.9rem",
                color: isDarkTheme() ? "#cbd5e1" : "#334155",
                lineHeight: 1.6,
              }}
            >
              Are you sure you want to cancel this DNS Change?
              <div
                style={{
                  marginTop: "0.75rem",
                  padding: "0.6rem 0.85rem",
                  background: isDarkTheme() ? "#0f172a" : "#f8fafd",
                  border: `1px solid ${isDarkTheme() ? "#2d4163" : "#e2e8f0"}`,
                  borderRadius: "0.5rem",
                }}
              >
                <div
                  style={{
                    fontFamily: "ui-monospace,SFMono-Regular,Menlo,monospace",
                    fontSize: "0.78rem",
                    color: isDarkTheme() ? "#7fa8d8" : "#1e5fa8",
                    wordBreak: "break-all",
                  }}
                >
                  {cancelTarget.id}
                </div>
                <div
                  style={{
                    marginTop: "0.4rem",
                    fontSize: "0.78rem",
                    color: isDarkTheme() ? "#94a3b8" : "#64748b",
                  }}
                >
                  Submitted {formatDateTime(cancelTarget.createdTimestamp)}
                </div>
                {cancelTarget.comments && (
                  <div
                    style={{
                      marginTop: "0.25rem",
                      fontSize: "0.78rem",
                      color: isDarkTheme() ? "#94a3b8" : "#64748b",
                    }}
                  >
                    {cancelTarget.comments}
                  </div>
                )}
              </div>
            </div>

            {/* Footer */}
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "0.6rem",
                padding: "0.9rem 1.4rem",
                borderTop: `1px solid ${isDarkTheme() ? "#2d4163" : "#e8ecf0"}`,
                background: isDarkTheme() ? "#162032" : "#f8fafd",
              }}
            >
              <button
                type="button"
                onClick={() => setCancelTarget(null)}
                style={{
                  padding: "0.5rem 1.1rem",
                  background: "transparent",
                  border: isDarkTheme()
                    ? "1px solid #4a6fa5"
                    : "1px solid #d4dbe8",
                  color: isDarkTheme() ? "#93c5fd" : "#334155",
                  borderRadius: "0.5rem",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  fontWeight: 500,
                  transition: "all 0.15s ease",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = isDarkTheme()
                    ? "#1e3a5f"
                    : "#f0f4f9";
                  e.currentTarget.style.borderColor = isDarkTheme()
                    ? "#5a82bb"
                    : "#c2c9d3";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = "transparent";
                  e.currentTarget.style.borderColor = isDarkTheme()
                    ? "#4a6fa5"
                    : "#d4dbe8";
                }}
              >
                Keep DNS Change
              </button>
              <button
                type="button"
                onClick={handleConfirmCancel}
                style={{
                  padding: "0.5rem 1.25rem",
                  background: "linear-gradient(135deg,#ef4444,#dc2626)",
                  border: "none",
                  color: "#fff",
                  borderRadius: "0.5rem",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  fontWeight: 600,
                  boxShadow: "0 4px 12px rgba(220,38,38,0.35)",
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  transition: "all 0.15s ease",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow =
                    "0 6px 20px rgba(220,38,38,0.45)";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow =
                    "0 4px 12px rgba(220,38,38,0.35)";
                }}
              >
                <i className="bi bi-x-circle-fill" />
                Cancel DNS Change
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
