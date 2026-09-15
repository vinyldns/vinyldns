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

import React, { useEffect } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import type { RecordSet, RecordType, RecordData } from '../../types/record';
import type { Group } from '../../types/group';

const ALL_RECORD_TYPES: RecordType[] = [
  'A', 'AAAA', 'CNAME', 'DS', 'MX', 'NAPTR', 'NS', 'PTR', 'SSHFP', 'SRV', 'TXT',
];

const SINGLE_RECORD_TYPES: RecordType[] = ['A', 'AAAA', 'CNAME', 'PTR', 'TXT'];

const emptyRecord = (type: RecordType): RecordData => {
  switch (type) {
    case 'MX':     return { preference: 10, exchange: '' };
    case 'SRV':    return { priority: 0, weight: 0, port: 0, target: '' };
    case 'SOA':    return { mname: '', rname: '', serial: 1, refresh: 28800, retry: 7200, expire: 604800, minimum: 86400 };
    case 'DS':     return { keytag: 0, algorithm: undefined as unknown as number, digesttype: undefined as unknown as number, digest: '' };
    case 'CAA':    return { flags: 0, tag: '', value: '' };
    case 'SSHFP':  return { algorithm: undefined as unknown as number, fingerprintType: undefined as unknown as number, fingerprint: '' };
    case 'NAPTR':  return { order: 0, preference: 0, flags: '', service: '', regexp: '', replacement: '' };
    default:       return {};
  }
};

interface RecordFormValues {
  name: string;
  type: RecordType;
  ttl: number;
  records: RecordData[];
  ownerGroupId?: string;
  nsText?: string;
  ptrText?: string;
}

interface RecordFormProps {
  zoneId: string;
  zoneName: string;
  initialData?: RecordSet;
  onSubmit: (data: Partial<RecordSet>) => void;
  onCancel: () => void;
  mode: 'create' | 'edit';
  isSharedZone?: boolean;
  isReverseZone?: boolean;
  isLoading?: boolean;
  allGroups?: Group[];
}

