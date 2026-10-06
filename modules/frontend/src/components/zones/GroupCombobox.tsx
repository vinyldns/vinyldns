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

import React, { useEffect, useRef, useState } from "react";
import type { Group } from "../../types/group";

interface GroupComboboxProps {
  groups: Group[];
  value: string;
  onChange: (id: string) => void;
  invalid?: boolean;
  errorMessage?: string;
}

export function GroupCombobox({
  groups,
  value,
  onChange,
  invalid,
  errorMessage,
}: GroupComboboxProps) {
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const isDark =
    document.documentElement.getAttribute("data-vds-theme") === "dark";

  const selected = groups.find((g) => g.id === value);
  const filtered = search
    ? groups.filter(
        (g) =>
          g.name.toLowerCase().includes(search.toLowerCase()) ||
          (g.description ?? "").toLowerCase().includes(search.toLowerCase()),
      )
    : groups;

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
        setSearch("");
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} style={{ position: "relative" }}>
      {/* Trigger — looks like a standard dropdown */}
      <div
        className={`form-select vds-zone-form__input ${invalid ? "is-invalid" : ""}`}
        style={{ cursor: "pointer", userSelect: "none" }}
        onClick={() => {
          setOpen((v) => !v);
          setSearch("");
        }}
      >
        {selected ? (
          <>
            {selected.name}
            {selected.description ? (
              <span className="text-muted ms-1" style={{ fontSize: "0.85em" }}>
                ({selected.description})
              </span>
            ) : null}
          </>
        ) : (
          <span>— Select a group —</span>
        )}
      </div>
      {invalid && errorMessage && (
        <div className="invalid-feedback d-block">{errorMessage}</div>
      )}

      {/* Dropdown panel */}
      {open && (
        <div className="vds-group-dropdown-panel">
          <div className="vds-group-dropdown-search-wrap">
            <input
              autoFocus
              type="text"
              className="form-control form-control-sm"
              placeholder="Type to filter groups…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onClick={(e) => e.stopPropagation()}
            />
          </div>

          <div className="vds-group-dropdown-list">
            <div
              className="vds-group-dropdown-placeholder"
              onMouseDown={() => {
                onChange("");
                setOpen(false);
                setSearch("");
              }}
            >
              — Select a group —
            </div>

            {filtered.length === 0 && (
              <div className="vds-group-dropdown-empty">No groups match</div>
            )}

            {filtered.map((g) => (
              <div
                key={g.id}
                className={`vds-group-dropdown-item ${g.id === value ? "selected" : ""}`}
                onMouseDown={() => {
                  onChange(g.id);
                  setOpen(false);
                  setSearch("");
                }}
              >
                {g.name}
                {g.description && (
                  <span
                    className="text-muted ms-1"
                    style={{ fontSize: "0.85em" }}
                  >
                    ({g.description})
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
