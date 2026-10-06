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
import { useState, useRef, useEffect } from "react";
import ReactDOM from "react-dom";

export type TimeRange = "all" | "1d" | "7d" | "30d" | "90d" | "custom";

interface TimeFilterDropdownProps {
  value: TimeRange;
  dateFrom: string;
  dateTo: string;
  onChange: (range: TimeRange) => void;
  onDateFromChange: (v: string) => void;
  onDateToChange: (v: string) => void;
}

const LABELS: Record<TimeRange, string> = {
  all: "All Time",
  "1d": "Today",
  "7d": "Last 7 Days",
  "30d": "Last 30 Days",
  "90d": "Last 90 Days",
  custom: "Custom Range",
};

const GRID: {
  range: TimeRange;
  icon: string;
  label: string;
  desc: string;
}[][] = [
  [
    {
      range: "all",
      icon: "bi-infinity",
      label: "All Time",
      desc: "No restriction",
    },
    { range: "1d", icon: "bi-sun", label: "Today", desc: "Last 24 hours" },
  ],
  [
    {
      range: "7d",
      icon: "bi-calendar-week",
      label: "Last 7 Days",
      desc: "Past week",
    },
    {
      range: "30d",
      icon: "bi-calendar-month",
      label: "Last 30 Days",
      desc: "Past month",
    },
  ],
  [
    {
      range: "90d",
      icon: "bi-calendar3-range",
      label: "Last 90 Days",
      desc: "Past 3 months",
    },
    {
      range: "custom",
      icon: "bi-calendar-range",
      label: "Custom Range",
      desc: "Pick dates",
    },
  ],
];

export function TimeFilterDropdown({
  value,
  dateFrom,
  dateTo,
  onChange,
  onDateFromChange,
  onDateToChange,
}: TimeFilterDropdownProps) {
  const [open, setOpen] = useState(false);
  const [panelPos, setPanelPos] = useState({ top: 0, right: 0 });
  const ref = useRef<HTMLDivElement>(null);
  const btnRef = useRef<HTMLButtonElement>(null);

  // Recalculate position from the button's live viewport rect
  const updatePos = () => {
    if (btnRef.current) {
      const r = btnRef.current.getBoundingClientRect();
      setPanelPos({ top: r.bottom + 6, right: window.innerWidth - r.right });
    }
  };

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (
        ref.current &&
        !ref.current.contains(target) &&
        !target.closest("[data-time-filter-panel]")
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // Track scroll/resize while open so panel follows the button
  useEffect(() => {
    if (!open) return;
    const onScroll = () => updatePos();
    const onResize = () => updatePos();
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", onResize);
    };
  }, [open]);

  const isActive = value !== "all";
  
  const btnLabel =
    value === "custom" && (dateFrom || dateTo)
      ? `${dateFrom || "…"} – ${dateTo || "…"}`
      : LABELS[value];

  const renderCell = (opt: {
    range: TimeRange;
    icon: string;
    label: string;
    desc: string;
  }) => {
    const selected = value === opt.range;
    return (
      <button
        key={opt.range}
        type="button"
        onClick={() => {
          onChange(opt.range);
          if (opt.range !== "custom") setOpen(false);
        }}
        className={`vds-time-filter-opt ${selected ? "vds-time-filter-opt--active" : ""}`}
      >
        <div className="vds-time-filter-icon-wrap">
          <i className={`bi ${opt.icon} vds-time-filter-icon custom-icon`} />
        </div>
        <span className="vds-time-filter-label">
          {opt.label}
        </span>
        {selected && (
          <i className="bi bi-check-circle-fill vds-time-filter-check" />
        )}
      </button>
    );
  };

  return (
    <div
      ref={ref}
      className="position-relative"
      style={{ display: "inline-block" }}
    >
      {/* ── Trigger button ── */}
      <button
        ref={btnRef}
        type="button"
        className={`btn btn-sm d-flex align-items-center gap-2 vds-btn-flat${isActive ? " vds-btn-flat--active" : ""}`}
        style={{
          height: 30,
          fontSize: "0.8rem",
          paddingLeft: 10,
          paddingRight: 8,
          fontWeight: isActive ? 600 : 400,
        }}
        onClick={() => {
          if (!open) updatePos();
          setOpen((o) => !o);
        }}
      >
        <i className="bi bi-calendar3" style={{ fontSize: "0.78rem" }} />
        <span style={{ whiteSpace: "nowrap" }}>{btnLabel}</span>
        {isActive && (
          <span
            className="vds-filter-chip--accent"
            style={{ fontSize: "0.62rem", padding: "1px 5px", lineHeight: 1.5 }}
          >
            Active
          </span>
        )}
        <i
          className={`bi bi-chevron-${open ? "up" : "down"}`}
          style={{ fontSize: "0.6rem", opacity: 0.55, marginLeft: 2 }}
        />
      </button>

      {/* ── Dropdown panel — rendered in body via portal to escape overflow clipping ── */}
      {open &&
        ReactDOM.createPortal(
          <div
            data-time-filter-panel
            className="vds-time-filter-panel"
            style={{
              top: panelPos.top,
              right: panelPos.right,
            }}
          >
            {/* Header */}
            <div className="vds-time-filter-header">
              <i className="bi bi-calendar3 vds-time-filter-header-icon" />
              <span className="vds-time-filter-header-title">TIME FILTER</span>
            </div>

            {/* 2-column grid of option cards */}
            <div className="vds-time-filter-grid">
              {GRID.map((row, ri) => (
                <div key={ri} className="vds-time-filter-row">
                  {row.map(renderCell)}
                </div>
              ))}
            </div>

            {/* Custom date range – expands below grid when Custom is selected */}
            {value === "custom" && (
              <div className="vds-time-filter-custom-wrap">
                <div className="vds-time-filter-custom-title">
                  <i className="bi bi-calendar-range vds-time-filter-custom-title-icon" />
                  DATE RANGE
                </div>
                <div style={{ display: "flex", alignItems: "flex-end", gap: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div className="vds-time-filter-input-label">From</div>
                    <input
                      type="date"
                      className="form-control form-control-sm vds-time-filter-input"
                      value={dateFrom}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => onDateFromChange(e.target.value)}
                    />
                  </div>
                  <div className="vds-time-filter-divider">–</div>
                  <div style={{ flex: 1 }}>
                    <div className="vds-time-filter-input-label">To</div>
                    <input
                      type="date"
                      className="form-control form-control-sm vds-time-filter-input"
                      value={dateTo}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => onDateToChange(e.target.value)}
                    />
                  </div>
                </div>
                {(dateFrom || dateTo) && (
                  <button
                    type="button"
                    className="vds-time-filter-btn-apply"
                    onClick={() => setOpen(false)}
                  >
                    <i className="bi bi-check-lg" />
                    Apply Range
                  </button>
                )}
              </div>
            )}

            {/* Clear footer */}
            {isActive && (
              <div className="vds-time-filter-footer">
                <button
                  type="button"
                  className="vds-time-filter-btn-clear"
                  onClick={() => {
                    onChange("all");
                    onDateFromChange("");
                    onDateToChange("");
                    setOpen(false);
                  }}
                >
                  <i className="bi bi-x-circle" />
                  Clear Filter
                </button>
              </div>
            )}
          </div>,
          document.body,
        )}
    </div>
  );
}