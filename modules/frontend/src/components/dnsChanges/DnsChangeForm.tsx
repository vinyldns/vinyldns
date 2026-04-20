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

import React, { useState } from 'react';
import {
  useForm,
  useFieldArray,
  useWatch,
  useFormContext,
  FormProvider,
} from 'react-hook-form';
import type { CreateDnsChangeRequest, SingleChange } from '../../types/dnsChange';

// ── Types ─────────────────────────────────────────────────────────────────────

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
  'id' | 'status' | 'recordName' | 'zoneName' | 'zoneId' | 'recordSetId' | 'errors' | 'systemMessage'
> & { record?: RecordData };

interface DnsChangeFormData {
  comments: string;
  ownerGroupId: string;
  changes: ChangeFormItem[];
}

// ── Record data field component ───────────────────────────────────────────────

const NAPTR_FLAGS = ['U', 'S', 'A', 'P'] as const;

function RecordDataFields({ index, recordType, isAdd }: { index: number; recordType: string; isAdd: boolean }) {
  const { register } = useFormContext<DnsChangeFormData>();
  const req = isAdd;

  const helpText = !isAdd && (
    <div className="form-text text-muted fst-italic">Record data is optional for delete.</div>
  );

  switch (recordType) {
    case 'A':
    case 'A+PTR':
      return (
        <div>
          <input
            className="form-control form-control-sm"
            placeholder="e.g. 1.1.1.1"
            {...register(`changes.${index}.record.address`, { required: req })}
          />
          {helpText}
        </div>
      );
    case 'AAAA':
    case 'AAAA+PTR':
      return (
        <div>
          <input
            className="form-control form-control-sm"
            placeholder="e.g. fd69:27cc:fe91::60"
            {...register(`changes.${index}.record.address`, { required: req })}
          />
          {helpText}
        </div>
      );
    case 'CNAME':
      return (
        <div>
          <input
            className="form-control form-control-sm"
            placeholder="e.g. target.example.com."
            disabled={!isAdd}
            {...register(`changes.${index}.record.cname`, { required: req })}
          />
        </div>
      );
    case 'PTR':
      return (
        <div>
          <input
            className="form-control form-control-sm"
            placeholder="e.g. test.example.com."
            {...register(`changes.${index}.record.ptrdname`, { required: req })}
          />
          {helpText}
        </div>
      );
    case 'TXT':
      return (
        <div>
          <textarea
            className="form-control form-control-sm"
            rows={2}
            placeholder="e.g. attr=val"
            {...register(`changes.${index}.record.text`, { required: req })}
          />
          {helpText}
        </div>
      );
    case 'MX':
      return (
        <div className="d-flex gap-2 flex-wrap">
          <div style={{ minWidth: 110 }}>
            <label className="form-label small mb-1">Preference</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="e.g. 1"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.preference`, { required: req, valueAsNumber: true, min: 0, max: 65535 })}
            />
          </div>
          <div style={{ minWidth: 200 }}>
            <label className="form-label small mb-1">Exchange</label>
            <input
              className="form-control form-control-sm"
              placeholder="e.g. mail.example.com."
              {...register(`changes.${index}.record.exchange`, { required: req })}
            />
          </div>
          {helpText && <div className="w-100 mb-0">{helpText}</div>}
        </div>
      );
    case 'NS':
      return (
        <div>
          <input
            className="form-control form-control-sm"
            placeholder="e.g. ns1.example.com."
            {...register(`changes.${index}.record.nsdname`, { required: req })}
          />
          {helpText}
        </div>
      );
    case 'SRV':
      return (
        <div className="d-flex gap-2 flex-wrap">
          <div style={{ minWidth: 85 }}>
            <label className="form-label small mb-1">Priority</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="0"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.priority`, { required: req, valueAsNumber: true })}
            />
          </div>
          <div style={{ minWidth: 85 }}>
            <label className="form-label small mb-1">Weight</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="0"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.weight`, { required: req, valueAsNumber: true })}
            />
          </div>
          <div style={{ minWidth: 85 }}>
            <label className="form-label small mb-1">Port</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="8080"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.port`, { required: req, valueAsNumber: true })}
            />
          </div>
          <div style={{ minWidth: 180 }}>
            <label className="form-label small mb-1">Target</label>
            <input
              className="form-control form-control-sm"
              placeholder="e.g. target.example.com."
              {...register(`changes.${index}.record.target`, { required: req })}
            />
          </div>
          {helpText && <div className="w-100 mb-0">{helpText}</div>}
        </div>
      );
    case 'NAPTR':
      return (
        <div className="d-flex gap-2 flex-wrap">
          <div style={{ minWidth: 85 }}>
            <label className="form-label small mb-1">Order</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="1"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.order`, { required: req, valueAsNumber: true })}
            />
          </div>
          <div style={{ minWidth: 85 }}>
            <label className="form-label small mb-1">Preference</label>
            <input
              type="number"
              className="form-control form-control-sm"
              placeholder="1"
              min={0}
              max={65535}
              {...register(`changes.${index}.record.preference`, { required: req, valueAsNumber: true })}
            />
          </div>
          <div style={{ minWidth: 80 }}>
            <label className="form-label small mb-1">Flags</label>
            <select
              className="form-select form-select-sm"
              {...register(`changes.${index}.record.flags`, { required: req })}
            >
              <option value="">--</option>
              {NAPTR_FLAGS.map((f) => (
                <option key={f} value={f}>{f}</option>
              ))}
            </select>
          </div>
          <div style={{ minWidth: 130 }}>
            <label className="form-label small mb-1">Service</label>
            <input
              className="form-control form-control-sm"
              placeholder="e.g. SIP+D2U"
              {...register(`changes.${index}.record.service`, { required: req })}
            />
          </div>
          <div style={{ minWidth: 130 }}>
            <label className="form-label small mb-1">Regexp</label>
            <input
              className="form-control form-control-sm"
              placeholder="optional"
              {...register(`changes.${index}.record.regexp`)}
            />
          </div>
          <div style={{ minWidth: 130 }}>
            <label className="form-label small mb-1">Replacement</label>
            <input
              className="form-control form-control-sm"
              placeholder="e.g. ."
              {...register(`changes.${index}.record.replacement`, { required: req })}
            />
          </div>
          {helpText && <div className="w-100 mb-0">{helpText}</div>}
        </div>
      );
    default:
      return <span className="text-muted small fst-italic">—</span>;
  }
}

// ── Single change row ─────────────────────────────────────────────────────────

const RECORD_TYPES = ['A+PTR', 'AAAA+PTR', 'A', 'AAAA', 'CNAME', 'PTR', 'TXT', 'MX', 'NS', 'SRV', 'NAPTR'] as const;

function ChangeRow({
  index,
  remove,
  serverErrors,
}: {
  index: number;
  remove: (i: number) => void;
  serverErrors?: string[];
}) {
  const { register, control, setValue } = useFormContext<DnsChangeFormData>();
  const changeType = useWatch({ control, name: `changes.${index}.changeType` });
  const recordType = useWatch({ control, name: `changes.${index}.type` });

  const isAdd = changeType === 'Add';
  const isPtr = recordType === 'PTR';

  // Spread register result but intercept onChange to clear record data on type switch
  const { onChange: onTypeChange, ...restTypeRegister } = register(`changes.${index}.type`);

  return (
    <div className={`border rounded p-2 mb-2 ${serverErrors && serverErrors.length > 0 ? 'border-danger bg-danger-subtle' : 'bg-white'}`}>
      {/* Row 1 — core fields */}
      <div className="row g-2 align-items-end">
        <div className="col-sm-2">
          <label className="form-label small fw-semibold mb-1">Change Type</label>
          <select className="form-select form-select-sm" {...register(`changes.${index}.changeType`)}>
            <option value="Add">Add</option>
            <option value="DeleteRecordSet">DeleteRecordSet</option>
          </select>
        </div>
        <div className="col-sm-2">
          <label className="form-label small fw-semibold mb-1">Record Type</label>
          <select
            className="form-select form-select-sm"
            {...restTypeRegister}
            onChange={(e) => {
              // Clear stale record data when type changes (mirrors portal clearRecordData)
              setValue(`changes.${index}.record`, {});
              void onTypeChange(e);
            }}
          >
            {RECORD_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="col-sm-3">
          <label className="form-label small fw-semibold mb-1">
            Input Name{isPtr ? ' (IP address)' : ' (FQDN)'}
          </label>
          <input
            className="form-control form-control-sm"
            placeholder={isPtr ? 'e.g. 192.0.2.193' : 'e.g. host.example.com.'}
            {...register(`changes.${index}.inputName`, { required: true })}
          />
        </div>
        <div className="col-sm-2">
          <label className="form-label small fw-semibold mb-1">TTL</label>
          <input
            type="number"
            className="form-control form-control-sm"
            placeholder="300"
            disabled={!isAdd}
            min={30}
            max={2147483647}
            {...register(`changes.${index}.ttl`, { valueAsNumber: true })}
          />
          {!isAdd && <div className="form-text text-muted fst-italic" style={{ fontSize: '0.7rem' }}>N/A for delete</div>}
        </div>
        <div className="col-sm-2 d-flex justify-content-end align-items-end">
          <button
            type="button"
            className="btn btn-sm btn-outline-danger"
            onClick={() => remove(index)}
            title="Remove row"
          >
            <i className="bi bi-x-lg" />
          </button>
        </div>
      </div>

      {/* Row 2 — record data */}
      <div className="row g-2 mt-1">
        <div className="col-12">
          <label className="form-label small fw-semibold mb-1 text-secondary">
            Record Data
            {isAdd ? '' : ' (optional)'}
          </label>
          <RecordDataFields index={index} recordType={recordType} isAdd={isAdd} />
        </div>
      </div>

      {/* Server-side per-row errors */}
      {serverErrors && serverErrors.length > 0 && (
        <div className="mt-1">
          {serverErrors.map((e, i) => (
            <div key={i} className="text-danger small">
              <i className="bi bi-exclamation-circle me-1" />
              {e}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main form ─────────────────────────────────────────────────────────────────

interface DnsChangeFormProps {
  onSubmit: (data: CreateDnsChangeRequest, allowManualReview: boolean) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  /** Per-row server errors returned by a 400 API response */
  serverRowErrors?: string[][];
}

export function DnsChangeForm({ onSubmit, onCancel, isSubmitting, serverRowErrors }: DnsChangeFormProps) {
  const [allowManualReview, setAllowManualReview] = useState(false);
  const [rowErrors, setRowErrors] = useState<string[][]>([]);

  // Merge local + server errors
  const effectiveRowErrors = serverRowErrors ?? rowErrors;

  const methods = useForm<DnsChangeFormData>({
    defaultValues: {
      comments: '',
      ownerGroupId: '',
      changes: [{ changeType: 'Add', inputName: '', type: 'A+PTR', ttl: undefined, record: {} }],
    },
  });

  const { register, control, handleSubmit } = methods;
  const { fields, append, remove } = useFieldArray({ control, name: 'changes' });

  const handleFormSubmit = (data: DnsChangeFormData) => {
    setRowErrors([]);

    // Expand A+PTR / AAAA+PTR into paired entries (mirrors portal formatData)
    const expandedChanges: ChangeFormItem[] = [];
    for (const entry of data.changes) {
      if (entry.type === 'A+PTR' || entry.type === 'AAAA+PTR') {
        const baseType = entry.type === 'A+PTR' ? 'A' : 'AAAA';
        expandedChanges.push({ ...entry, type: baseType });
        expandedChanges.push({
          changeType: entry.changeType,
          type: 'PTR',
          ttl: entry.ttl,
          inputName: (entry.record as RecordData)?.address ?? '',
          record: { ptrdname: entry.inputName },
        });
      } else if (entry.type === 'NAPTR') {
        // Ensure regexp is always a string (portal default '')
        const r = entry.record as RecordData;
        expandedChanges.push({
          ...entry,
          record: { ...r, regexp: r?.regexp ?? '' },
        });
      } else {
        expandedChanges.push(entry);
      }
    }

    // For DeleteRecordSet: drop record field if all values are empty
    const finalChanges = expandedChanges.map((entry) => {
      if (entry.changeType === 'DeleteRecordSet' && entry.record) {
        const allEmpty = Object.values(entry.record).every(
          (v) => v === undefined || v === null || (typeof v === 'string' && v.trim() === '')
        );
        if (allEmpty) {
          const { record: _r, ...rest } = entry;
          return rest as ChangeFormItem;
        }
      }
      return entry;
    });

    onSubmit(
      {
        comments: data.comments || undefined,
        ownerGroupId: data.ownerGroupId || undefined,
        changes: finalChanges,
      },
      allowManualReview,
    );
  };

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        {/* Top-level metadata */}
        <div className="row g-3 mb-4">
          <div className="col-md-6">
            <label className="form-label fw-semibold">Description / Comments</label>
            <textarea
              className="form-control"
              rows={2}
              placeholder="Optional description for this batch change"
              {...register('comments')}
            />
          </div>
          <div className="col-md-4">
            <label className="form-label fw-semibold">Owner Group ID</label>
            <input
              className="form-control"
              placeholder="Optional — required for shared zone records"
              {...register('ownerGroupId')}
            />
          </div>
          <div className="col-md-2 d-flex align-items-end pb-1">
            <div className="form-check">
              <input
                type="checkbox"
                id="allowManualReview"
                className="form-check-input"
                checked={allowManualReview}
                onChange={(e) => setAllowManualReview(e.target.checked)}
              />
              <label htmlFor="allowManualReview" className="form-check-label small">
                Allow Manual Review
              </label>
            </div>
          </div>
        </div>

        {/* Changes */}
        <div className="mb-3">
          <div className="d-flex justify-content-between align-items-center mb-2">
            <h6 className="fw-semibold mb-0">Changes</h6>
            <button
              type="button"
              className="btn btn-sm btn-outline-success"
              onClick={() =>
                append({ changeType: 'Add', inputName: '', type: 'A+PTR', ttl: undefined, record: {} })
              }
            >
              <i className="bi bi-plus-circle me-1" />
              Add Row
            </button>
          </div>

          {fields.length === 0 && (
            <div className="alert alert-warning py-2">Add at least one change.</div>
          )}

          {fields.map((field, index) => (
            <ChangeRow
              key={field.id}
              index={index}
              remove={remove}
              serverErrors={effectiveRowErrors[index]}
            />
          ))}
        </div>

        <div className="d-flex gap-2">
          <button
            type="submit"
            className="btn btn-primary"
            disabled={isSubmitting || fields.length === 0}
          >
            {isSubmitting ? (
              <>
                <span className="spinner-border spinner-border-sm me-1" />
                Submitting…
              </>
            ) : (
              'Submit Batch Change'
            )}
          </button>
          <button type="button" className="btn btn-outline-secondary" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>
    </FormProvider>
  );
}
