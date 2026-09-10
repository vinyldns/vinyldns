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

import React, { useRef, useState, useEffect } from "react";
import {
  useForm,
  useFieldArray,
  useWatch,
  useFormContext,
  FormProvider,
} from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import type {
  CreateDnsChangeRequest,
  SingleChange,
} from "../../types/dnsChange";
import { groupsService } from "../../services/groupsService";

/** Union of all possible DNS record data shapes across supported record types. */
interface RecordData {
  // A / AAAA / A+PTR / AAAA+PTR
  address?: string;
  // CNAME
  cname?: string;
  // PTR
  ptrdname?: string;
  // TXT
  text?: string;
  // MX
  preference?: number;
  exchange?: string;
  // NS
  nsdname?: string;
  // SRV
  priority?: number;
  weight?: number;
  port?: number;
  target?: string;
  // NAPTR
  order?: number;
  flags?: string;
  service?: string;
  regexp?: string;
  replacement?: string;
}

/**
 * Form-level change item. Strips server-only fields from `SingleChange` so
 * the form only manages the subset of fields the user can actually provide.
 * The `record` field holds the type-specific record data payload.
 */
/**
 * Form-level change item. Strips server-only fields from `SingleChange` so
 * the form only manages the subset of fields the user can actually provide.
 * The `record` field holds the type-specific record data payload.
 */
type ChangeFormItem = Omit<
  SingleChange,
  | "id"
  | "status"
  | "recordName"
  | "zoneName"
  | "zoneId"
  | "recordSetId"
  | "errors"
  | "systemMessage"
> & { record?: RecordData };
export type { ChangeFormItem, RecordData };

/** Top-level react-hook-form shape for the batch change submission form. */
/** Top-level react-hook-form shape for the batch change submission form. */
interface DnsChangeFormData {
  comments: string;
  ownerGroupId: string;
  scheduledOption: "now" | "later";
  scheduledTime: string;
  changes: ChangeFormItem[];
}

/** Default batch change limit. Matches VinylDNS server default. */
const BATCH_CHANGE_LIMIT = 1000;

/**
 * IPv4 address validation pattern. Matches only dotted-decimal notation with
 * each octet in the 0–255 range.
 */
const RE_IPV4 =
  /^(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)$/;

/**
 * IPv6 address validation pattern covering all standard address forms including
 * compressed (::), mixed IPv4/IPv6, and link-local addresses with zone IDs.
 */
const RE_IPV6 =
  /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$/;

/**
 * FQDN validation pattern. Allows an optional leading wildcard label (`*.`)
 * and requires each label to be 1–63 alphanumeric/hyphen characters. An
 * optional trailing dot is permitted.
 */
