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

import { describe, it, expect } from "vitest";
import {
  parseCsvToChanges,
  changeSignature,
  findDuplicateGroups,
  formatRecordData,
  toLocalDateTimeInput,
  localDatetimeToUtc,
  getLocalTimeZoneLabel,
  validateScheduledTime,
  type ChangeFormItem,
} from "../../../components/dnsChanges/DnsChangeForm";

describe("localDatetimeToUtc", () => {
  it("returns UTC ISO format without milliseconds (legacy portal format)", () => {
    const result = localDatetimeToUtc("2026-11-18T15:00");
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00Z$/);
    expect(result).not.toContain(".");
  });

  it("round-trips to the same instant as the local input", () => {
    const result = localDatetimeToUtc("2026-11-21T15:00");
    expect(new Date(result).getTime()).toBe(
      new Date(2026, 10, 21, 15, 0, 0, 0).getTime(),
    );
  });

  it("returns empty string for invalid input", () => {
    expect(localDatetimeToUtc("")).toBe("");
    expect(localDatetimeToUtc("invalid")).toBe("");
    expect(localDatetimeToUtc("2026-13-45T25:70")).toBe("");
  });
});

describe("getLocalTimeZoneLabel", () => {
  it("returns a short zone label, not an IANA id", () => {
    const label = getLocalTimeZoneLabel();
    expect(label).not.toBe("");
    expect(label).not.toContain("/");
  });
});

describe("validateScheduledTime", () => {
  it("is valid when not scheduling for later", () => {
    expect(validateScheduledTime("", "now")).toBe(true);
  });

  it("requires a value when scheduling for later", () => {
    expect(validateScheduledTime("", "later")).toBe("Select a date and time.");
  });

  it("rejects invalid date strings", () => {
    expect(validateScheduledTime("invalid", "later")).toBe(
      "Select a valid date and time.",
    );
  });

  it("rejects a time in the past", () => {
    const now = new Date();
    const pastDate = new Date(now.getTime() - 2 * 60 * 60 * 1000);
    const pastLocalStr = toLocalDateTimeInput(pastDate);
    expect(validateScheduledTime(pastLocalStr, "later")).toBe(
      "Scheduled time must be in the future.",
    );
  });

  it("accepts a time in the future", () => {
    const now = new Date();
    const futureDate = new Date(now.getTime() + 2 * 60 * 60 * 1000);
    const futureLocalStr = toLocalDateTimeInput(futureDate);
    expect(validateScheduledTime(futureLocalStr, "later")).toBe(true);
  });
});

const HEADER = "Change Type,Record Type,Input Name,TTL,Record Data";

describe("parseCsvToChanges", () => {
  it("rejects a CSV that does not start with the canonical header", () => {
    const result = parseCsvToChanges(
      "foo,bar\nAdd,A,host.example.,300,1.2.3.4",
      1000,
    );
    expect(result.changes).toEqual([]);
    expect(result.error).toMatch(/Import failed/);
    expect(result.error).toMatch(/header must be/);
  });

  it("parses Add rows for A, CNAME, TXT, MX, NAPTR, SRV", () => {
    const csv = [
      HEADER,
      "Add,A,host.example.,300,1.2.3.4",
      "Add,CNAME,alias.example.,300,target.example.",
      'Add,TXT,txt.example.,300,"hello world"',
      "Add,MX,mx.example.,300,10 mail.example.",
      'Add,NAPTR,naptr.example.,300,100 50 U E2U+sip "!^.*$!sip:test@example.!" .',
      "Add,SRV,_sip._tcp.example.,300,10 20 5060 sip.example.",
    ].join("\n");

    const { changes, error } = parseCsvToChanges(csv, 1000);
    expect(error).toBeUndefined();
    expect(changes).toHaveLength(6);

    expect(changes[0]).toMatchObject({
      changeType: "Add",
      type: "A",
      inputName: "host.example.",
      ttl: 300,
      record: { address: "1.2.3.4" },
    });
    expect(changes[1].record).toEqual({ cname: "target.example." });
    expect(changes[2].record).toEqual({ text: "hello world" });
    expect(changes[3].record).toEqual({
      preference: 10,
      exchange: "mail.example.",
    });
    expect(changes[4].record).toMatchObject({
      order: 100,
      preference: 50,
      flags: "U",
      service: "E2U+sip",
    });
    expect(changes[5].record).toEqual({
      priority: 10,
      weight: 20,
      port: 5060,
      target: "sip.example.",
    });
  });

  it('routes any row whose change type contains "delete" to DeleteRecordSet', () => {
    const csv = [HEADER, "DeleteRecordSet,A,host.example.,300,1.2.3.4"].join(
      "\n",
    );
    const { changes } = parseCsvToChanges(csv, 1000);
    expect(changes[0].changeType).toBe("DeleteRecordSet");
  });

  it("ignores blank or comma-only trailing rows", () => {
    const csv = [HEADER, "Add,A,host.example.,300,1.2.3.4", "", ",,,,"].join(
      "\n",
    );
    const { changes } = parseCsvToChanges(csv, 1000);
    expect(changes).toHaveLength(1);
  });

  it("returns an error when row count exceeds the supplied limit", () => {
    const rows = Array.from(
      { length: 5 },
      (_, i) => `Add,A,host${i}.example.,300,1.2.3.${i}`,
    );
    const csv = [HEADER, ...rows].join("\n");
    const result = parseCsvToChanges(csv, 3);
    expect(result.changes).toEqual([]);
    expect(result.error).toMatch(/Cannot add more than 3/);
  });

  it("falls back to a 5-field NAPTR layout when regexp is omitted", () => {
    const csv = [
      HEADER,
      "Add,NAPTR,naptr.example.,300,100 50 U E2U+sip target.example.",
    ].join("\n");
    const { changes } = parseCsvToChanges(csv, 1000);
    expect(changes[0].record).toMatchObject({
      order: 100,
      preference: 50,
      flags: "U",
      service: "E2U+sip",
      regexp: "",
      replacement: "target.example.",
    });
  });
});

