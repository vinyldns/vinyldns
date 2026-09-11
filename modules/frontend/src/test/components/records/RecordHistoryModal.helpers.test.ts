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
  changeTypeBadgeClass,
  statusBadgeClass,
  formatHistoryTime,
  formatRecordValues,
} from "../../../components/records/RecordHistoryModal";

describe("changeTypeBadgeClass", () => {
  it.each([
    ["Create", "vds-change-type-badge--add"],
    ["create", "vds-change-type-badge--add"],
    ["Delete", "vds-change-type-badge--delete"],
    ["Update", "vds-change-type-badge--update"],
  ])("maps %s → %s", (type, expected) => {
    expect(changeTypeBadgeClass(type)).toBe(expected);
  });

  it("returns default for empty / unknown types", () => {
    expect(changeTypeBadgeClass("")).toBe("vds-change-type-badge--default");
    expect(changeTypeBadgeClass("Unknown")).toBe(
      "vds-change-type-badge--default",
    );
  });
});

describe("statusBadgeClass", () => {
  it("maps Complete → success", () => {
    expect(statusBadgeClass("Complete")).toBe("vds-status-badge--success");
  });
  it("maps Failed → danger", () => {
    expect(statusBadgeClass("Failed")).toBe("vds-status-badge--danger");
  });
  it("defaults to warning for everything else", () => {
    expect(statusBadgeClass("Pending")).toBe("vds-status-badge--warning");
    expect(statusBadgeClass("Anything")).toBe("vds-status-badge--warning");
  });
});

describe("formatHistoryTime", () => {
  it("returns a two-line date and time for a parseable ISO date", () => {
    const out = formatHistoryTime("2019-05-12T08:59:49Z");
    expect(out).toContain("\n");
    expect(out).toMatch(/2019/);
  });
});

describe("formatRecordValues", () => {
  it("returns [] when records is missing or empty", () => {
    expect(formatRecordValues(undefined)).toEqual([]);
    expect(formatRecordValues({})).toEqual([]);
    expect(formatRecordValues({ records: [] })).toEqual([]);
  });

  it("extracts addresses for A / AAAA", () => {
    expect(
      formatRecordValues({
        records: [{ address: "1.1.1.1" }, { address: "2.2.2.2" }],
      }),
    ).toEqual(["1.1.1.1", "2.2.2.2"]);
  });

  it('formats MX as "<preference> <exchange>"', () => {
    expect(
      formatRecordValues({
        records: [{ preference: 10, exchange: "mail.example." }],
      }),
    ).toEqual(["10 mail.example."]);
  });

  it('formats SRV as "<priority> <weight> <port> <target>"', () => {
    expect(
      formatRecordValues({
        records: [{ priority: 1, weight: 2, port: 80, target: "srv.example." }],
      }),
    ).toEqual(["1 2 80 srv.example."]);
  });

  it("falls back to key:value join for unknown shapes", () => {
    expect(
      formatRecordValues({ records: [{ algorithm: 1, fingerprint: "abc" }] }),
    ).toEqual(["algorithm: 1, fingerprint: abc"]);
  });
});