const RE_FQDN =
  /^(\*\.)?([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+([a-zA-Z]{2,}\.?)$/;

/** Decode a single CSV row using standard CSV quoting rules. */
function decodeCsvRow(row: string): string[] {
  const regex = /(,|\r?\n|\r|^)(?:"([^"]*(?:""[^"]*)*)"|([^,\r\n]*))/gi;
  const matches = [...row.matchAll(regex)];
  return matches.map((m) =>
    m[2] !== undefined ? m[2].replace(/""/g, '"') : (m[3] ?? ""),
  );
}

/**
 * Parses a full CSV text into `ChangeFormItem[]`.
 *
 * Validates the expected header row before processing data. NAPTR rows are
 * handled with a 5- or 6-field fallback because `regexp` is optional in some
 * export tools. Returns an `error` string instead of throwing so callers can
 * display it inline rather than catching an exception.
 */
export function parseCsvToChanges(
  csvText: string,
  limit: number,
): { changes: ChangeFormItem[]; error?: string } {
  const rows = csvText.split("\n");
  const header = rows[0]?.trim();
  if (header !== "Change Type,Record Type,Input Name,TTL,Record Data") {
    return {
      changes: [],
      error:
        "Import failed. CSV header must be: Change Type,Record Type,Input Name,TTL,Record Data",
    };
  }
  const dataRows = rows.slice(1).filter((r) => r.replace(/,+/g, "").trim());
  if (dataRows.length > limit) {
    return {
      changes: [],
      error: `Import failed. Cannot add more than ${limit} records per DNS change.`,
    };
  }
  const changes: ChangeFormItem[] = [];
  for (const row of dataRows) {
    const cols = decodeCsvRow(row);
    const changeTypeRaw = cols[0]?.trim() ?? "";
    const type = (cols[1]?.trim().toUpperCase() ??
      "A+PTR") as ChangeFormItem["type"];
    const inputName = cols[2]?.trim() ?? "";
    const ttlStr = cols[3]?.trim();
    const ttl = ttlStr ? parseInt(ttlStr, 10) : undefined;
    const recordData = cols[4]?.trim() ?? "";
    const changeType: "Add" | "DeleteRecordSet" = /delete/i.test(changeTypeRaw)
      ? "DeleteRecordSet"
      : "Add";

    let record: RecordData = {};
    if (["A", "AAAA", "A+PTR", "AAAA+PTR"].includes(type)) {
      record = { address: recordData };
    } else if (type === "CNAME") {
      record = { cname: recordData };
    } else if (type === "PTR") {
      record = { ptrdname: recordData };
    } else if (type === "TXT") {
      record = { text: recordData };
    } else if (type === "NS") {
      record = { nsdname: recordData };
    } else if (type === "MX") {
      const [pref, exchange] = recordData.split(" ");
      record = { preference: parseInt(pref, 10), exchange };
    } else if (type === "NAPTR") {
      const parts = recordData.split(" ");
      if (parts.length >= 6) {
        record = {
          order: parseInt(parts[0], 10),
          preference: parseInt(parts[1], 10),
          flags: parts[2],
          service: parts[3],
          regexp: parts[4],
          replacement: parts[5],
        };
      } else {
        record = {
          order: parseInt(parts[0], 10),
          preference: parseInt(parts[1], 10),
          flags: parts[2],
          service: parts[3],
          regexp: "",
          replacement: parts[4] ?? "",
        };
      }
    } else if (type === "SRV") {
      const [pri, wt, port, target] = recordData.split(" ");
      record = {
        priority: parseInt(pri, 10),
        weight: parseInt(wt, 10),
        port: parseInt(port, 10),
        target,
      };
    }
    changes.push({
      changeType,
      type,
      inputName,
      ttl,
      record: record as Record<string, unknown> & RecordData,
    });
  }
  return { changes };
}

const NAPTR_FLAGS = ["U", "S", "A", "P"] as const;

/**
 * Build a stable signature for a change row used to detect duplicates after
 * CSV import. Two rows are considered duplicates only when ALL of the
 * user-supplied fields match: changeType, type, inputName, ttl, and every
 * non-empty record sub-field. Record keys are sorted so property ordering
 * never affects the signature.
 */
export function changeSignature(c: ChangeFormItem): string {
  const recObj = (c.record ?? {}) as Record<string, unknown>;
  const recEntries = Object.entries(recObj)
    .filter(
      ([, v]) =>
        v !== undefined &&
        v !== null &&
        !(typeof v === "string" && v.trim() === "") &&
        !(typeof v === "number" && Number.isNaN(v)),
    )
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([k, v]) => `${k}=${String(v).trim().toLowerCase()}`)
    .join("|");
  return [
    String(c.changeType ?? "").toLowerCase(),
    String(c.type ?? "").toLowerCase(),
    String(c.inputName ?? "")
      .trim()
      .toLowerCase(),
    // TTL is intentionally excluded: rows with the same record data but
    // different TTL are still treated as duplicates so the user can pick
    // which TTL value to keep.
    recEntries,
  ].join("::");
}

/**
 * Group change rows by `changeSignature` and return only the groups that have
 * more than one row, i.e. duplicates. Indices refer to positions in the input
 * array so callers can mutate the original list precisely.
 */
export function findDuplicateGroups(
  changes: ChangeFormItem[],
): { signature: string; indices: number[] }[] {
  const map = new Map<string, number[]>();
  changes.forEach((c, idx) => {
    const sig = changeSignature(c);
    const list = map.get(sig);
    if (list) list.push(idx);
    else map.set(sig, [idx]);
  });
  const groups: { signature: string; indices: number[] }[] = [];
  for (const [signature, indices] of map.entries()) {
    if (indices.length > 1) groups.push({ signature, indices });
  }
  return groups;
}

/**
 * Type-aware record data fields for a single change row inside the batch form.
 * Compact inline variant used inside the table — no labels, minimal padding.
 */
function RecordDataFields({
  index,
  recordType,
  isAdd,
  isDark,
}: {
  index: number;
  recordType: string;
  isAdd: boolean;
  isDark: boolean;
}) {
  const {
    register,
    formState: { errors },
  } = useFormContext<DnsChangeFormData>();
  const req = isAdd;

  const recordError = (
    field: keyof NonNullable<DnsChangeFormData["changes"][number]["record"]>,
  ) =>
    errors?.changes?.[index]?.record?.[field]?.message ||
    (errors?.changes?.[index]?.record as Record<string, unknown> | undefined)?.[
      field
    ];

  const inputStyle: React.CSSProperties = {
    background: isDark ? "#1a2640" : "#fff",
    color: isDark ? "#cdd9ed" : "#212529",
    borderColor: isDark ? "rgba(127,168,216,0.2)" : "#dde3ec",
    boxShadow: "none",
    borderRadius: "0.35rem",
    minWidth: 0,
  };

  switch (recordType) {
    case "A":
    case "A+PTR":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="e.g. 1.1.1.1"
          autoComplete="off"
          style={inputStyle}
          {...register(`changes.${index}.record.address`, {
            required: req ? "Record data is required" : false,
            validate: (v) =>
              !req || !v || RE_IPV4.test(String(v)) || "Invalid IPv4",
          })}
        />
      );
    case "AAAA":
    case "AAAA+PTR":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="fd69:27cc::60"
          autoComplete="off"
          style={inputStyle}
          {...register(`changes.${index}.record.address`, {
            required: req ? "Record data is required" : false,
            validate: (v) =>
              !req || !v || RE_IPV6.test(String(v)) || "Invalid IPv6",
          })}
        />
      );
    case "CNAME":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="target.example.com."
          autoComplete="off"
          disabled={!isAdd}
          style={{
            ...inputStyle,
            background: !isAdd
              ? isDark
                ? "#0f1825"
                : "#e9ecef"
              : inputStyle.background,
          }}
          {...register(`changes.${index}.record.cname`, {
            required: req ? "Record data is required" : false,
            validate: (v) =>
              !req || !v || RE_FQDN.test(String(v)) || "Invalid FQDN",
          })}
        />
      );
    case "PTR":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="test.example.com."
          autoComplete="off"
          style={inputStyle}
          {...register(`changes.${index}.record.ptrdname`, {
            required: req ? "Record data is required" : false,
            validate: (v) =>
              !req || !v || RE_FQDN.test(String(v)) || "Invalid FQDN",
          })}
        />
      );
    case "TXT":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="attr=val"
          autoComplete="off"
          style={inputStyle}
          {...register(`changes.${index}.record.text`, {
            required: req ? "Record data is required" : false,
          })}
        />
      );
    case "MX":
      return (
        <div className="d-flex gap-1">
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Pref"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 70 }}
            {...register(`changes.${index}.record.preference`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
              min: 0,
              max: 65535,
            })}
          />
          <input
            className="form-control form-control-sm"
            placeholder="mail.example.com."
            style={inputStyle}
            {...register(`changes.${index}.record.exchange`, {
              required: req ? "Record data is required" : false,
              validate: (v) =>
                !req || !v || RE_FQDN.test(String(v)) || "Invalid FQDN",
            })}
          />
        </div>
      );
    case "NS":
      return (
        <input
          className="form-control form-control-sm"
          placeholder="ns1.example.com."
          autoComplete="off"
          style={inputStyle}
          {...register(`changes.${index}.record.nsdname`, {
            required: req ? "Record data is required" : false,
            validate: (v) =>
              !req || !v || RE_FQDN.test(String(v)) || "Invalid FQDN",
          })}
        />
      );
    case "SRV":
      return (
        <div className="d-flex gap-1">
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Pri"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 60 }}
            {...register(`changes.${index}.record.priority`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Wt"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 60 }}
            {...register(`changes.${index}.record.weight`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Port"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 70 }}
            {...register(`changes.${index}.record.port`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            className="form-control form-control-sm"
            placeholder="target.example.com."
            autoComplete="off"
            style={inputStyle}
            {...register(`changes.${index}.record.target`, {
              required: req ? "Record data is required" : false,
              validate: (v) =>
                !req ||
                !v ||
                RE_FQDN.test(String(v)) ||
                v === "." ||
                "Invalid FQDN",
            })}
          />
        </div>
      );
    case "NAPTR":
      return (
        <div className="d-flex gap-1 flex-wrap">
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Ord"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 60 }}
            {...register(`changes.${index}.record.order`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="Pref"
            min={0}
            max={65535}
            style={{ ...inputStyle, width: 60 }}
            {...register(`changes.${index}.record.preference`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <select
            className="form-select form-select-sm"
            style={{ ...inputStyle, width: 70 }}
            {...register(`changes.${index}.record.flags`, {
              required: req ? "Record data is required" : false,
            })}
          >
            <option value="">--</option>
            {NAPTR_FLAGS.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>
          <input
            className="form-control form-control-sm"
            placeholder="SIP+D2U"
            autoComplete="off"
            style={{ ...inputStyle, width: 90 }}
            {...register(`changes.${index}.record.service`, {
              required: req ? "Record data is required" : false,
            })}
          />
          <input
            className="form-control form-control-sm"
            placeholder="Regexp"
            autoComplete="off"
            style={{ ...inputStyle, width: 80 }}
            {...register(`changes.${index}.record.regexp`)}
          />
          <input
            className="form-control form-control-sm"
            placeholder="Replacement"
            autoComplete="off"
            style={inputStyle}
            {...register(`changes.${index}.record.replacement`, {
              required: req ? "Record data is required" : false,
            })}
          />
        </div>
      );
    default:
      return <span className="text-muted small fst-italic">—</span>;
  }
}

const RECORD_TYPES = [
  "A+PTR",
  "AAAA+PTR",
  "A",
  "AAAA",
  "CNAME",
  "PTR",
  "TXT",
  "MX",
  "NS",
  "SRV",
  "NAPTR",
] as const;

/**
 * A single DNS change row rendered as a compact table row.
 * All fields sit inline on one line so many rows are visible simultaneously.
 */
function ChangeRow({
  index,
  remove,
  serverErrors,
  disabled,
}: {
  index: number;
  remove: (i: number) => void;
  serverErrors?: string[];
  disabled?: boolean;
}) {
  const {
    register,
    control,
    setValue,
    formState: { errors },
  } = useFormContext<DnsChangeFormData>();
  const changeType = useWatch({ control, name: `changes.${index}.changeType` });
  const recordType = useWatch({ control, name: `changes.${index}.type` });
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

  const isAdd = changeType === "Add";
  const isPtr = recordType === "PTR";
  const hasErrors = serverErrors && serverErrors.length > 0;

  const { onChange: onTypeChange, ...restTypeRegister } = register(
    `changes.${index}.type`,
  );

  const cellStyle: React.CSSProperties = {
    padding: "0.3rem 0.4rem",
    verticalAlign: "top",
    background: hasErrors ? (isDark ? "#1e0a0a" : "#fff8f8") : "transparent",
  };

  const inputStyle: React.CSSProperties = {
    background: isDark ? "#1a2640" : "#fff",
    color: isDark ? "#cdd9ed" : "#212529",
    borderColor: isDark ? "rgba(127,168,216,0.2)" : "#dde3ec",
    boxShadow: "none",
    borderRadius: "0.35rem",
    fontSize: "0.82rem",
  };

  return (
    <tr data-change-row="true">
      {/* # */}
      <td
        style={{
          ...cellStyle,
          width: 36,
          textAlign: "center",
          color: isDark ? "#64748b" : "#94a3b8",
          fontSize: "0.75rem",
          fontWeight: 600,
        }}
      >
        {hasErrors ? (
          <i
            className="bi bi-exclamation-circle-fill"
            style={{ color: "#dc2626" }}
            title={serverErrors!.join("\n")}
          />
        ) : (
          index + 1
        )}
      </td>

      {/* Change Type */}
      <td style={{ ...cellStyle, width: 130 }}>
        <select
          className="form-select form-select-sm"
          style={inputStyle}
          {...register(`changes.${index}.changeType`)}
        >
          <option value="Add">Add</option>
          <option value="DeleteRecordSet">Delete</option>
        </select>
      </td>

      {/* Record Type */}
      <td style={{ ...cellStyle, width: 110 }}>
        <select
          className="form-select form-select-sm"
          style={inputStyle}
          {...restTypeRegister}
          onChange={(e) => {
            setValue(`changes.${index}.record`, {});
            void onTypeChange(e);
          }}
        >
          {RECORD_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </td>

      {/* Input Name */}
      <td style={{ ...cellStyle, minWidth: 200 }}>
        <div
          style={{ display: "flex", flexDirection: "column", height: "100%" }}
        >
          <input
            className="form-control form-control-sm"
            placeholder={isPtr ? "192.0.2.193" : "host.example.com."}
            autoComplete="off"
            aria-invalid={
              errors?.changes?.[index]?.inputName ? "true" : undefined
            }
            style={{
              ...inputStyle,
              borderColor: inputStyle.borderColor,
            }}
            {...register(`changes.${index}.inputName`, {
              required: "Input Name is required",
              validate: (v) => {
                if (!v) return true;
                if (isPtr)
                  return RE_IPV4.test(v) || RE_IPV6.test(v) || "Invalid IP";
                return RE_FQDN.test(v) || "Invalid FQDN";
              },
            })}
          />
          {errors?.changes?.[index]?.inputName && (
            <div
              style={{
                fontSize: "0.72rem",
                color: "#dc3545",
                marginTop: "3px",
                display: "flex",
                alignItems: "center",
                gap: 4,
              }}
            >
              <i className="bi bi-exclamation-circle-fill" />
              {errors.changes[index]?.inputName?.message ||
                "Input Name is required"}
            </div>
          )}
        </div>
      </td>

      {/* TTL */}
      <td style={{ ...cellStyle, width: 80 }}>
        <input
          type="number"
          className="form-control form-control-sm"
          placeholder=""
          autoComplete="off"
          disabled={!isAdd}
          min={30}
          max={2147483647}
          style={{
            ...inputStyle,
            background: !isAdd
              ? isDark
                ? "#0f1825"
                : "#f1f5f9"
              : inputStyle.background,
          }}
          {...register(`changes.${index}.ttl`, { valueAsNumber: true })}
        />
      </td>

      {/* Record Data */}
      <td style={{ ...cellStyle }}>
        <div
          style={{ display: "flex", flexDirection: "column", height: "100%" }}
        >
          <RecordDataFields
            index={index}
            recordType={recordType}
            isAdd={isAdd}
            isDark={isDark}
          />
          {(() => {
            const recordErrors = errors?.changes?.[index]?.record as
              Record<string, { message?: string } | undefined> | undefined;
            const recordErrorMessage = Object.values(recordErrors ?? {}).find(
              (value) =>
                value && typeof value === "object" && "message" in value,
            )?.message;
            return recordErrorMessage ? (
              <div
                style={{
                  fontSize: "0.72rem",
                  color: "#dc3545",
                  marginTop: "3px",
                  display: "flex",
                  alignItems: "center",
                  gap: 4,
                }}
              >
                <i className="bi bi-exclamation-circle-fill" />
                {recordErrorMessage}
              </div>
            ) : null;
          })()}
        </div>
      </td>

      {serverErrors && serverErrors.length > 0 && (
        <td
          style={{ ...cellStyle, background: isDark ? "#1f0d0d" : "#fff5f5" }}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
            {serverErrors.map((message) => (
              <div
                key={message}
                style={{
                  fontSize: "0.72rem",
                  color: "#dc3545",
                  display: "flex",
                  alignItems: "center",
                  gap: 4,
                }}
              >
                <i className="bi bi-exclamation-circle-fill" />
                {message}
              </div>
            ))}
          </div>
        </td>
      )}

      {/* Remove */}
      <td style={{ ...cellStyle, width: 90, textAlign: "center" }}>
        <button
          type="button"
          onClick={() => remove(index)}
          disabled={disabled}
          title={disabled ? "Editing is locked during review" : "Remove row"}
          aria-label="Delete row"
          className="btn btn-sm d-inline-flex align-items-center gap-1"
          style={{
            border: `1px solid ${disabled ? (isDark ? "#475569" : "#cbd5e1") : isDark ? "#7f1d1d" : "#f1aeb5"}`,
            background: disabled
              ? isDark
                ? "#1e293b"
                : "#f8fafc"
              : isDark
                ? "rgba(127,29,29,0.25)"
                : "#fff5f5",
            color: disabled ? (isDark ? "#94a3b8" : "#64748b") : "#dc3545",
            borderRadius: "0.35rem",
            padding: "0.2rem 0.55rem",
            fontSize: "0.75rem",
            fontWeight: 600,
            cursor: disabled ? "not-allowed" : "pointer",
            opacity: disabled ? 0.8 : 1,
            boxShadow: "none",
          }}
        >
          <i className="bi bi-trash3" />
          Delete
        </button>
      </td>
    </tr>
  );
}

/**
 * Isolated "Request Date/Time" field extracted into its own component to keep
 * the main form render function readable. Shows a simple Now/Later radio
 * toggle; the datetime-local input only appears when "Later" is selected.
 * The user's local timezone is displayed next to the picker so there's no
 * ambiguity about which timezone the server will interpret the value in.
 */
function ScheduledTimeField({
  register,
  watch,
  isDark,
}: {
  register: ReturnType<typeof useForm<DnsChangeFormData>>["register"];
  watch: ReturnType<typeof useForm<DnsChangeFormData>>["watch"];
  isDark: boolean;
}) {
  const scheduledOption = watch("scheduledOption");
  const localTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <div className="col-12 col-md-4">
      <label
        className="form-label"
        style={{
          fontSize: "0.8rem",
          fontWeight: 600,
          color: isDark ? "#cbd5e1" : "#1f2a44",
        }}
      >
        Request Date/Time
      </label>
      <div className="d-flex gap-3 mb-1">
        <div className="form-check">
          <input
            type="radio"
            className="form-check-input"
            id="scheduleNow"
            value="now"
            {...register("scheduledOption")}
          />
          <label className="form-check-label small" htmlFor="scheduleNow">
            Now
          </label>
        </div>
        <div className="form-check">
          <input
            type="radio"
            className="form-check-input"
            id="scheduleLater"
            value="later"
            {...register("scheduledOption")}
          />
          <label className="form-check-label small" htmlFor="scheduleLater">
            Later
          </label>
        </div>
      </div>
      {scheduledOption === "later" && (
        <div className="d-flex align-items-center gap-1">
          <input
            type="datetime-local"
            className="form-control form-control-sm"
            style={{
              background: isDark ? "#1a2640" : "#fff",
              color: isDark ? "#cdd9ed" : "#212529",
              borderColor: isDark ? "rgba(127,168,216,0.2)" : "#dde3ec",
              boxShadow: "none",
              borderRadius: "0.45rem",
            }}
            {...register("scheduledTime")}
          />
          <span className="text-muted small text-nowrap">{localTz}</span>
        </div>
      )}
    </div>
  );
}

/**
 * Check if a change item has meaningful data that would be lost on discard.
 * Returns true if either inputName has a value OR the corresponding record field
 * for the row's type has a value.
 */
export function hasMeaningfulDiscardData(changes: ChangeFormItem[]): boolean {
  // If 2+ rows: immediately true (assume meaningful data)
  if (changes.length >= 2) return true;

  // Single row: check if inputName OR record fields have values
  if (changes.length === 1) {
    const c = changes[0];
    if (c.inputName && c.inputName.trim()) return true;

    const record = c.record ?? {};
    const hasRecordData =
      (record.address && String(record.address).trim()) ||
      (record.cname && String(record.cname).trim()) ||
      (record.ptrdname && String(record.ptrdname).trim()) ||
      (record.text && String(record.text).trim()) ||
      (record.preference !== undefined && record.preference !== null) ||
      (record.exchange && String(record.exchange).trim()) ||
      (record.nsdname && String(record.nsdname).trim()) ||
      (record.priority !== undefined && record.priority !== null) ||
      (record.weight !== undefined && record.weight !== null) ||
      (record.port !== undefined && record.port !== null) ||
      (record.target && String(record.target).trim()) ||
      (record.order !== undefined && record.order !== null) ||
      (record.flags && String(record.flags).trim()) ||
      (record.service && String(record.service).trim()) ||
      (record.regexp && String(record.regexp).trim()) ||
      (record.replacement && String(record.replacement).trim());

    return !!hasRecordData;
  }

  return false;
}

/**
 * @param onSubmit        - Receives the fully normalized `CreateDnsChangeRequest`
 *                          and the `allowManualReview` flag on form submit.
 * @param onCancel        - Called when the user dismisses without submitting.
 * @param isSubmitting    - Disables the submit button while the parent mutation runs.
 * @param serverRowErrors - Per-row error arrays returned by a 400 API response;
 *                          passed straight down to each `ChangeRow`.
 */
interface DnsChangeFormProps {
  onSubmit: (data: CreateDnsChangeRequest, allowManualReview: boolean) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  /** Per-row server errors returned by a 400 API response */
  serverRowErrors?: string[][];
  /** Callback to notify parent when unsaved data is detected */
  onUnsavedChange?: (hasUnsaved: boolean) => void;
}

/**
 * Format a record-data object into a single human-readable line for display
 * in the duplicate-review modal. Empty values are dropped so PTR-only rows
 * don't render a sea of empty `field=` pairs.
 */
export function formatRecordData(record: RecordData | undefined): string {
  if (!record) return "—";
  const parts: string[] = [];
  for (const [k, v] of Object.entries(record).sort(([a], [b]) =>
    a.localeCompare(b),
  )) {
    if (v === undefined || v === null) continue;
    if (typeof v === "string" && v.trim() === "") continue;
    parts.push(`${k}: ${v}`);
  }
  return parts.length ? parts.join(", ") : "—";
}

interface DuplicateReviewModalProps {
  state: {
    changes: ChangeFormItem[];
    groups: { signature: string; indices: number[] }[];
    keep: Set<number>;
  };
  isDark: boolean;
  onToggleKeep: (rowIdx: number) => void;
  onApply: () => void;
  onCancel: () => void;
}

/**
 * Modal shown when the CSV importer detects duplicate change rows. Each
 * "duplicate group" represents one set of identical rows (matching change
 * type, record type, input name, TTL, and record data). The user can pick
 * which row in each group to keep; everything outside any group is kept
 * automatically and is not shown.
 */
function DuplicateReviewModal({
  state,
  isDark,
  onToggleKeep,
  onApply,
  onCancel,
}: DuplicateReviewModalProps) {
  const { changes, groups, keep } = state;
  // `totalRows` — total number of rows in the imported CSV.
  // `totalDuplicateRows` — total rows participating in any duplicate group
  //   (i.e. the maximum that *could* be removed if the user kept only one
  //   row per group).
  // `willKeep` / `willRemove` — reflect the user's current checkbox state.
  const totalRows = changes.length;
  const totalDuplicateRows = groups.reduce(
    (sum, g) => sum + g.indices.length,
    0,
  );
  const willRemove = totalRows - keep.size;
  const willKeep = keep.size;

  // Lock body scroll while modal is open, restore on close.
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  const panelBg = isDark ? "#1e293b" : "#ffffff";
  const panelBorder = isDark ? "#2d4163" : "#e8ecf0";
  const headerText = isDark ? "#e2e8f0" : "#0d1b3e";
  const subText = isDark ? "#94a3b8" : "#64748b";
  const cardBg = isDark ? "#162032" : "#f8fafd";
  const cardBorder = isDark ? "#2d4163" : "#e2e8f0";
  const rowBg = isDark ? "#1e293b" : "#ffffff";
  const rowBorder = isDark ? "#334155" : "#e8ecf0";
  const removeBg = isDark ? "#3f1d1d" : "#fef2f2";
  const removeBorder = isDark ? "#7f1d1d" : "#fecaca";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="dup-review-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(15, 23, 42, 0.65)",
        backdropFilter: "blur(2px)",
        zIndex: 1080,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "1.5rem",
      }}
    >
      <div
        style={{
          background: panelBg,
          color: headerText,
          border: `1px solid ${panelBorder}`,
          borderRadius: "0.85rem",
          boxShadow: "0 25px 60px rgba(0, 0, 0, 0.45)",
          width: "min(900px, 100%)",
          maxHeight: "90vh",
          display: "flex",
          flexDirection: "column",
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
            borderBottom: `1px solid ${panelBorder}`,
            background: isDark
              ? "linear-gradient(90deg, #1e293b, #162032)"
              : "linear-gradient(90deg, #ffffff, #f8fafd)",
          }}
        >
          <span
            style={{
              width: 38,
              height: 38,
              borderRadius: "50%",
              background: isDark ? "#3b2f0d" : "#fff7e0",
              color: "#d97706",
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "1.1rem",
              flexShrink: 0,
            }}
          >
            <i className="bi bi-exclamation-triangle-fill" aria-hidden="true" />
          </span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <h5
              id="dup-review-title"
              style={{
                margin: 0,
                fontSize: "1.05rem",
                fontWeight: 600,
                color: headerText,
              }}
            >
              Duplicate records found
            </h5>
          </div>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Close"
            style={{
              background: "transparent",
              border: "none",
              color: subText,
              fontSize: "1.1rem",
              cursor: "pointer",
              padding: "0.25rem 0.5rem",
              borderRadius: "0.4rem",
              transition: "background 0.15s",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = isDark ? "#2d4163" : "#e8ecf0";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "transparent";
            }}
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        {/* Body */}
        <div
          style={{
            padding: "1.1rem 1.4rem",
            overflowY: "auto",
            flex: 1,
          }}
        >
          <p
            style={{
              margin: "0 0 1rem",
              fontSize: "0.88rem",
              color: subText,
              lineHeight: 1.5,
            }}
          >
            Rows are considered duplicates when{" "}
            <strong style={{ color: headerText }}>
              Change Type, Record Type, Input Name,
            </strong>{" "}
            and <strong style={{ color: headerText }}>Record Data</strong> all
            match. Keep the rows you want to import; unchecked rows will be
            dropped before the form is populated.
          </p>

          {groups.map((group, gIdx) => {
            const sample = changes[group.indices[0]];
            return (
              <div
                key={gIdx}
                style={{
                  background: cardBg,
                  border: `1px solid ${cardBorder}`,
                  borderRadius: "0.7rem",
                  padding: "0.85rem 1rem",
                  marginBottom: "0.9rem",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    marginBottom: "0.65rem",
                  }}
                >
                  <span
                    style={{
                      fontSize: "0.7rem",
                      fontWeight: 700,
                      letterSpacing: "0.04em",
                      textTransform: "uppercase",
                      color: "#d97706",
                      background: isDark ? "#3b2f0d" : "#fff7e0",
                      padding: "0.2rem 0.55rem",
                      borderRadius: "0.35rem",
                    }}
                  >
                    Group {gIdx + 1}
                  </span>
                  <span style={{ fontSize: "0.82rem", color: subText }}>
                    {group.indices.length} identical rows
                  </span>
                </div>

                {/* Shared key fields */}
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
                    gap: "0.5rem 1rem",
                    fontSize: "0.82rem",
                    marginBottom: "0.85rem",
                    paddingBottom: "0.75rem",
                    borderBottom: `1px dashed ${cardBorder}`,
                  }}
                >
                  <div>
                    <div style={{ color: subText, fontSize: "0.72rem" }}>
                      Change Type
                    </div>
                    <div style={{ color: headerText, fontWeight: 500 }}>
                      {sample.changeType ?? "—"}
                    </div>
                  </div>
                  <div>
                    <div style={{ color: subText, fontSize: "0.72rem" }}>
                      Record Type
                    </div>
                    <div style={{ color: headerText, fontWeight: 500 }}>
                      {sample.type ?? "—"}
                    </div>
                  </div>
                  <div style={{ gridColumn: "span 2", minWidth: 0 }}>
                    <div style={{ color: subText, fontSize: "0.72rem" }}>
                      Input Name
                    </div>
                    <div
                      style={{
                        color: headerText,
                        fontWeight: 500,
                        wordBreak: "break-all",
                      }}
                    >
                      {sample.inputName || "—"}
                    </div>
                  </div>
                  <div style={{ gridColumn: "1 / -1", minWidth: 0 }}>
                    <div style={{ color: subText, fontSize: "0.72rem" }}>
                      Record Data
                    </div>
                    <div
                      style={{
                        color: headerText,
                        fontWeight: 500,
                        wordBreak: "break-word",
                        fontFamily:
                          "ui-monospace, SFMono-Regular, Menlo, monospace",
                        fontSize: "0.78rem",
                      }}
                    >
                      {formatRecordData(sample.record)}
                    </div>
                  </div>
                </div>

                {/* Per-row checkboxes */}
                <div
                  style={{ display: "flex", flexDirection: "column", gap: 5 }}
                >
                  {group.indices.map((rowIdx) => {
                    const row = changes[rowIdx];
                    const checked = keep.has(rowIdx);
                    return (
                      <label
                        key={rowIdx}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "0.65rem",
                          padding: "0.5rem 0.85rem",
                          background: checked
                            ? isDark
                              ? "#0f2f1a"
                              : "#f0fdf4"
                            : isDark
                              ? "#1e293b"
                              : "#f8fafc",
                          border: `1px solid ${
                            checked
                              ? isDark
                                ? "#16a34a"
                                : "#86efac"
                              : isDark
                                ? "#2d3d52"
                                : "#e2e8f0"
                          }`,
                          borderRadius: "0.5rem",
                          cursor: "pointer",
                          transition: "background 0.15s, border-color 0.15s",
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => onToggleKeep(rowIdx)}
                          style={{
                            cursor: "pointer",
                            flexShrink: 0,
                            width: 15,
                            height: 15,
                            accentColor: "#16a34a",
                          }}
                        />
                        <span
                          style={{
                            fontWeight: 600,
                            fontSize: "0.83rem",
                            color: headerText,
                            flexShrink: 0,
                          }}
                        >
                          Row #{rowIdx + 1}
                        </span>
                        <span
                          style={{
                            background: isDark ? "#1e3a5f" : "#dbeafe",
                            color: isDark ? "#93c5fd" : "#1e5fa8",
                            fontSize: "0.72rem",
                            fontWeight: 600,
                            padding: "0.1rem 0.5rem",
                            borderRadius: "0.3rem",
                            fontFamily:
                              "ui-monospace, SFMono-Regular, Menlo, monospace",
                            flexShrink: 0,
                          }}
                        >
                          TTL {row.ttl !== undefined ? row.ttl : "—"}
                        </span>
                        <span style={{ flex: 1 }} />
                        {checked && (
                          <span
                            style={{
                              background: "#dc2626",
                              color: "#fff",
                              fontSize: "0.68rem",
                              fontWeight: 700,
                              padding: "0.15rem 0.55rem",
                              borderRadius: "9999px",
                              letterSpacing: "0.04em",
                              textTransform: "uppercase",
                              flexShrink: 0,
                            }}
                          >
                            Remove
                          </span>
                        )}
                      </label>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: "0.9rem 1.4rem",
            borderTop: `1px solid ${panelBorder}`,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "0.75rem",
            background: isDark ? "#162032" : "#f8fafd",
            flexWrap: "wrap",
          }}
        >
          <div
            style={{
              fontSize: "0.82rem",
              color: subText,
              display: "flex",
              alignItems: "center",
              gap: "0.4rem",
              flexWrap: "wrap",
            }}
          >
            <span>
              {willRemove > 0 ? (
                <>
                  <strong style={{ color: isDark ? "#fca5a5" : "#dc2626" }}>
                    {willRemove}
                  </strong>{" "}
                  duplicate{willRemove !== 1 ? "s" : ""} will be removed
                </>
              ) : null}
            </span>
          </div>
          <div style={{ display: "flex", gap: "0.55rem" }}>
            <button
              type="button"
              onClick={onCancel}
              style={{
                padding: "0.5rem 1rem",
                background: "transparent",
                border: `1px solid ${panelBorder}`,
                color: headerText,
                borderRadius: "0.5rem",
                cursor: "pointer",
                fontSize: "0.85rem",
                fontWeight: 500,
                transition: "background 0.15s",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = isDark
                  ? "#2d4163"
                  : "#e8ecf0";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "transparent";
              }}
            >
              Cancel import
            </button>
            <button
              type="button"
              onClick={onApply}
              disabled={willKeep === 0}
              style={{
                padding: "0.5rem 1.1rem",
                background:
                  willKeep === 0
                    ? isDark
                      ? "#334155"
                      : "#cbd5e1"
                    : "linear-gradient(90deg, #1e5fa8, #0d1b3e)",
                border: "none",
                color: "#fff",
                borderRadius: "0.5rem",
                cursor: willKeep === 0 ? "not-allowed" : "pointer",
                fontSize: "0.85rem",
                fontWeight: 600,
                boxShadow:
                  willKeep === 0 ? "none" : "0 2px 8px rgba(30, 95, 168, 0.25)",
                transition: "box-shadow 0.15s",
                display: "inline-flex",
                alignItems: "center",
                gap: "0.4rem",
              }}
              onMouseEnter={(e) => {
                if (willKeep > 0)
                  e.currentTarget.style.boxShadow =
                    "0 3px 12px rgba(30, 95, 168, 0.4)";
              }}
              onMouseLeave={(e) => {
                if (willKeep > 0)
                  e.currentTarget.style.boxShadow =
                    "0 2px 8px rgba(30, 95, 168, 0.25)";
              }}
            >
              <i className="bi bi-check2-circle" aria-hidden="true" />
              Apply &amp; import {willKeep} row{willKeep !== 1 ? "s" : ""}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Multi-row batch DNS change form.
 *
 * Manages state for:
 * - Batch metadata (description, owner group, scheduled time)
 * - An unbounded list of individual change rows via `useFieldArray`
 * - CSV import with client-side validation and inline error feedback
 * - Per-row server error display after a 400 API response
 *
 * `FormProvider` wraps the entire form so child row components can access
 * `register` and `control` via `useFormContext` without prop drilling.
 *
 * A+PTR and AAAA+PTR are convenience types that get expanded into two
 * separate API entries (address + reverse PTR) in `handleFormSubmit`,
 * mirroring the legacy portal's `formatData` behavior.
 */
export function DnsChangeForm({
  onSubmit,
  onCancel,
  isSubmitting,
  serverRowErrors,
  onUnsavedChange,
}: DnsChangeFormProps) {
  const [allowManualReview, setAllowManualReview] = useState(false);
  const [rowErrors, setRowErrors] = useState<string[][]>([]);
  const [csvAlert, setCsvAlert] = useState<{
    type: "success" | "danger";
    message: string;
  } | null>(null);
  const [pendingSubmitData, setPendingSubmitData] = useState<{
    data: CreateDnsChangeRequest;
    allowManualReview: boolean;
    rowCount: number;
  } | null>(null);
  const [dupReview, setDupReview] = useState<{
    changes: ChangeFormItem[];
    groups: { signature: string; indices: number[] }[];
    keep: Set<number>;
  } | null>(null);
  // Cancel confirmation modal
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [isOwnerGroupMenuOpen, setIsOwnerGroupMenuOpen] = useState(false);
  const csvFileRef = useRef<HTMLInputElement>(null);
  const prevFieldsLengthRef = useRef(0);
  const shouldWarnRef = useRef(false);
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

  // Fetch the user's groups to populate the owner group ID selector.
  // ignoreAccess=true
  const { data: groupsData, isLoading: isGroupsLoading } = useQuery({
    queryKey: ["groups-for-dns-form"],
    queryFn: async () => {
      const res = await groupsService.getGroups(true);
      return res.data.groups ?? [];
    },
    staleTime: 5 * 60 * 1000,
  });
  const groups = groupsData ?? [];

  // Server errors take priority over any local client-side error state;
  // once the parent clears `serverRowErrors` (e.g. on resubmit), local
  // errors from the previous attempt are also discarded.
  const effectiveRowErrors = serverRowErrors ?? rowErrors;

  // Surface a banner-level hint when any row's server error references the
  // owner group — this happens when records belong to a shared zone but
  // no owner group ID was provided in the batch metadata.
  const ownerGroupError = (serverRowErrors ?? [])
    .flat()
    .some((e) => e.includes("owner group ID must be specified for record"));

  const methods = useForm<DnsChangeFormData>({
    defaultValues: {
      comments: "",
      ownerGroupId: "",
      scheduledOption: "now",
      scheduledTime: "",
      changes: [
        {
          changeType: "Add",
          inputName: "",
          type: "A+PTR",
          ttl: 300,
          record: {},
        },
      ],
    },
  });

  const { register, control, handleSubmit, watch, formState } = methods;
  const { fields, append, remove, replace } = useFieldArray({
    control,
    name: "changes",
  });

  // Watch changes to detect unsaved data
  const allChanges = useWatch({
    control,
    name: "changes",
  });

  // Create a dependency value from stringified key fields to enable proper change detection
  // for nested form objects
  const changesDependency = JSON.stringify(
    allChanges.map((c) => ({
      inputName: c.inputName,
      type: c.type,
      record: c.record,
    })),
  );

  // Detect unsaved changes and notify parent
  useEffect(() => {
    if (onUnsavedChange) {
      const hasUnsaved = hasMeaningfulDiscardData(allChanges);
      onUnsavedChange(hasUnsaved);
    }
  }, [changesDependency, onUnsavedChange, allChanges]);

  // Browser refresh warning for unsaved data
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (hasMeaningfulDiscardData(allChanges)) {
        e.preventDefault();
        e.returnValue = "";
        return "";
      }
    };

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, [allChanges]);

  // Auto-focus the Change Type select of the newly added row whenever a row
  // is appended.
  useEffect(() => {
    if (fields.length > prevFieldsLengthRef.current) {
      const rows = document.querySelectorAll<HTMLElement>(
        '[data-change-row="true"]',
      );
      const lastRow = rows[rows.length - 1];
      if (lastRow) {
        const firstInput = lastRow.querySelector<HTMLElement>("select, input");
        if (firstInput) firstInput.focus();
      }
    }
    prevFieldsLengthRef.current = fields.length;
  }, [fields.length]);

  /**
   * Scrolls the first field that failed react-hook-form validation into view
   * and focuses it. This makes the inline error message visible to the user
   * when they click Submit but one or more rows are scrolled out of the viewport.
   * react-hook-form sets aria-invalid="true" on every registered input that
   * fails a validation rule, making them discoverable by a standard DOM query.
   */
  const onInvalid = () => {
    const firstInvalid = document.querySelector<HTMLElement>(
      '[aria-invalid="true"]',
    );
    if (firstInvalid) {
      firstInvalid.scrollIntoView({ behavior: "smooth", block: "center" });
      firstInvalid.focus({ preventScroll: true });
    }
  };

  /**
   * First-pass submit handler invoked by react-hook-form after validation passes.
   * Instead of calling `onSubmit` directly this stages the prepared payload in
   * `pendingSubmitData`, switching the footer to a confirmation panel. The second
   * click on "Confirm & Submit" is what actually calls `onSubmit`.
   */
  const handleFormSubmit = (data: DnsChangeFormData) => {
    setRowErrors([]);

    // A+PTR and AAAA+PTR are convenience compound types: each row expands into
    // a paired A/AAAA entry and a reverse PTR entry before reaching the API.
    // This mirrors the legacy portal's formatData function.
    const expandedChanges: ChangeFormItem[] = [];
    for (const entry of data.changes) {
      if (entry.type === "A+PTR" || entry.type === "AAAA+PTR") {
        const baseType = entry.type === "A+PTR" ? "A" : "AAAA";
        expandedChanges.push({ ...entry, type: baseType });
        expandedChanges.push({
          changeType: entry.changeType,
          type: "PTR",
          ttl: entry.ttl,
          inputName: (entry.record as RecordData)?.address ?? "",
          record: { ptrdname: entry.inputName },
        });
      } else if (entry.type === "NAPTR") {
        const r = entry.record as RecordData;
        expandedChanges.push({
          ...entry,
          record: { ...r, regexp: r?.regexp ?? "" },
        });
      } else {
        expandedChanges.push(entry);
      }
    }

    // For DeleteRecordSet: drop record if all values are empty.
    // Also strip NaN from TTL and any numeric record sub-fields produced by
    // valueAsNumber on blank number inputs, or left over when the user switches
    // record types (e.g. MX → A leaves preference: NaN on the row).
    const finalChanges = expandedChanges.map((entry) => {
      // TTL only applies to Add changes; DeleteRecordSet rows carry the default
      // value in the (disabled) input but must not send it to the API.
      const cleanTtl =
        entry.changeType !== "DeleteRecordSet" &&
        entry.ttl !== undefined &&
        !Number.isNaN(entry.ttl)
          ? entry.ttl
          : undefined;

      const cleanRecord = entry.record
        ? (Object.fromEntries(
            Object.entries(entry.record as Record<string, unknown>).filter(
              ([, v]) =>
                v !== undefined &&
                v !== null &&
                v !== "" &&
                !(typeof v === "number" && Number.isNaN(v)),
            ),
          ) as typeof entry.record)
        : entry.record;

      const cleaned: ChangeFormItem = {
        ...entry,
        ...(cleanTtl !== undefined ? { ttl: cleanTtl } : {}),
        record: cleanRecord,
      };

      if (cleaned.changeType === "DeleteRecordSet" && cleaned.record) {
        const allEmpty = Object.values(cleaned.record).every(
          (v) =>
            v === undefined ||
            v === null ||
            (typeof v === "string" && v.trim() === ""),
        );
        if (allEmpty) {
          const { record: _r, ...rest } = cleaned;
          return rest as ChangeFormItem;
        }
      }
      return cleaned;
    });

    // Stage the payload for user confirmation rather than submitting immediately.
    // The confirmation panel will display the change count and let the user
    // back out before the API call is made. 
    setPendingSubmitData({
      data: {
        comments: data.comments || undefined,
        ownerGroupId: data.ownerGroupId || undefined,
        scheduledTime:
          data.scheduledOption === "later" && data.scheduledTime
            ? new Date(data.scheduledTime).toISOString()
            : undefined,
        changes: finalChanges,
      },
      allowManualReview,
      // Count the user-facing rows, not the expanded A+PTR/AAAA+PTR pairs.
      rowCount: data.changes.length,
    });
  };

  /** Executes the staged submit after the user clicks "Confirm & Submit". */
  const handleConfirmSubmit = () => {
    if (!pendingSubmitData) return;
    onSubmit(pendingSubmitData.data, pendingSubmitData.allowManualReview);
    setPendingSubmitData(null);
  };

  /** Returns the form to edit mode without submitting. */
  const handleBackToEdit = () => {
    setPendingSubmitData(null);
  };

  const handleCsvImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // reset input so same file can be re-imported
    if (csvFileRef.current) csvFileRef.current.value = "";
    if (!file) return;
    if (!file.name.endsWith(".csv")) {
      setCsvAlert({
        type: "danger",
        message: "Import failed. File should be of '.csv' type.",
      });
      return;
    }
    const reader = new FileReader();
    reader.onload = (evt) => {
      const text = evt.target?.result as string;
      const { changes, error } = parseCsvToChanges(text, BATCH_CHANGE_LIMIT);
      if (error) {
        setCsvAlert({ type: "danger", message: error });
        return;
      }
      // Detect duplicate rows BEFORE applying. If any are found, defer the
      // replace() call and let the user resolve them in the review modal.
      const groups = findDuplicateGroups(changes);
      if (groups.length > 0) {
        // Default: keep the first occurrence of each duplicate group, plus
        // every unique row (rows that don't appear in any group).
        const inAnyGroup = new Set<number>();
        groups.forEach((g) => g.indices.forEach((i) => inAnyGroup.add(i)));
        const keep = new Set<number>();
        changes.forEach((_, i) => {
          if (!inAnyGroup.has(i)) keep.add(i);
        });
        groups.forEach((g) => keep.add(g.indices[0]));
        setDupReview({ changes, groups, keep });
        setCsvAlert(null);
        return;
      }
      replace(changes as Parameters<typeof replace>[0]);
      setCsvAlert({
        type: "success",
        message: `Successfully imported ${changes.length} DNS change${changes.length !== 1 ? "s" : ""}.`,
      });
    };
    reader.readAsText(file);
  };

  /**
   * Apply the user's keep/remove decisions from the duplicate-review modal.
   * Rebuilds the change list preserving the original CSV order and replaces
   * the form's field array. Shows a success alert that explicitly reports
   * how many duplicate rows were removed.
   */
  const handleDupReviewApply = () => {
    if (!dupReview) return;
    const kept = dupReview.changes.filter((_, i) => dupReview.keep.has(i));
    const removed = dupReview.changes.length - kept.length;
    replace(kept as Parameters<typeof replace>[0]);
    setCsvAlert({
      type: "success",
      message:
        removed > 0
          ? `Imported ${kept.length} DNS change${kept.length !== 1 ? "s" : ""} (removed ${removed} duplicate${removed !== 1 ? "s" : ""}).`
          : `Successfully imported ${kept.length} DNS change${kept.length !== 1 ? "s" : ""}.`,
    });
    setDupReview(null);
  };

  /** Discard the import entirely; no rows are added to the form. */
  const handleDupReviewCancel = () => {
    setDupReview(null);
    setCsvAlert({
      type: "danger",
      message: "Import cancelled. No changes were added.",
    });
  };

  /** Toggle keep/remove for a single row inside the duplicate modal. */
  const toggleDupKeep = (rowIdx: number) => {
    setDupReview((prev) => {
      if (!prev) return prev;
      const next = new Set(prev.keep);
      if (next.has(rowIdx)) next.delete(rowIdx);
      else next.add(rowIdx);
      return { ...prev, keep: next };
    });
  };

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(handleFormSubmit, onInvalid)} noValidate>
        {/* ── Section: Metadata ─────────────────────────────────── */}
        <div
          className="rounded-3 mb-3"
          style={{
            border: `1px solid ${isDark ? "#2d4163" : "#e2e8f0"}`,
            overflow: "hidden",
            background: isDark ? "#131c2e" : "#ffffff",
            boxShadow: isDark
              ? "0 1px 3px rgba(0,0,0,0.3)"
              : "0 1px 3px rgba(15,23,42,0.06)",
          }}
        >
          <div
            className="px-3 py-2 d-flex align-items-center gap-2"
            style={{
              background: isDark ? "#1a2536" : "#f8fafc",
              color: isDark ? "#cbd5e1" : "#1f2a44",
              borderBottom: `1px solid ${isDark ? "#2d3d52" : "#e2e8f0"}`,
            }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{ fontSize: "0.95rem", color: "#1e5fa8" }}
            />
            <span
              className="fw-semibold"
              style={{
                color: isDark ? "#e2e8f0" : "#1f2a44",
                fontSize: "0.9rem",
              }}
            >
              Batch Details
            </span>
          </div>
          <div className="px-3 py-2">
            <div className="row g-2 align-items-start">
              <div className="col-12 col-md-4">
                <label
                  className="form-label"
                  style={{
                    fontSize: "0.8rem",
                    fontWeight: 600,
                    color: isDark ? "#cbd5e1" : "#1f2a44",
                  }}
                >
                  Description
                  <span
                    style={{ fontWeight: 400, color: "#9aacbe", marginLeft: 4 }}
                  >
                    (optional)
                  </span>
                </label>
                <textarea
                  className="form-control form-control-sm"
                  rows={2}
                  placeholder="Brief description of this batch change"
                  style={{
                    background: isDark ? "#1a2640" : "#fff",
                    color: isDark ? "#cdd9ed" : "#212529",
                    borderColor: isDark ? "rgba(127,168,216,0.2)" : "#dde3ec",
                    boxShadow: "none",
                    borderRadius: "0.45rem",
                    resize: "none",
                  }}
                  {...register("comments")}
                />
              </div>
              <div className="col-12 col-sm-7 col-md-4">
                <label
                  className="form-label"
                  style={{
                    fontSize: "0.8rem",
                    fontWeight: 600,
                    color: isDark ? "#cbd5e1" : "#1f2a44",
                  }}
                >
                  Owner Group
                  <span
                    style={{ fontWeight: 400, color: "#9aacbe", marginLeft: 4 }}
                  >
                    (optional)
                  </span>
                </label>
                {isGroupsLoading ? (
                  <div
                    className="form-control form-control-sm"
                    style={{
                      background: isDark ? "#1a2640" : "#fff",
                      color: isDark ? "#cdd9ed" : "#212529",
                      borderColor: isDark ? "rgba(127,168,216,0.2)" : "#dde3ec",
                      boxShadow: "none",
                      borderRadius: "0.45rem",
                      opacity: 0.8,
                    }}
                  >
                    Loading groups…
                  </div>
                ) : groups.length > 0 ? (
                  <div style={{ position: "relative" }}>
                    <select
                      className={`form-select form-select-sm${ownerGroupError ? " is-invalid" : ""}`}
                      style={{
                        background: isDark ? "#1a2640" : "#fff",
                        color: isDark ? "#cdd9ed" : "#212529",
                        borderColor: ownerGroupError
                          ? "#dc3545"
                          : isDark
                            ? "rgba(127,168,216,0.2)"
                            : "#dde3ec",
                        boxShadow: "none",
                        borderRadius: "0.45rem",
                        appearance: "none",
                        WebkitAppearance: "none",
                        MozAppearance: "none",
                        paddingRight: "2rem",
                      }}
                      {...register("ownerGroupId")}
                      onFocus={() => setIsOwnerGroupMenuOpen(true)}
                      onBlur={() => setIsOwnerGroupMenuOpen(false)}
                    >
                      <option value="">— No owner group —</option>
                      {groups
                        .slice()
                        .sort((a, b) => a.name.localeCompare(b.name))
                        .map((g) => (
                          <option key={g.id} value={g.id}>
                            {g.name}
                          </option>
                        ))}
                    </select>
                    <i
                      className={`bi ${isOwnerGroupMenuOpen ? "bi-chevron-up" : "bi-chevron-down"}`}
                      style={{
                        position: "absolute",
                        right: 10,
                        top: "50%",
                        transform: "translateY(-50%)",
                        pointerEvents: "none",
                        fontSize: "0.72rem",
                        color: isDark ? "#cbd5e1" : "#64748b",
                      }}
                    />
                  </div>
                ) : (
                  <input
                    className={`form-control form-control-sm${ownerGroupError ? " is-invalid" : ""}`}
                    placeholder="Required for shared zone records"
                    style={{
                      background: isDark ? "#1a2640" : "#fff",
                      color: isDark ? "#cdd9ed" : "#212529",
                      borderColor: ownerGroupError
                        ? "#dc3545"
                        : isDark
                          ? "rgba(127,168,216,0.2)"
                          : "#dde3ec",
                      boxShadow: "none",
                      borderRadius: "0.45rem",
                    }}
                    {...register("ownerGroupId")}
                  />
                )}
                {ownerGroupError && (
                  <div
                    style={{
                      fontSize: "0.78rem",
                      color: "#b02a37",
                      marginTop: 4,
                    }}
                  >
                    <i className="bi bi-exclamation-circle me-1" />
                    <strong>
                      Record Owner Group is required for records in shared
                      zones.
                    </strong>
                  </div>
                )}
                <div
                  style={{
                    fontSize: "0.76rem",
                    color: "#6b7a90",
                    marginTop: 4,
                  }}
                >
                  Or you can{" "}
                  <a href="/groups" style={{ color: "#1e5fa8" }}>
                    create a new group from the Groups page
                  </a>
                  .
                </div>
              </div>
              <ScheduledTimeField
                register={register}
                watch={watch}
                isDark={isDark}
              />
            </div>
          </div>
        </div>

        {/* ── Section: Changes ──────────────────────────────────── */}
        <div
          className="rounded-3 mb-3"
          style={{
            border: `1px solid ${isDark ? "#2d4163" : "#e2e8f0"}`,
            overflow: "hidden",
            background: isDark ? "#131c2e" : "#ffffff",
            boxShadow: isDark
              ? "0 3px 6px rgba(0,0,0,0.3)"
              : "0 3px 6px rgba(15,23,42,0.06)",
          }}
        >
          <div
            className="px-3 py-2 d-flex align-items-center justify-content-between flex-wrap gap-2"
            style={{
              background: isDark ? "#1a2536" : "#f8fafc",
              color: isDark ? "#cbd5e1" : "#1f2a44",
              borderBottom: `1px solid ${isDark ? "#2d3d52" : "#e2e8f0"}`,
            }}
          >
            <div className="d-flex align-items-center gap-2">
              <i
                className="bi bi-list-check"
                style={{ fontSize: "0.95rem", color: "#1e5fa8" }}
              />
              <span
                className="fw-semibold"
                style={{
                  color: isDark ? "#e2e8f0" : "#1f2a44",
                  fontSize: "0.9rem",
                }}
              >
                DNS Changes
              </span>
              {fields.length > 0 && (
                <span
                  style={{
                    background: isDark
                      ? "rgba(30,95,168,0.25)"
                      : "rgba(30,95,168,0.1)",
                    color: isDark ? "#93c5fd" : "#1e5fa8",
                    fontSize: "0.72rem",
                    fontWeight: 700,
                    borderRadius: "999px",
                    padding: "2px 9px",
                    border: `1px solid ${isDark ? "rgba(30,95,168,0.4)" : "rgba(30,95,168,0.2)"}`,
                  }}
                >
                  {fields.length}
                </span>
              )}
            </div>
            <div className="d-flex align-items-start gap-2">
              <button
                type="button"
                className="vds-ubtn vds-ubtn--secondary"
                disabled={
                  fields.length >= BATCH_CHANGE_LIMIT ||
                  Boolean(pendingSubmitData)
                }
                onClick={() =>
                  append({
                    changeType: "Add",
                    inputName: "",
                    type: "A+PTR",
                    ttl: 300,
                    record: {},
                  })
                }
              >
                <i className="bi bi-plus-lg" />
                Add Change
              </button>

              <div className="d-flex flex-column align-items-center gap-1">
                <label
                  htmlFor="batchChangeCsv"
                  className="vds-ubtn vds-ubtn--secondary mb-0"
                  style={{
                    cursor: pendingSubmitData ? "not-allowed" : "pointer",
                    opacity: pendingSubmitData ? 0.55 : 1,
                    pointerEvents: pendingSubmitData ? "none" : "auto",
                  }}
                >
                  <i className="bi bi-upload" />
                  Import CSV
                </label>
                <a
                  href="https://www.vinyldns.io/portal/dns-changes#dns-change-csv-import"
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    fontSize: "0.72rem",
                    color: isDark ? "#7fb8f0" : "#1e5fa8",
                    textDecoration: "none",
                    whiteSpace: "nowrap",
                  }}
                >
                  <i className="bi bi-box-arrow-up-right me-1" />
                  Sample CSV format
                </a>
              </div>
              <input
                ref={csvFileRef}
                type="file"
                id="batchChangeCsv"
                accept=".csv"
                style={{ display: "none" }}
                onChange={handleCsvImport}
              />
            </div>
          </div>

          <div className="p-2">
            {csvAlert && (
              <div
                className={`alert alert-${csvAlert.type} d-flex align-items-center gap-2 py-2 px-3`}
                style={{ fontSize: "0.82rem", marginBottom: "0.5rem" }}
              >
                <i
                  className={`bi ${csvAlert.type === "success" ? "bi-check-circle" : "bi-exclamation-triangle"}`}
                />
                {csvAlert.message}
                <button
                  type="button"
                  onClick={() => setCsvAlert(null)}
                  style={{
                    marginLeft: "auto",
                    display: "flex",
                    alignItems: "center",
                    gap: 4,
                    padding: "0.2rem 0.55rem",
                    fontSize: "0.75rem",
                    fontWeight: 500,
                    border: `1px solid ${csvAlert.type === "success" ? "rgba(34,197,94,0.3)" : "rgba(239,68,68,0.3)"}`,
                    background:
                      csvAlert.type === "success"
                        ? "rgba(34,197,94,0.08)"
                        : "rgba(239,68,68,0.08)",
                    color: csvAlert.type === "success" ? "#059669" : "#dc2626",
                    borderRadius: "0.35rem",
                    cursor: "pointer",
                  }}
                >
                  <i className="bi bi-x-lg" />
                  Dismiss
                </button>
              </div>
            )}

            {fields.length >= BATCH_CHANGE_LIMIT && (
              <div
                className="alert alert-warning d-flex align-items-center gap-2 py-2 px-3"
                style={{ fontSize: "0.82rem", marginBottom: "0.5rem" }}
              >
                <i className="bi bi-exclamation-triangle-fill" />
                Limit reached. Cannot add more than {BATCH_CHANGE_LIMIT} records
                per DNS change.
              </div>
            )}

            {fields.length === 0 ? (
              <div
                style={{
                  border: `2px dashed ${isDark ? "#3d5273" : "#94a3b8"}`,
                  borderRadius: "0.65rem",
                  padding: "2rem 1rem",
                  textAlign: "center",
                  color: isDark ? "#64748b" : "#475569",
                  background: isDark ? "#1e293b" : "#f8fafc",
                }}
              >
                <i
                  className="bi bi-plus-circle"
                  style={{
                    fontSize: "1.6rem",
                    display: "block",
                    marginBottom: "0.4rem",
                  }}
                />
                <span style={{ fontSize: "0.85rem", fontWeight: 500 }}>
                  No changes added yet
                </span>
                <br />
                <span style={{ fontSize: "0.78rem" }}>
                  Click <strong>Add Change</strong> to get started
                </span>
              </div>
            ) : (
              <div
                style={{
                  overflowX: "auto",
                  overflowY: "auto",
                  maxHeight: "calc(100vh - 500px)",
                }}
              >
                <table
                  style={{
                    width: "100%",
                    borderCollapse: "collapse",
                    fontSize: "0.82rem",
                  }}
                >
                  <thead>
                    <tr>
                      {[
                        "#",
                        "Change Type",
                        "Record Type",
                        "Input Name",
                        "TTL",
                        "Record Data",
                        "Actions",
                      ].map((h) => (
                        <th
                          key={h}
                          style={{
                            padding: "0.3rem 0.4rem",
                            fontSize: "0.72rem",
                            fontWeight: 700,
                            textTransform: "uppercase",
                            letterSpacing: "0.04em",
                            color: isDark ? "#64748b" : "#64748b",
                            background: isDark ? "#1a2536" : "#f8fafd",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {fields.map((field, index) => (
                      <ChangeRow
                        key={field.id}
                        index={index}
                        remove={remove}
                        serverErrors={effectiveRowErrors[index]}
                        disabled={Boolean(pendingSubmitData)}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* ── Footer Actions ────────────────────────────────────── */}
        <div
          style={{
            paddingTop: "0.1rem",
            paddingBottom: "0",
            position: "sticky",
            bottom: 0,
            backgroundColor: isDark ? "#0f172a" : "#ffffff",
            zIndex: 10,
          }}
        >
          {pendingSubmitData ? (
            <div style={{ padding: "0.05rem 0 0.5rem 0" }}>
              <div
                className="d-flex align-items-center gap-2 p-2 mb-2"
                style={{
                  background: isDark
                    ? "rgba(255,193,7,0.08)"
                    : "rgba(255,193,7,0.12)",
                  border: "1px solid rgba(255,193,7,0.35)",
                  borderRadius: "0.5rem",
                  fontSize: "0.85rem",
                  color: isDark ? "#ffe082" : "#664d03",
                }}
              >
                <i
                  className="bi bi-exclamation-triangle-fill"
                  style={{ flexShrink: 0, fontSize: "1rem", lineHeight: 1.4 }}
                />
                <div>
                  <strong>Review before submitting:</strong> You are about to
                  submit{" "}
                  <strong>
                    {pendingSubmitData.rowCount} DNS change
                    {pendingSubmitData.rowCount !== 1 ? "s" : ""}
                  </strong>
                  .
                  {pendingSubmitData.data.scheduledTime && (
                    <>
                      {" "}
                      Scheduled for:{" "}
                      <strong>{pendingSubmitData.data.scheduledTime}</strong>.
                    </>
                  )}{" "}
                  This action cannot be undone once submitted.
                </div>
              </div>
              <div className="d-flex align-items-center gap-2">
                <button
                  type="button"
                  className="vds-ubtn vds-ubtn--primary"
                  onClick={handleConfirmSubmit}
                  disabled={isSubmitting}
                  style={
                    isDark
                      ? {
                          backgroundColor: "#2563eb",
                          color: "#f8fafc",
                          borderColor: "#3b82f6",
                          boxShadow: "0 1px 3px rgba(37, 99, 235, 0.25)",
                        }
                      : undefined
                  }
                >
                  {isSubmitting ? (
                    <>
                      <span className="spinner-border spinner-border-sm" />
                      Submitting…
                    </>
                  ) : (
                    <>
                      <i className="bi bi-send-fill" />
                      Confirm &amp; Submit
                    </>
                  )}
                </button>
                <button
                  type="button"
                  className="vds-ubtn vds-ubtn--secondary"
                  onClick={handleBackToEdit}
                  disabled={isSubmitting}
                >
                  <i className="bi bi-arrow-left" />
                  Back to Edit
                </button>
              </div>
            </div>
          ) : (
            <div
              className="d-flex align-items-center gap-2"
              style={{ padding: "0.25rem 0 0 0" }}
            >
              <button
                type="submit"
                className="vds-ubtn vds-ubtn--primary"
                disabled={fields.length === 0 || isSubmitting}
                style={
                  isDark
                    ? {
                        backgroundColor: "#2563eb",
                        color: "#f8fafc",
                        borderColor: "#3b82f6",
                        boxShadow: "0 1px 3px rgba(37, 99, 235, 0.25)",
                      }
                    : undefined
                }
              >
                {isSubmitting ? (
                  <>
                    <span className="spinner-border spinner-border-sm" />
                    Submitting…
                  </>
                ) : (
                  <>
                    <i className="bi bi-send-fill" />
                    Submit Batch Change
                  </>
                )}
              </button>
              <button
                type="button"
                className="vds-ubtn vds-ubtn--secondary"
                onClick={() => {
                  if (hasMeaningfulDiscardData(allChanges)) {
                    setShowCancelConfirm(true);
                  } else {
                    onCancel();
                  }
                }}
                disabled={isSubmitting}
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      </form>
      {dupReview && (
        <DuplicateReviewModal
          state={dupReview}
          isDark={isDark}
          onToggleKeep={toggleDupKeep}
          onApply={handleDupReviewApply}
          onCancel={handleDupReviewCancel}
        />
      )}

      {/* ── Cancel confirmation modal ── */}
      {showCancelConfirm && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-confirm-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowCancelConfirm(false);
          }}
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(15,23,42,0.65)",
            backdropFilter: "blur(3px)",
            zIndex: 1090,
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
              boxShadow: "0 20px 50px rgba(0,0,0,0.4)",
              width: "min(420px, 100%)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.85rem",
                padding: "1rem 1.25rem",
                borderBottom: `1px solid ${isDark ? "#2d4163" : "#e8ecf0"}`,
              }}
            >
              <span
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: "50%",
                  background: isDark ? "#3f1d1d" : "#fef2f2",
                  color: "#dc2626",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "1rem",
                  flexShrink: 0,
                }}
              >
                <i className="bi bi-exclamation-triangle-fill" />
              </span>
              <h6
                id="cancel-confirm-title"
                style={{
                  margin: 0,
                  fontWeight: 600,
                  fontSize: "1rem",
                  color: isDark ? "#e2e8f0" : "#0d1b3e",
                }}
              >
                Discard batch change?
              </h6>
            </div>
            <div style={{ padding: "1rem 1.25rem" }}>
              <p
                style={{
                  margin: 0,
                  fontSize: "0.88rem",
                  color: isDark ? "#94a3b8" : "#475569",
                }}
              >
                All changes entered so far will be lost. This action cannot be
                undone.
              </p>
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "0.5rem",
                padding: "0.75rem 1.25rem",
                borderTop: `1px solid ${isDark ? "#2d4163" : "#e8ecf0"}`,
                background: isDark ? "#162032" : "#f8fafd",
              }}
            >
              <button
                type="button"
                onClick={() => setShowCancelConfirm(false)}
                style={{
                  padding: "0.45rem 1rem",
                  background: "transparent",
                  border: `1px solid ${isDark ? "#2d4163" : "#d4dae3"}`,
                  color: isDark ? "#94a3b8" : "#5a6a85",
                  borderRadius: "0.45rem",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  fontWeight: 500,
                }}
              >
                Keep editing
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowCancelConfirm(false);
                  onCancel();
                }}
                style={{
                  padding: "0.45rem 1.1rem",
                  background: "#dc2626",
                  border: "none",
                  color: "#fff",
                  borderRadius: "0.45rem",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  fontWeight: 600,
                  boxShadow: "0 2px 8px rgba(220,38,38,0.3)",
                }}
              >
                <i className="bi bi-trash3-fill me-1" />
                Discard changes
              </button>
            </div>
          </div>
        </div>
      )}
    </FormProvider>
  );
}
