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

import React, { useState, useRef, useEffect } from 'react';

export type TimeRange = 'all' | '1d' | '7d' | '30d' | '90d' | 'custom';

interface TimeFilterDropdownProps {
  value: TimeRange;
  dateFrom: string;
  dateTo: string;
  onChange: (range: TimeRange) => void;
  onDateFromChange: (v: string) => void;
  onDateToChange: (v: string) => void;
}

const LABELS: Record<TimeRange, string> = {
  all:    'All Time',
  '1d':   'Today',
  '7d':   'Last 7 Days',
  '30d':  'Last 30 Days',
  '90d':  'Last 90 Days',
  custom: 'Custom Range',
};

// 3 rows × 2 cols
const GRID: { range: TimeRange; icon: string; label: string; desc: string }[][] = [
  [
    { range: 'all',    icon: 'bi-infinity',       label: 'All Time',      desc: 'No restriction' },
    { range: '1d',     icon: 'bi-sun',            label: 'Today',         desc: 'Last 24 hours'  },
  ],
  [
    { range: '7d',     icon: 'bi-calendar-week',  label: 'Last 7 Days',   desc: 'Past week'      },
    { range: '30d',    icon: 'bi-calendar-month', label: 'Last 30 Days',  desc: 'Past month'     },
  ],
  [
    { range: '90d',    icon: 'bi-calendar3-range', label: 'Last 90 Days', desc: 'Past 3 months'  },
    { range: 'custom', icon: 'bi-calendar-range',  label: 'Custom Range', desc: 'Pick dates'     },
  ],
];