export function RecordForm({ zoneId, zoneName, initialData, onSubmit, onCancel, mode, isSharedZone = false, isReverseZone = false, isLoading = false, allGroups = [] }: RecordFormProps) {
  const allowedTypes = ALL_RECORD_TYPES;  const { register, control, handleSubmit, watch, reset, setValue,
    formState: { errors, isSubmitting } } = useForm<RecordFormValues>({
    defaultValues: {
      name: initialData?.name ?? '',
      type: initialData?.type ?? (isReverseZone ? 'PTR' : 'A'),
      ttl:  initialData?.ttl ?? 300,
      records: initialData?.records?.length ? initialData.records : [emptyRecord(initialData?.type ?? 'A')],
      ownerGroupId: initialData?.ownerGroupId ?? '',
      nsText: initialData?.type === 'NS'
        ? (initialData.records ?? []).map((r) => ((r as { nsdname: string }).nsdname ?? '')).join('\n')
        : '',
      ptrText: initialData?.type === 'PTR'
        ? (initialData.records ?? []).map((r) => ((r as { ptrdname: string }).ptrdname ?? '')).join('\n')
        : '',
    },
  });

  const selectedType = watch('type');
  const { fields, append, remove, replace, update } = useFieldArray({ control, name: 'records' });

  useEffect(() => {
    if (mode === 'create') {
      replace([emptyRecord(selectedType)]);
      setValue('nsText', '');
      setValue('ptrText', '');
    }
  }, [selectedType, mode, replace, setValue]);

  const handleFormSubmit = (values: RecordFormValues) => {
    let records: RecordData[] = values.records;
    if (values.type === 'NS') {
      records = (values.nsText ?? '')
        .split('\n').map((l) => l.trim()).filter(Boolean)
        .map((nsdname) => ({ nsdname }));
    } else if (values.type === 'PTR') {
      records = (values.ptrText ?? '')
        .split('\n').map((l) => l.trim()).filter(Boolean)
        .map((ptrdname) => ({ ptrdname }));
    }
    onSubmit({
      zoneId,
      name:       values.name,
      type:       values.type,
      ttl:        Number(values.ttl),
      records,
      ownerGroupId: values.ownerGroupId || undefined,
    });
  };

  const handleClear = () => {
    reset({
      name: '', type: 'A', ttl: 300,
      records: [emptyRecord('A')], ownerGroupId: '',
      nsText: '', ptrText: '',
    });
  };
  const isSingle = SINGLE_RECORD_TYPES.includes(selectedType);

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
      <div className="row g-3 mb-3">
        {/* Name */}
        <div className="col-md-4">
          <label className="form-label fw-semibold small">
            <i className="bi bi-fonts me-1 text-primary" />Record Name
            <span className="text-danger ms-1">*</span>
          </label>
          <div className="input-group input-group-sm">
            <input
              {...register('name', { required: 'Name is required' })}
              className={`form-control${errors.name ? ' is-invalid' : ''}`}
              placeholder={selectedType === 'PTR' ? 'e.g. 192.168.1.10 or 2001:db8::1' : 'e.g. www'}
              disabled={mode === 'edit'}
            />
            <span className="input-group-text text-muted small">.{zoneName}</span>
          </div>
          {errors.name && <div className="invalid-feedback d-block">{errors.name.message}</div>}
          {mode === 'create' && (
            <div className="form-text">Use <code>@</code> for the zone apex.</div>
          )}
        </div>

        {/* Type */}
        <div className="col-md-2">
          <label className="form-label fw-semibold small">
            <i className="bi bi-tag me-1 text-primary" />Type
            <span className="text-danger ms-1">*</span>
          </label>
          <select
            {...register('type', { required: true })}
            className="form-select form-select-sm"
            disabled={mode === 'edit'}
          >
            {allowedTypes.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>

        {/* TTL */}
        <div className="col-md-2">
          <label className="form-label fw-semibold small">
            <i className="bi bi-clock me-1 text-primary" />TTL (seconds)
            <span className="text-danger ms-1">*</span>
          </label>
          <input
            type="number"
            {...register('ttl', { required: true, min: 0 })}
            className={`form-control form-control-sm${errors.ttl ? ' is-invalid' : ''}`}
            placeholder="300"
          />
        </div>

        {/* Owner Group — only shown for shared zones */}
        {isSharedZone && (
          <div className="col-md-4">
            <label className="form-label fw-semibold small">
              <i className="bi bi-people me-1 text-primary" />Owner Group
              <span className="text-danger ms-1">*</span>
            </label>
            <select
              {...register('ownerGroupId', { required: 'Owner Group is required for shared zones' })}
              className={`form-select form-select-sm${errors.ownerGroupId ? ' is-invalid' : ''}`}
            >
              <option value="">-- Select --</option>
              {allGroups.slice().sort((a, b) => a.name.localeCompare(b.name)).map((g) => (
                <option key={g.id} value={g.id}>{g.name}</option>
              ))}
            </select>
            {errors.ownerGroupId && (
              <div className="invalid-feedback">{errors.ownerGroupId.message}</div>
            )}
          </div>
        )}

        {/* Ownership Transfer fields — read-only, shown in edit mode when present */}
        {mode === 'edit' && isSharedZone && initialData?.recordSetGroupChange?.requestedOwnerGroupId &&
          initialData.recordSetGroupChange.requestedOwnerGroupId !== 'null' && (
          <div className="col-md-4">
            <label className="form-label fw-semibold small">
              <i className="bi bi-arrow-left-right me-1 text-primary" />Ownership Transfer Group
            </label>
            <input
              type="text"
              className="form-control form-control-sm"
              readOnly
              value={
                allGroups.find(g => g.id === initialData.recordSetGroupChange!.requestedOwnerGroupId)?.name
                ?? initialData.ownerGroupName
                ?? initialData.recordSetGroupChange.requestedOwnerGroupId
              }
            />
          </div>
        )}

        {mode === 'edit' && isSharedZone && initialData?.recordSetGroupChange?.ownershipTransferStatus && (
          <div className="col-md-4">
            <label className="form-label fw-semibold small">
              <i className="bi bi-info-circle me-1 text-primary" />Ownership Transfer Status
            </label>
            <input
              type="text"
              className="form-control form-control-sm"
              readOnly
              value={initialData.recordSetGroupChange.ownershipTransferStatus}
            />
          </div>
        )}
      </div>

      {/* ── Record data rows ── */}
      <div className="vds-record-data-section mb-3">
        <div className="vds-zone-section-header mb-2">
          <div className="vds-zone-section-header__icon">
            <i className="bi bi-database" />
          </div>
          <div>
            <div className="vds-zone-section-header__title">Record Data</div>
            <div className="vds-zone-section-header__subtitle">
              {selectedType} record values for this record set
            </div>
          </div>
        </div>

        {selectedType === 'NS' ? (
          <div className="vds-record-data-row">
            <div className="flex-grow-1">
              <label className="form-label small mb-1">NS Target FQDNs (one per line)</label>
              <textarea
                {...register('nsText')}
                className="form-control form-control-sm"
                rows={4}
                placeholder={'ns1.example.com.\nns2.example.com.'}
              />
            </div>
          </div>
        ) : selectedType === 'PTR' ? (
          <div className="vds-record-data-row">
            <div className="flex-grow-1">
              <label className="form-label small mb-1">FQDN (one per line)</label>
              <textarea
                {...register('ptrText')}
                className="form-control form-control-sm"
                rows={4}
                placeholder={'host1.example.com.\nhost2.example.com.'}
              />
            </div>
          </div>
        ) : (
          <>
            <div className="d-flex flex-column gap-2">
              {fields.map((field, idx) => (
                <div key={field.id} className="vds-record-data-row">
                  {fields.length > 1 && (
                    <span className="vds-record-data-row__idx">{idx + 1}</span>
                  )}
                  <div className="d-flex align-items-start gap-2 flex-grow-1 flex-wrap">
                    <RecordDataFields
                      type={selectedType}
                      index={idx}
                      register={register}
                      errors={errors}
                    />
                  </div>
                  {!isSingle && (
                    <div className="d-flex flex-column gap-1 ms-1">
                      <button
                        type="button"
                        className="btn btn-sm vds-row-action-btn vds-row-action-btn--clear"
                        onClick={() => update(idx, emptyRecord(selectedType))}
                        title="Clear row"
                      >
                        <i className="bi bi-eraser-fill" />
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-danger vds-remove-record-btn"
                        onClick={() => fields.length > 1 ? remove(idx) : replace([emptyRecord(selectedType)])}
                        title={fields.length > 1 ? 'Remove row' : 'Clear row'}
                      >
                        <i className="bi bi-dash-circle" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
            {!isSingle && (
              <button type="button"
                className="btn btn-sm vds-add-record-btn mt-2 d-flex align-items-center gap-1"
                onClick={() => append(emptyRecord(selectedType))}>
                <i className="bi bi-plus-circle-fill" />Add Another
              </button>
            )}
          </>
        )}
      </div>

      {/* Actions */}
      <div className="vds-zone-form__actions d-flex justify-content-end gap-2 pt-2">
        {mode === 'create' && (
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={handleClear}>
            <i className="bi bi-eraser me-1" />Clear
          </button>
        )}
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onCancel}>
          <i className="bi bi-x-circle me-1" />Cancel
        </button>
        <button type="submit" className="btn btn-sm d-flex align-items-center gap-1 vds-btn-nav" disabled={isSubmitting || isLoading}>
          {isLoading
            ? <><span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true" />{mode === 'edit' ? 'Saving…' : 'Adding…'}</>
            : <><i className={`bi bi-${mode === 'edit' ? 'save' : 'plus-circle-fill'} me-1`} />{mode === 'edit' ? 'Save Changes' : 'Add Record'}</>}
        </button>
      </div>
    </form>
  );
}


function RecordDataFields({ type, index, register, errors }: {
  type: RecordType;
  index: number;
  register: ReturnType<typeof useForm<RecordFormValues>>['register'];
  errors: ReturnType<typeof useForm<RecordFormValues>>['formState']['errors'];
}) {
  const p = `records.${index}` as const;
  const err = (errors.records as Record<number, Record<string, { message?: string }>> | undefined)?.[index];
  const cls = (field: string) => `form-control form-control-sm${err?.[field] ? ' is-invalid' : ''}`;

  switch (type) {
    case 'A':
    case 'AAAA':
      return (
        <div className="flex-grow-1">
          <label className="form-label small mb-1">IP Address</label>
          <input {...register(`${p}.address` as never, { required: 'Required' })}
            className={cls('address')}
            placeholder={type === 'A' ? '192.168.1.1' : '2001:db8::1'} />
        </div>
      );

    case 'CNAME':
      return (
        <div className="flex-grow-1">
          <label className="form-label small mb-1">CNAME Target FQDN</label>
          <input {...register(`${p}.cname` as never, { required: 'Required' })}
            className={cls('cname')} placeholder="target.example.com." />
        </div>
      );

    case 'PTR':
      return (
        <div className="flex-grow-1">
          <label className="form-label small mb-1">PTR Domain Name</label>
          <input {...register(`${p}.ptrdname` as never, { required: 'Required' })}
            className={cls('ptrdname')} placeholder="host.example.com." />
        </div>
      );

    case 'NS':
      return (
        <div className="flex-grow-1">
          <label className="form-label small mb-1">Name Server</label>
          <input {...register(`${p}.nsdname` as never, { required: 'Required' })}
            className={cls('nsdname')} placeholder="ns1.example.com." />
        </div>
      );

    case 'MX':
      return (
        <>
          <div style={{ width: 100 }}>
            <label className="form-label small mb-1">Preference</label>
            <input type="number" {...register(`${p}.preference` as never, { required: true, min: 0 })}
              className={cls('preference')} placeholder="10" />
          </div>
          <div className="flex-grow-1">
            <label className="form-label small mb-1">Exchange</label>
            <input {...register(`${p}.exchange` as never, { required: 'Required' })}
              className={cls('exchange')} placeholder="mail.example.com." />
          </div>
        </>
      );

    case 'TXT':
      return (
        <div className="flex-grow-1">
          <label className="form-label small mb-1">Text</label>
          <textarea {...register(`${p}.text` as never, { required: 'Required' })}
            className={cls('text')} rows={2} placeholder="v=spf1 include:example.com ~all" />
        </div>
      );

    case 'SRV':
      return (
        <>
          <div style={{ width: 90 }}>
            <label className="form-label small mb-1">Priority</label>
            <input type="number" {...register(`${p}.priority` as never)} className={cls('priority')} placeholder="0" />
          </div>
          <div style={{ width: 90 }}>
            <label className="form-label small mb-1">Weight</label>
            <input type="number" {...register(`${p}.weight` as never)} className={cls('weight')} placeholder="0" />
          </div>
          <div style={{ width: 90 }}>
            <label className="form-label small mb-1">Port</label>
            <input type="number" {...register(`${p}.port` as never)} className={cls('port')} placeholder="443" />
          </div>
          <div className="flex-grow-1">
            <label className="form-label small mb-1">Target</label>
            <input {...register(`${p}.target` as never, { required: 'Required' })}
              className={cls('target')} placeholder="service.example.com." />
          </div>
        </>
      );

    case 'SOA':
      return (
        <div className="row g-2 flex-grow-1">
          <div className="col-md-4">
            <label className="form-label small mb-1">Primary NS (mname)</label>
            <input {...register(`${p}.mname` as never)} className={cls('mname')} placeholder="ns1.example.com." />
          </div>
          <div className="col-md-4">
            <label className="form-label small mb-1">Responsible (rname)</label>
            <input {...register(`${p}.rname` as never)} className={cls('rname')} placeholder="admin.example.com." />
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-1">Serial</label>
            <input type="number" {...register(`${p}.serial` as never)} className={cls('serial')} />
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-1">Refresh</label>
            <input type="number" {...register(`${p}.refresh` as never)} className={cls('refresh')} />
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-1">Retry</label>
            <input type="number" {...register(`${p}.retry` as never)} className={cls('retry')} />
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-1">Expire</label>
            <input type="number" {...register(`${p}.expire` as never)} className={cls('expire')} />
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-1">Minimum TTL</label>
            <input type="number" {...register(`${p}.minimum` as never)} className={cls('minimum')} />
          </div>
        </div>
      );

    case 'CAA':
      return (
        <>
          <div style={{ width: 80 }}>
            <label className="form-label small mb-1">Flags</label>
            <input type="number" {...register(`${p}.flags` as never)} className={cls('flags')} placeholder="0" />
          </div>
          <div style={{ minWidth: 120 }}>
            <label className="form-label small mb-1">Tag</label>
            <select {...register(`${p}.tag` as never)} className={`form-select form-select-sm${err?.['tag'] ? ' is-invalid' : ''}`}>
              <option value="">-- Select --</option>
              <option value="issue">issue</option>
              <option value="issuewild">issuewild</option>
              <option value="iodef">iodef</option>
            </select>
          </div>
          <div className="flex-grow-1">
            <label className="form-label small mb-1">Value</label>
            <input {...register(`${p}.value` as never)} className={cls('value')} placeholder="ca.example.com" />
          </div>
        </>
      );

    case 'SSHFP':
      return (
        <>
          <div style={{ width: 110 }}>
            <label className="form-label small mb-1">Algorithm</label>
            <select {...register(`${p}.algorithm` as never)} className="form-select form-select-sm">
              <option value="">-- Select --</option>
              <option value={1}>1 – RSA</option>
              <option value={2}>2 – DSA</option>
              <option value={3}>3 – ECDSA</option>
              <option value={4}>4 – Ed25519</option>
            </select>
          </div>
          <div style={{ width: 130 }}>
            <label className="form-label small mb-1">FP Type</label>
            <select {...register(`${p}.fingerprintType` as never)} className="form-select form-select-sm">
              <option value="">-- Select --</option>
              <option value={1}>1 – SHA-1</option>
              <option value={2}>2 – SHA-256</option>
            </select>
          </div>
          <div className="flex-grow-1">
            <label className="form-label small mb-1">Fingerprint</label>
            <input {...register(`${p}.fingerprint` as never)} className={cls('fingerprint')} placeholder="abc123..." />
          </div>
        </>
      );

    case 'DS':
      return (
        <>
          <div style={{ width: 90 }}>
            <label className="form-label small mb-1">Key Tag</label>
            <input type="number" {...register(`${p}.keytag` as never)} className={cls('keytag')} />
          </div>
          <div style={{ width: 210 }}>
            <label className="form-label small mb-1">Algorithm</label>
            <select {...register(`${p}.algorithm` as never)} className="form-select form-select-sm">
              <option value="">-- Select --</option>
              <option value={3}>(3) DSA</option>
              <option value={5}>(5) RSASHA1</option>
              <option value={6}>(6) DSA_NSEC3_SHA1</option>
              <option value={7}>(7) RSASHA1_NSEC3_SHA1</option>
              <option value={8}>(8) RSASHA256</option>
              <option value={10}>(10) RSASHA512</option>
              <option value={12}>(12) ECC_GOST</option>
              <option value={13}>(13) ECDSAP256SHA256</option>
              <option value={14}>(14) ECDSAP384SHA384</option>
              <option value={15}>(15) ED25519</option>
              <option value={16}>(16) ED448</option>
              <option value={253}>(253) PRIVATEDNS</option>
              <option value={254}>(254) PRIVATEOID</option>
            </select>
          </div>
          <div style={{ width: 180 }}>
            <label className="form-label small mb-1">Digest Type</label>
            <select {...register(`${p}.digesttype` as never)} className="form-select form-select-sm">
              <option value="">-- Select --</option>
              <option value={1}>(1) SHA1</option>
              <option value={2}>(2) SHA256</option>
              <option value={3}>(3) GOSTR341194</option>
              <option value={4}>(4) SHA384</option>
            </select>
          </div>
          <div className="flex-grow-1">
            <label className="form-label small mb-1">Digest</label>
            <input {...register(`${p}.digest` as never)} className={cls('digest')} placeholder="hex digest" />
          </div>
        </>
      );

    case 'NAPTR':
      return (
        <div className="row g-2 flex-grow-1">
          <div className="col-2">
            <label className="form-label small mb-1">Order</label>
            <input type="number" {...register(`${p}.order` as never)} className={cls('order')} />
          </div>
          <div className="col-2">
            <label className="form-label small mb-1">Preference</label>
            <input type="number" {...register(`${p}.preference` as never)} className={cls('preference')} />
          </div>
          <div className="col-2">
            <label className="form-label small mb-1">Flags</label>
            <select {...register(`${p}.flags` as never)} className={`form-select form-select-sm${err?.['flags'] ? ' is-invalid' : ''}`}>
              <option value="">-- Select --</option>
              <option value="u">U</option>
              <option value="s">S</option>
              <option value="a">A</option>
              <option value="p">P</option>
            </select>
          </div>
          <div className="col-3">
            <label className="form-label small mb-1">Service</label>
            <input {...register(`${p}.service` as never)} className={cls('service')} placeholder="E2U+sip" />
          </div>
          <div className="col-3">
            <label className="form-label small mb-1">Regexp</label>
            <input {...register(`${p}.regexp` as never)} className={cls('regexp')} />
          </div>
          <div className="col-6">
            <label className="form-label small mb-1">Replacement</label>
            <input {...register(`${p}.replacement` as never)} className={cls('replacement')} />
          </div>
        </div>
      );

    default:
      return <div className="text-muted small">Record data fields for {type} are not yet supported.</div>;
  }
}
