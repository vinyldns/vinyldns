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
import { DiscardChangesModal } from "../modals/DiscardChangesModal";
import { DuplicateReviewModal } from "../modals/DuplicateReviewModal";

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

interface DnsChangeFormData {
  comments: string;
  ownerGroupId: string;
  scheduledOption: "now" | "later";
  scheduledTime: string;
  changes: ChangeFormItem[];
}

const BATCH_CHANGE_LIMIT = 1000;

const RE_IPV4 =
  /^(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)$/;

const RE_IPV6 =
  /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$/;

const RE_FQDN =
  /^(\*\.)?([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+([a-zA-Z]{2,}\.?)$/;

function decodeCsvRow(row: string): string[] {
  const regex = /(,|\r?\n|\r|^)(?:"([^"]*(?:""[^"]*)*)"|([^,\r\n]*))/gi;
  const matches = [...row.matchAll(regex)];
  return matches.map((m) =>
    m[2] !== undefined ? m[2].replace(/""/g, '"') : (m[3] ?? ""),
  );
}

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
    recEntries,
  ].join("::");
}

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

function RecordDataFields({
  index,
  recordType,
  isAdd,
}: {
  index: number;
  recordType: string;
  isAdd: boolean;
}) {
  const {
    register,
    formState: { errors },
  } = useFormContext<DnsChangeFormData>();
  const req = isAdd;

  switch (recordType) {
    case "A":
    case "A+PTR":
      return (
        <input
          className="form-control form-control-sm vds-form-input"
          placeholder="e.g. 1.1.1.1"
          autoComplete="off"
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
          className="form-control form-control-sm vds-form-input"
          placeholder="fd69:27cc::60"
          autoComplete="off"
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
          className="form-control form-control-sm vds-form-input"
          placeholder="target.example.com."
          autoComplete="off"
          disabled={!isAdd}
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
          className="form-control form-control-sm vds-form-input"
          placeholder="test.example.com."
          autoComplete="off"
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
          className="form-control form-control-sm vds-form-input"
          placeholder="attr=val"
          autoComplete="off"
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
            className="form-control form-control-sm vds-form-input"
            placeholder="Pref"
            min={0}
            max={65535}
            style={{ width: 70 }}
            {...register(`changes.${index}.record.preference`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
              min: 0,
              max: 65535,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm vds-form-input"
            placeholder="mail.example.com."
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
          className="form-control form-control-sm vds-form-input"
          placeholder="ns1.example.com."
          autoComplete="off"
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
            className="form-control form-control-sm vds-form-input"
            placeholder="Pri"
            min={0}
            max={65535}
            style={{ width: 60 }}
            {...register(`changes.${index}.record.priority`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm vds-form-input"
            placeholder="Wt"
            min={0}
            max={65535}
            style={{ width: 60 }}
            {...register(`changes.${index}.record.weight`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm vds-form-input"
            placeholder="Port"
            min={0}
            max={65535}
            style={{ width: 70 }}
            {...register(`changes.${index}.record.port`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            className="form-control form-control-sm vds-form-input"
            placeholder="target.example.com."
            autoComplete="off"
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
            className="form-control form-control-sm vds-form-input"
            placeholder="Ord"
            min={0}
            max={65535}
            style={{ width: 60 }}
            {...register(`changes.${index}.record.order`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <input
            type="number"
            className="form-control form-control-sm vds-form-input"
            placeholder="Pref"
            min={0}
            max={65535}
            style={{ width: 60 }}
            {...register(`changes.${index}.record.preference`, {
              required: req ? "Record data is required" : false,
              valueAsNumber: true,
            })}
          />
          <select
            className="form-select form-select-sm vds-form-input"
            style={{ width: 70 }}
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
            className="form-control form-control-sm vds-form-input"
            placeholder="SIP+D2U"
            autoComplete="off"
            style={{ width: 90 }}
            {...register(`changes.${index}.record.service`, {
              required: req ? "Record data is required" : false,
            })}
          />
          <input
            className="form-control form-control-sm vds-form-input"
            placeholder="Regexp"
            autoComplete="off"
            style={{ width: 80 }}
            {...register(`changes.${index}.record.regexp`)}
          />
          <input
            className="form-control form-control-sm vds-form-input"
            placeholder="Replacement"
            autoComplete="off"
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

  const isAdd = changeType === "Add";
  const isPtr = recordType === "PTR";
  const hasErrors = serverErrors && serverErrors.length > 0;

  const { onChange: onTypeChange, ...restTypeRegister } = register(
    `changes.${index}.type`,
  );

  return (
    <tr data-change-row="true">
      {/* # */}
      <td
        className={`vds-table-cell vds-cell-index ${hasErrors ? "vds-table-cell-error" : ""}`}
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
      <td
        className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}
        style={{ width: 130 }}
      >
        <select
          className="form-select form-select-sm vds-form-input"
          {...register(`changes.${index}.changeType`)}
        >
          <option value="Add">Add</option>
          <option value="DeleteRecordSet">Delete</option>
        </select>
      </td>

      {/* Record Type */}
      <td
        className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}
        style={{ width: 110 }}
      >
        <select
          className="form-select form-select-sm vds-form-input"
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
      <td
        className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}
        style={{ minWidth: 200 }}
      >
        <div
          style={{ display: "flex", flexDirection: "column", height: "100%" }}
        >
          <input
            className={`form-control form-control-sm vds-form-input ${errors?.changes?.[index]?.inputName ? "is-invalid" : ""}`}
            placeholder={isPtr ? "192.0.2.193" : "host.example.com."}
            autoComplete="off"
            aria-invalid={
              errors?.changes?.[index]?.inputName ? "true" : undefined
            }
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
            <div className="vds-field-error-text">
              <i className="bi bi-exclamation-circle-fill" />
              {errors.changes[index]?.inputName?.message ||
                "Input Name is required"}
            </div>
          )}
        </div>
      </td>

      {/* TTL */}
      <td
        className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}
        style={{ width: 80 }}
      >
        <input
          type="number"
          className="form-control form-control-sm vds-form-input"
          placeholder=""
          autoComplete="off"
          disabled={!isAdd}
          min={30}
          max={2147483647}
          {...register(`changes.${index}.ttl`, { valueAsNumber: true })}
        />
      </td>

      {/* Record Data */}
      <td className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}>
        <div
          style={{ display: "flex", flexDirection: "column", height: "100%" }}
        >
          <RecordDataFields
            index={index}
            recordType={recordType}
            isAdd={isAdd}
          />
          {(() => {
            const recordErrors = errors?.changes?.[index]?.record as
              | Record<string, { message?: string } | undefined>
              | undefined;
            const recordErrorMessage = Object.values(recordErrors ?? {}).find(
              (value) =>
                value && typeof value === "object" && "message" in value,
            )?.message;
            return recordErrorMessage ? (
              <div className="vds-field-error-text">
                <i className="bi bi-exclamation-circle-fill" />
                {recordErrorMessage}
              </div>
            ) : null;
          })()}
        </div>
      </td>

      {serverErrors && serverErrors.length > 0 && (
        <td className="vds-table-cell vds-table-cell-error">
          <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
            {serverErrors.map((message) => (
              <div key={message} className="vds-field-error-text">
                <i className="bi bi-exclamation-circle-fill" />
                {message}
              </div>
            ))}
          </div>
        </td>
      )}

      {/* Remove */}
      <td
        className={`vds-table-cell ${hasErrors ? "vds-table-cell-error" : ""}`}
        style={{ width: 90, textAlign: "center" }}
      >
        <button
          type="button"
          onClick={() => remove(index)}
          disabled={disabled}
          title={disabled ? "Editing is locked during review" : "Remove row"}
          aria-label="Delete row"
          className="btn btn-sm d-inline-flex align-items-center gap-1 vds-btn-delete-row"
        >
          <i className="bi bi-trash3" />
          Delete
        </button>
      </td>
    </tr>
  );
}

function ScheduledTimeField({
  register,
  watch,
}: {
  register: ReturnType<typeof useForm<DnsChangeFormData>>["register"];
  watch: ReturnType<typeof useForm<DnsChangeFormData>>["watch"];
}) {
  const scheduledOption = watch("scheduledOption");
  const localTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <div className="col-12 col-md-4">
      <label className="form-label vds-form-label">Request Date/Time</label>
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
            className="form-control form-control-sm vds-form-input"
            style={{ borderRadius: "0.45rem" }}
            {...register("scheduledTime")}
          />
          <span className="text-muted small text-nowrap">{localTz}</span>
        </div>
      )}
    </div>
  );
}

export function hasMeaningfulDiscardData(changes: ChangeFormItem[]): boolean {
  if (changes.length >= 2) return true;

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

interface DnsChangeFormProps {
  onSubmit: (data: CreateDnsChangeRequest, allowManualReview: boolean) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  serverRowErrors?: string[][];
  onUnsavedChange?: (hasUnsaved: boolean) => void;
}

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
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [isOwnerGroupMenuOpen, setIsOwnerGroupMenuOpen] = useState(false);
  const csvFileRef = useRef<HTMLInputElement>(null);
  const prevFieldsLengthRef = useRef(0);
  const shouldWarnRef = useRef(false);

  const { data: groupsData, isLoading: isGroupsLoading } = useQuery({
    queryKey: ["groups-for-dns-form"],
    queryFn: async () => {
      const res = await groupsService.getGroups(true);
      return res.data.groups ?? [];
    },
    staleTime: 5 * 60 * 1000,
  });
  const groups = groupsData ?? [];

  const effectiveRowErrors = serverRowErrors ?? rowErrors;
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

  const allChanges = useWatch({ control, name: "changes" });
  const changesDependency = JSON.stringify(
    allChanges.map((c) => ({
      inputName: c.inputName,
      type: c.type,
      record: c.record,
    })),
  );

  useEffect(() => {
    if (onUnsavedChange) {
      const hasUnsaved = hasMeaningfulDiscardData(allChanges);
      onUnsavedChange(hasUnsaved);
    }
  }, [changesDependency, onUnsavedChange, allChanges]);

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

  const onInvalid = () => {
    const firstInvalid = document.querySelector<HTMLElement>(
      '[aria-invalid="true"]',
    );
    if (firstInvalid) {
      firstInvalid.scrollIntoView({ behavior: "smooth", block: "center" });
      firstInvalid.focus({ preventScroll: true });
    }
  };

  const handleFormSubmit = (data: DnsChangeFormData) => {
    setRowErrors([]);
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

    const finalChanges = expandedChanges.map((entry) => {
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
      rowCount: data.changes.length,
    });
  };

  const handleConfirmSubmit = () => {
    if (!pendingSubmitData) return;
    onSubmit(pendingSubmitData.data, pendingSubmitData.allowManualReview);
    setPendingSubmitData(null);
  };

  const handleBackToEdit = () => {
    setPendingSubmitData(null);
  };

  const handleCsvImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
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
      const groups = findDuplicateGroups(changes);
      if (groups.length > 0) {
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

  const handleDupReviewCancel = () => {
    setDupReview(null);
    setCsvAlert({
      type: "danger",
      message: "Import cancelled. No changes were added.",
    });
  };

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
        <div className="vds-panel mb-3">
          <div className="vds-panel-header px-3 py-2">
            <i
              className="bi bi-info-circle-fill"
              style={{ fontSize: "0.95rem", color: "#1e5fa8" }}
            />
            <span className="vds-panel-title">Batch Details</span>
          </div>
          <div className="px-3 py-2">
            <div className="row g-2 align-items-start">
              <div className="col-12 col-md-4">
                <label className="form-label vds-form-label">
                  Description
                  <span className="vds-form-label-optional">(optional)</span>
                </label>
                <textarea
                  className="form-control form-control-sm vds-form-input"
                  rows={2}
                  placeholder="Brief description of this batch change"
                  style={{ borderRadius: "0.45rem", resize: "none" }}
                  {...register("comments")}
                />
              </div>
              <div className="col-12 col-sm-7 col-md-4">
                <label className="form-label vds-form-label">
                  Owner Group
                  <span className="vds-form-label-optional">(optional)</span>
                </label>
                {isGroupsLoading ? (
                  <div
                    className="form-control form-control-sm vds-form-input"
                    style={{ borderRadius: "0.45rem", opacity: 0.8 }}
                  >
                    Loading groups…
                  </div>
                ) : groups.length > 0 ? (
                  <div style={{ position: "relative" }}>
                    <select
                      className={`form-select form-select-sm vds-form-input ${ownerGroupError ? "is-invalid" : ""}`}
                      style={{
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
                      className={`bi ${isOwnerGroupMenuOpen ? "bi-chevron-up" : "bi-chevron-down"} vds-select-chevron`}
                    />
                  </div>
                ) : (
                  <input
                    className={`form-control form-control-sm vds-form-input ${ownerGroupError ? "is-invalid" : ""}`}
                    placeholder="Required for shared zone records"
                    style={{ borderRadius: "0.45rem" }}
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
                  <a href="/groups" className="vds-help-link">
                    create a new group from the Groups page
                  </a>
                  .
                </div>
              </div>
              <ScheduledTimeField register={register} watch={watch} />
            </div>
          </div>
        </div>

        {/* ── Section: Changes ──────────────────────────────────── */}
        <div className="vds-panel vds-panel-shadow-lg mb-3">
          <div className="vds-panel-header justify-content-between flex-wrap px-3 py-2">
            <div className="d-flex align-items-center gap-2">
              <i
                className="bi bi-list-check"
                style={{ fontSize: "0.95rem", color: "#1e5fa8" }}
              />
              <span className="vds-panel-title">DNS Changes</span>
              {fields.length > 0 && (
                <span className="vds-badge-count">{fields.length}</span>
              )}
            </div>
            <div className="d-flex align-items-start gap-2">
              <button
                type="button"
                className="vds-ubtn vds-ubtn--add-change"
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
                  className="vds-ubtn vds-ubtn--import-csv mb-0"
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
                  className="vds-help-link"
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
              <div className="vds-empty-state">
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
                  Click <strong>Add Row</strong> to get started
                </span>
              </div>
            ) : (
              <div className="vds-table-container">
                <table className="vds-changes-table">
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
                        <th key={h} className="vds-table-th">
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
        <div className="vds-form-footer">
          {pendingSubmitData ? (
            <div style={{ padding: "0.05rem 0 0.5rem 0" }}>
              <div className="vds-review-warning d-flex align-items-center gap-2 p-2 mb-2">
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
                  className="vds-ubtn vds-ubtn--primary vds-btn-submit-primary"
                  onClick={handleConfirmSubmit}
                  disabled={isSubmitting}
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
                className="vds-ubtn vds-ubtn--add-change"
                disabled={fields.length === 0 || isSubmitting}
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
                className="vds-ubtn vds-ubtn--danger"
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
          onToggleKeep={toggleDupKeep}
          onApply={handleDupReviewApply}
          onCancel={handleDupReviewCancel}
        />
      )}

      <DiscardChangesModal
        isOpen={showCancelConfirm}
        title="Discard batch change?"
        description="All changes entered so far will be lost. This action cannot be undone."
        cancelLabel="Keep editing"
        confirmLabel="Discard changes"
        onClose={() => setShowCancelConfirm(false)}
        onConfirm={() => {
          setShowCancelConfirm(false);
          onCancel();
        }}
      />
    </FormProvider>
  );
}