describe("changeSignature", () => {
  const base: ChangeFormItem = {
    changeType: "Add",
    type: "A",
    inputName: "host.example.",
    ttl: 300,
    record: { address: "1.2.3.4" },
  };

  it("produces the same signature for identical rows ignoring case/whitespace", () => {
    const a = changeSignature(base);
    const b = changeSignature({
      ...base,
      inputName: "  HOST.example.  ",
      record: { address: " 1.2.3.4 " },
    });
    expect(a).toBe(b);
  });

  it("changes signature when any of the four tracked fields differs", () => {
    const original = changeSignature(base);
    expect(
      changeSignature({ ...base, changeType: "DeleteRecordSet" }),
    ).not.toBe(original);
    expect(changeSignature({ ...base, type: "AAAA" })).not.toBe(original);
    expect(changeSignature({ ...base, inputName: "other.example." })).not.toBe(
      original,
    );
    expect(
      changeSignature({ ...base, record: { address: "5.6.7.8" } }),
    ).not.toBe(original);
  });

  it("ignores TTL when computing the signature (duplicate semantics)", () => {
    // TTL is intentionally excluded so rows with the same record data but
    // different TTLs are still flagged as duplicates — the user picks which
    // TTL to keep at the resolution step.
    expect(changeSignature({ ...base, ttl: 600 })).toBe(changeSignature(base));
  });

  it("ignores empty/undefined record sub-fields when computing the signature", () => {
    const a = changeSignature(base);
    const b = changeSignature({
      ...base,
      record: { address: "1.2.3.4", cname: "", priority: undefined },
    });
    expect(a).toBe(b);
  });
});

describe("findDuplicateGroups", () => {
  it("returns an empty array when every row is unique", () => {
    const rows: ChangeFormItem[] = [
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: { address: "1.1.1.1" },
      },
      {
        changeType: "Add",
        type: "A",
        inputName: "b.example.",
        ttl: 60,
        record: { address: "1.1.1.2" },
      },
    ];
    expect(findDuplicateGroups(rows)).toEqual([]);
  });

  it("groups identical rows and tracks their original indices", () => {
    const dupRecord = { address: "1.1.1.1" };
    const rows: ChangeFormItem[] = [
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: dupRecord,
      },
      {
        changeType: "Add",
        type: "A",
        inputName: "b.example.",
        ttl: 60,
        record: { address: "1.1.1.2" },
      },
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: dupRecord,
      },
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: dupRecord,
      },
    ];
    const groups = findDuplicateGroups(rows);
    expect(groups).toHaveLength(1);
    expect(groups[0].indices).toEqual([0, 2, 3]);
  });

  it("detects multiple independent duplicate groups", () => {
    const rows: ChangeFormItem[] = [
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: { address: "1.1.1.1" },
      },
      {
        changeType: "Add",
        type: "CNAME",
        inputName: "b.example.",
        ttl: 60,
        record: { cname: "x." },
      },
      {
        changeType: "Add",
        type: "A",
        inputName: "a.example.",
        ttl: 60,
        record: { address: "1.1.1.1" },
      },
      {
        changeType: "Add",
        type: "CNAME",
        inputName: "b.example.",
        ttl: 60,
        record: { cname: "x." },
      },
    ];
    const groups = findDuplicateGroups(rows);
    expect(groups).toHaveLength(2);
    const allIndices = groups.flatMap((g) => g.indices).sort();
    expect(allIndices).toEqual([0, 1, 2, 3]);
  });
});

describe("formatRecordData", () => {
  it("returns em-dash for missing record data", () => {
    expect(formatRecordData(undefined)).toBe("—");
    expect(formatRecordData({})).toBe("—");
  });

  it("alphabetises keys and drops empties for stable display", () => {
    const out = formatRecordData({
      port: 5060,
      weight: 10,
      priority: 1,
      target: "sip.example.",
      preference: undefined,
    });
    expect(out).toBe(
      "port: 5060, priority: 1, target: sip.example., weight: 10",
    );
  });
});
