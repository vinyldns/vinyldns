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
import { useNavigate } from "react-router-dom";
import { DnsChangeForm } from "../components/dnsChanges/DnsChangeForm";
import { useDnsChanges } from "../hooks/useDnsChanges";
import { useAlerts } from "../contexts/AlertContext";
import { useBreadcrumbs } from "../contexts/BreadcrumbContext";
import type { CreateDnsChangeRequest } from "../types/dnsChange";

/**
 * Page for creating a new batch DNS change.
 *
 * Delegates form rendering to `DnsChangeForm` and handles the two-phase API
 * response: a clean 2xx navigates away, while a 400 with an array body routes
 * per-row validation errors back into the form without a full page reload.
 * This mirrors the legacy portal's `formatErrors` behavior so users see
 * inline feedback on the exact row that failed server-side validation.
 */
export function DnsChangeNewPage() {
  const navigate = useNavigate();
  const { createBatchChange, isSubmitting } = useDnsChanges();
  const { addAlert } = useAlerts();
  const { setCrumbs } = useBreadcrumbs();
  // Stores per-row validation errors returned by the API on a 400 response.
  // Each index maps to a change row in the form; an empty array means no
  // errors for that row.
  const [serverRowErrors, setServerRowErrors] = useState<string[][]>([]);
  // Guards navigation away from the form so an in-progress batch isn't
  // discarded by an accidental Back click.
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [hasUnsaved, setHasUnsaved] = useState(false);
  const [isDark, setIsDark] = useState<boolean>(
    () => document.documentElement.getAttribute("data-vds-theme") === "dark",
  );

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(
        document.documentElement.getAttribute("data-vds-theme") === "dark",
      );
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-vds-theme"],
    });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    setCrumbs([
      { label: "DNS Changes", to: "/dnschanges" },
      { label: "New Batch Change" },
    ]);
    return () => setCrumbs(null);
  }, [setCrumbs]);

  // Handle browser back button
  useEffect(() => {
    const handlePopState = () => {
      if (hasUnsaved) {
        window.history.pushState(null, "", window.location.href);
        setShowLeaveConfirm(true);
      }
    };

    window.addEventListener("popstate", handlePopState);
    return () => {
      window.removeEventListener("popstate", handlePopState);
    };
  }, [hasUnsaved]);

  // Handle breadcrumb navigation with unsaved changes protection
  useEffect(() => {
    const handleLinkClick = (e: MouseEvent) => {
      // Check if click is on a breadcrumb link
      const target = (e.target as Element).closest("a[href]");
      if (!target) return;

      // Check if this is a breadcrumb link
      const isBreadcrumb =
        target.classList.contains("vds-topbar__crumb-link") ||
        target.classList.contains("vds-topbar__home-link");
      if (!isBreadcrumb) return;

      // If we have unsaved changes, prevent navigation and show modal
      if (hasUnsaved) {
        e.preventDefault();
        e.stopPropagation();
        setShowLeaveConfirm(true);
        return;
      }
    };

    document.addEventListener("click", handleLinkClick, true);
    return () => {
      document.removeEventListener("click", handleLinkClick, true);
    };
  }, [hasUnsaved]);

  const requestLeave = () => {
    if (hasUnsaved) {
      setShowLeaveConfirm(true);
    } else {
      navigate("/dnschanges");
    }
  };

  const handleSubmit = (
    data: CreateDnsChangeRequest,
    allowManualReview: boolean,
  ) => {
    setServerRowErrors([]);
    createBatchChange(
      { data, allowManualReview },
      {
        onSuccess: () => void navigate("/dnschanges"),
        onError: (err: unknown) => {
          const error = err as {
            response?: { status?: number; data?: unknown };
          };
          const status = error.response?.status;
          const responseData = error.response?.data;

          // 400 with an array body means the API returned per-change validation
          // errors. Surface them inline rather than showing a generic alert so
          // the user can fix the exact row without re-entering the whole form.
          if (status === 400 && Array.isArray(responseData)) {
            const perRow = (responseData as Array<{ errors?: string[] }>).map(
              (change) => change.errors ?? [],
            );
            setServerRowErrors(perRow);
            const hasErrors = perRow.some((e) => e.length > 0);
            if (hasErrors) {
              addAlert(
                "danger",
                "Errors found in one or more rows. Please correct and resubmit.",
              );
            }
          }
        },
      },
    );
  };

  return (
    <div>
      <div className="rounded-3 mb-3 d-flex justify-content-between align-items-center vds-page-header">
        <div className="d-flex align-items-center gap-3">
          <div className="rounded-3 d-flex align-items-center justify-content-center vds-page-header__icon">
            <i className="bi bi-plus-circle-fill text-white fs-5" />
          </div>
          <div>
            <h4 className="mb-0 fw-bold vds-page-header__title">
              New Batch Change
            </h4>
            <small className="text-muted">
              Submit a new DNS batch change request for review and processing
            </small>
          </div>
        </div>
        <button
          type="button"
          className="btn btn-sm d-flex align-items-center gap-2 vds-btn-flat"
          onClick={requestLeave}
        >
          <i className="bi bi-arrow-left" />
          <span className="vds-btn-flat__label">Back to DNS Changes</span>
        </button>
      </div>

      <DnsChangeForm
        onSubmit={handleSubmit}
        onCancel={() => navigate("/dnschanges")}
        isSubmitting={isSubmitting}
        serverRowErrors={serverRowErrors}
        onUnsavedChange={setHasUnsaved}
      />

      {showLeaveConfirm && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="leave-confirm-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowLeaveConfirm(false);
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
              background: isDark ? "#1e293b" : "#ffffff",
              border: `1px solid ${isDark ? "#2d4163" : "#e8ecf0"}`,
              borderRadius: "0.85rem",
              boxShadow: "0 25px 60px rgba(0,0,0,0.4)",
              width: "min(440px, 100%)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.85rem",
                padding: "1.1rem 1.4rem",
                borderBottom: `1px solid ${isDark ? "#2d4163" : "#e8ecf0"}`,
                background: isDark
                  ? "linear-gradient(90deg,#1e293b,#162035)"
                  : "linear-gradient(90deg,#ffffff,#f8fafd)",
              }}
            >
              <span
                style={{
                  width: 38,
                  height: 38,
                  borderRadius: "50%",
                  background: "#fff7e0",
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
                  id="leave-confirm-title"
                  style={{
                    margin: 0,
                    fontSize: "1rem",
                    fontWeight: 700,
                    color: isDark ? "#e2e8f0" : "#0d1b3e",
                  }}
                >
                  Discard batch change?
                </h6>
                <div
                  style={{
                    marginTop: 2,
                    fontSize: "0.75rem",
                    color: isDark ? "#94a3b8" : "#64748b",
                  }}
                >
                  Any changes you have entered will be lost
                </div>
              </div>
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "0.6rem",
                padding: "0.9rem 1.4rem",
                borderTop: `1px solid ${isDark ? "#2d4163" : "#e8ecf0"}`,
                background: isDark ? "#162032" : "#f8fafd",
              }}
            >
              <button
                type="button"
                onClick={() => setShowLeaveConfirm(false)}
                style={{
                  padding: "0.5rem 1.1rem",
                  background: "transparent",
                  border: `1px solid ${isDark ? "#2d4163" : "#d4dbe8"}`,
                  color: isDark ? "#94a3b8" : "#334155",
                  borderRadius: "0.5rem",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  fontWeight: 500,
                }}
              >
                Keep editing
              </button>
              <button
                type="button"
                onClick={() => navigate("/dnschanges")}
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
                }}
              >
                <i className="bi bi-trash3-fill" />
                Discard changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