export function TimeFilterDropdown({
  value, dateFrom, dateTo, onChange, onDateFromChange, onDateToChange,
}: TimeFilterDropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const isActive = value !== 'all';
  const btnLabel =
    value === 'custom' && (dateFrom || dateTo)
      ? `${dateFrom || '…'} – ${dateTo || '…'}`
      : LABELS[value];

  // ── Renders one option card cell ──────────────────────────────────────────
  const renderCell = (opt: { range: TimeRange; icon: string; label: string; desc: string }) => {
    const selected = value === opt.range;
    return (
      <button
        key={opt.range}
        type="button"
        onClick={() => { onChange(opt.range); if (opt.range !== 'custom') setOpen(false); }}
        style={{
          flex: '1 1 0',
          border: selected ? '1.5px solid rgba(46,80,144,0.45)' : '1px solid rgba(46,80,144,0.1)',
          borderRadius: '0.5rem',
          background: selected
            ? 'linear-gradient(135deg,rgba(30,58,95,0.11) 0%,rgba(46,80,144,0.16) 100%)'
            : 'rgba(246,248,252,0.95)',
          padding: '5px 4px 4px',
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          gap: 6,
          cursor: 'pointer',
          transition: 'all 0.14s',
          boxShadow: selected ? '0 1px 6px rgba(46,80,144,0.15)' : 'none',
          outline: 'none',
          position: 'relative',
        }}
        onMouseEnter={(e) => {
          if (!selected) (e.currentTarget as HTMLButtonElement).style.background = 'rgba(46,80,144,0.07)';
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLButtonElement).style.background = selected
            ? 'linear-gradient(135deg,rgba(30,58,95,0.11) 0%,rgba(46,80,144,0.16) 100%)'
            : 'rgba(246,248,252,0.95)';
        }}
      >
        <div style={{
          width: 20, height: 20, borderRadius: '50%',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: selected ? 'linear-gradient(135deg,#1e3a5f 0%,#2e5090 100%)' : 'rgba(46,80,144,0.1)',
          flexShrink: 0,
        }}>
          <i className={`bi ${opt.icon}`} style={{ fontSize: '0.62rem', color: selected ? '#c8deff' : '#506080' }} />
        </div>
        <span style={{ fontSize: '0.7rem', fontWeight: selected ? 700 : 500, color: selected ? '#1e3a5f' : '#344563', lineHeight: 1.2, whiteSpace: 'nowrap' }}>
          {opt.label}
        </span>
        {selected && (
          <i className="bi bi-check-circle-fill" style={{
            marginLeft: 'auto', fontSize: '0.58rem', color: '#2e5090', flexShrink: 0,
          }} />
        )}
      </button>
    );
  };

  return (
    <div ref={ref} className="position-relative" style={{ display: 'inline-block' }}>
      {/* ── Trigger button ── */}
      <button
        type="button"
        className={`btn btn-sm d-flex align-items-center gap-2 vds-btn-flat${isActive ? ' vds-btn-flat--active' : ''}`}
        style={{ height: 30, fontSize: '0.8rem', paddingLeft: 10, paddingRight: 8, fontWeight: isActive ? 600 : 400 }}
        onClick={() => setOpen((o) => !o)}
      >
        <i className="bi bi-calendar3" style={{ fontSize: '0.78rem', color: isActive ? '#2e5090' : undefined }} />
        <span style={{ whiteSpace: 'nowrap' }}>{btnLabel}</span>
        {isActive && (
          <span className="vds-filter-chip--accent" style={{ fontSize: '0.62rem', padding: '1px 5px', lineHeight: 1.5 }}>
            Active
          </span>
        )}
        <i className={`bi bi-chevron-${open ? 'up' : 'down'}`} style={{ fontSize: '0.6rem', opacity: 0.55, marginLeft: 2 }} />
      </button>

      {/* ── Dropdown panel ── */}
      {open && (
        <div style={{
          position: 'absolute',
          top: 'calc(100% + 6px)',
          right: 0,
          zIndex: 1200,
          width: 320,
          background: '#fff',
          borderRadius: '0.9rem',
          boxShadow: '0 12px 40px rgba(20,40,90,0.2), 0 2px 8px rgba(0,0,0,0.07)',
          border: '1px solid #d4dbe8',
          overflow: 'hidden',
        }}>
          {/* Gradient header */}
          <div style={{
            background: 'linear-gradient(90deg,#1e3a5f 0%,#2e5090 100%)',
            padding: '10px 14px 9px',
            display: 'flex', alignItems: 'center', gap: 7,
          }}>
            <i className="bi bi-calendar3" style={{ color: '#c8deff', fontSize: '0.75rem' }} />
            <span style={{ color: '#c8deff', fontSize: '0.7rem', fontWeight: 700, letterSpacing: '0.08em' }}>
              TIME FILTER
            </span>
          </div>

          {/* 2-column grid of option cards */}
          <div style={{ padding: '8px 10px 4px', display: 'flex', flexDirection: 'column', gap: 4 }}>
            {GRID.map((row, ri) => (
              <div key={ri} style={{ display: 'flex', gap: 4 }}>
                {row.map(renderCell)}
              </div>
            ))}
          </div>

          {/* Custom date range – expands below grid when Custom is selected */}
          {value === 'custom' && (
            <div style={{
              margin: '6px 12px 8px',
              padding: '11px 13px',
              background: 'rgba(46,80,144,0.05)',
              borderRadius: '0.6rem',
              border: '1px solid rgba(46,80,144,0.18)',
            }}>
              <div style={{ fontSize: '0.67rem', color: '#506080', fontWeight: 700, marginBottom: 8,
                  letterSpacing: '0.04em', display: 'flex', alignItems: 'center', gap: 5 }}>
                <i className="bi bi-calendar-range" style={{ color: '#2e5090' }} />
                DATE RANGE
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '0.67rem', color: '#8090a8', marginBottom: 4, fontWeight: 500 }}>From</div>
                  <input type="date" className="form-control form-control-sm"
                    style={{ fontSize: '0.76rem', borderRadius: '0.4rem', borderColor: 'rgba(46,80,144,0.3)' }}
                    value={dateFrom}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => onDateFromChange(e.target.value)} />
                </div>
                <div style={{ color: '#9baec8', fontSize: '1rem', paddingBottom: 4 }}>–</div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '0.67rem', color: '#8090a8', marginBottom: 4, fontWeight: 500 }}>To</div>
                  <input type="date" className="form-control form-control-sm"
                    style={{ fontSize: '0.76rem', borderRadius: '0.4rem', borderColor: 'rgba(46,80,144,0.3)' }}
                    value={dateTo}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => onDateToChange(e.target.value)} />
                </div>
              </div>
              {(dateFrom || dateTo) && (
                <button type="button"
                  style={{
                    marginTop: 10, width: '100%',
                    background: 'linear-gradient(90deg,#1e3a5f 0%,#2e5090 100%)',
                    color: '#fff', border: 'none', borderRadius: '0.4rem',
                    padding: '5px 0', fontSize: '0.75rem', fontWeight: 600,
                    cursor: 'pointer', display: 'flex', alignItems: 'center',
                    justifyContent: 'center', gap: 5,
                  }}
                  onClick={() => setOpen(false)}>
                  <i className="bi bi-check-lg" />Apply Range
                </button>
              )}
            </div>
          )}

          {/* Clear footer */}
          {isActive && (
            <div style={{ padding: '6px 12px 10px', borderTop: '1px solid #eef0f5' }}>
              <button type="button"
                style={{
                  width: '100%',
                  background: 'rgba(229,62,62,0.06)',
                  border: '1px solid rgba(229,62,62,0.18)',
                  borderRadius: '0.4rem',
                  color: '#e53e3e', fontSize: '0.75rem', fontWeight: 600,
                  padding: '5px 0', cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
                }}
                onClick={() => { onChange('all'); onDateFromChange(''); onDateToChange(''); setOpen(false); }}>
                <i className="bi bi-x-circle" />Clear Filter
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

