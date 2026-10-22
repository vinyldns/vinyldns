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

import React, { useEffect, useState } from 'react';
import { GroupCombobox } from './GroupCombobox';
import { useForm } from 'react-hook-form';
import { useQuery } from '@tanstack/react-query';
import { groupsService } from '../../services/groupsService';
import type { Zone } from '../../types/zone';
import type { Group } from '../../types/group';

const KEY_ALGORITHMS = [
  'HMAC-MD5',
  'HMAC-SHA1',
  'HMAC-SHA224',
  'HMAC-SHA256',
  'HMAC-SHA384',
  'HMAC-SHA512',
];

interface ZoneFormProps {
  initialData?: Partial<Zone>;
  groups: Group[];
  backendIds?: string[];
  onSubmit: (data: Zone) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  mode: 'create' | 'edit';
}

// ── Small field label + help text row ─────────────────────────────────────────
function FieldHelp({ text }: { text: string }) {
  return <p className="vds-zone-field-help">{text}</p>;
}

// ── Section divider ───────────────────────────────────────────────────────────
function SectionHeader({ icon, title, subtitle }: { icon: string; title: string; subtitle: string }) {
  return (
    <div className="vds-zone-section-header">
      <div className="vds-zone-section-header__icon">
        <i className={`bi ${icon}`} />
      </div>
      <div>
        <div className="vds-zone-section-header__title">{title}</div>
        <div className="vds-zone-section-header__subtitle">{subtitle}</div>
      </div>
    </div>
  );
}

export function ZoneForm({
  initialData,
  groups,
  backendIds = [],
  onSubmit,
  onCancel,
  isSubmitting,
  mode,
}: ZoneFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    control,
    formState: { errors },
  } = useForm<Zone>({ defaultValues: initialData ?? {} });

  useEffect(() => {
    if (initialData) reset(initialData);
  }, [initialData, reset]);

  // ── Valid email domains accordion ─────────────────────────────────────────
  const [showEmailDomains, setShowEmailDomains] = useState(false);
  const { data: validEmailDomains } = useQuery({
    queryKey: ['email-domains'],
    queryFn: async () => {
      const res = await groupsService.listEmailDomains();
      return Array.isArray(res.data) ? (res.data as string[]) : [];
    },
  });

  // ── Connection section open/close ─────────────────────────────────────────
  const [connOpen, setConnOpen]     = useState(
    !!(initialData?.connection?.keyName || initialData?.connection?.primaryServer)
  );
  const [transferOpen, setTransferOpen] = useState(
    !!(initialData?.transferConnection?.keyName || initialData?.transferConnection?.primaryServer)
  );

  // ── Clear connection helpers ──────────────────────────────────────────────
  const clearConnection = () => {
    setValue('connection.keyName', '');
    setValue('connection.key', '');
    setValue('connection.algorithm', '');
    setValue('connection.primaryServer', '');
  };
  const clearTransferConnection = () => {
    setValue('transferConnection.keyName', '');
    setValue('transferConnection.key', '');
    setValue('transferConnection.algorithm', '');
    setValue('transferConnection.primaryServer', '');
  };

  const connKeyName   = watch('connection.keyName');
  const connServer    = watch('connection.primaryServer');
  const transKeyName  = watch('transferConnection.keyName');
  const transServer   = watch('transferConnection.primaryServer');
  const connDirty     = !!(connKeyName || connServer);
  const transDirty    = !!(transKeyName || transServer);
  const adminGroupId  = watch('adminGroupId');

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="vds-zone-form">

      {/* ── Intro banner (create only) ── */}
      {mode === 'create' && (
        <div className="vds-zone-form__intro">
          <i className="bi bi-info-circle-fill vds-zone-form__intro-icon" />
          <span>
            Use this form to connect to an already existing DNS zone.
            If you need a new zone, please contact DNS Ops. Be sure to inform them you are
            managing your zone through VinylDNS.
          </span>
        </div>
      )}

      {/* ════════════════════════════════════════════════
          SECTION 1 — Basic Zone Info
          ════════════════════════════════════════════════ */}
      <SectionHeader
        icon="bi-diagram-3-fill"
        title="Zone Information"
        subtitle="Required fields to identify the zone"
      />

      <div className="row g-3 mb-3">
        {/* Zone Name */}
        <div className="col-md-6">
          <label className="vds-zone-form__label">
            Zone Name <span className="text-danger">*</span>
          </label>
          <input
            className={`form-control vds-zone-form__input ${errors.name ? 'is-invalid' : ''}`}
            placeholder="e.g. vinyldns.example.net."
            {...register('name', { required: 'Zone name is required' })}
            disabled={mode === 'edit'}
          />
          {errors.name
            ? <div className="invalid-feedback">{errors.name.message}</div>
            : <FieldHelp text='Name for the DNS zone (e.g. "vinyldns.example.net."). Include the trailing dot.' />
          }
        </div>

        {/* Email */}
        <div className="col-md-6">
          <label className="vds-zone-form__label">
            Email <span className="text-danger">*</span>
          </label>
          <input
            type="text"
            className={`form-control vds-zone-form__input ${errors.email ? 'is-invalid' : ''}`}
            placeholder="zone-admin@example.com"
            {...register('email', { required: 'Email is required' })}
          />
          {errors.email
            ? <div className="invalid-feedback">{errors.email.message}</div>
            : <FieldHelp text="The email distribution list for the zone — typically the org that owns the zone." />
          }
          {/* Valid domains accordion */}
          {validEmailDomains && validEmailDomains.length > 0 && (
            <div className="mt-1">
              <button
                type="button"
                className="vds-zone-form__domains-toggle"
                onClick={() => setShowEmailDomains((v) => !v)}
              >
                <i className={`bi bi-chevron-${showEmailDomains ? 'up' : 'down'} me-1`} />
                Valid Email Domains
              </button>
              {showEmailDomains && (
                <div className="vds-zone-form__domains-list">
                  {validEmailDomains.map((d) => (
                    <span key={d} className="vds-zone-form__domain-chip">{d}</span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="row g-3 mb-4" style={{gap: '6.5rem'}}>
        {/* Admin Group */}
        <div className="col-md-5">
          <label className="vds-zone-form__label">
            Admin Group <span className="text-danger">*</span>
          </label>
          <input type="hidden" {...register('adminGroupId', { required: 'Admin group is required' })} />
          <GroupCombobox
            groups={groups}
            value={adminGroupId ?? ''}
            onChange={(id) => setValue('adminGroupId', id, { shouldValidate: true })}
            invalid={!!errors.adminGroupId}
            errorMessage={errors.adminGroupId?.message}
          />
          {!errors.adminGroupId && (
            <FieldHelp text="The VinylDNS group given admin rights to this zone. All users in this group can manage all records." />
          )}
        </div>

        {/* Backend ID */}
        <div className="col-md-4">
          <label className="vds-zone-form__label">Backend ID (Optional)</label>
          <select className="form-select vds-zone-form__input" {...register('backendId')} defaultValue="default">
            {backendIds.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
          <FieldHelp text="Pre-configured DNS connection ID. Leave default unless advised by your DNS admin team." />
        </div>
      </div>

      {/* ════════════════════════════════════════════════
          SECTION 2 — DNS Server Connection
          ════════════════════════════════════════════════ */}
      <div className="vds-zone-collapsible mb-3">
        <button
          type="button"
          className={`vds-zone-collapsible__trigger${connOpen ? ' vds-zone-collapsible__trigger--open' : ''}`}
          onClick={() => setConnOpen((v) => !v)}
          aria-expanded={connOpen}
        >
          <div className="d-flex align-items-center gap-2">
            <div className="vds-zone-collapsible__icon-wrap">
              <i className="bi bi-server" />
            </div>
            <div>
              <div className="vds-zone-collapsible__title">DNS Server Connection</div>
              <div className="vds-zone-collapsible__subtitle">
                {connDirty ? 'Connection configured' : 'Optional — leave blank to use default'}
              </div>
            </div>
          </div>
          <div className="d-flex align-items-center gap-2">
            {connDirty && <span className="vds-zone-collapsible__badge">Configured</span>}
            <i className={`bi bi-chevron-${connOpen ? 'up' : 'down'} vds-zone-collapsible__chevron`} />
          </div>
        </button>

        {connOpen && (
          <div className="vds-zone-collapsible__body">
            <div className="vds-zone-collapsible__help-banner">
              <i className="bi bi-info-circle me-2" />
              If none of the following options are filled, default connection settings are used.
            </div>
            <div className="row g-3 mb-2">
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Name</label>
                <input
                  className="form-control vds-zone-form__input"
                  placeholder="e.g. vinyldns."
                  {...register('connection.keyName')}
                />
                <FieldHelp text="The name of the key used to sign requests to the DNS server. This is set when the zone is setup in the DNS server, and is used to connect to the DNS server when performing updates and transfers." />
              </div>
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Secret</label>
                <input
                  type="password"
                  className="form-control vds-zone-form__input"
                  placeholder={mode === 'edit' ? 'Current key hidden — enter to change' : ''}
                  {...register('connection.key')}
                />
                <FieldHelp text="The secret key used to sign requests sent to the DNS server." />
              </div>
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Algorithm</label>
                <select
                  className="form-select vds-zone-form__input"
                  {...register('connection.algorithm')}
                >
                  <option value="">— Select —</option>
                  {KEY_ALGORITHMS.map((a) => (
                    <option key={a} value={a}>{a}</option>
                  ))}
                </select>
                <FieldHelp text="The TSIG algorithm that matches the secret key above." />
              </div>
            </div>
            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="vds-zone-form__label">DNS Server</label>
                <input
                  className="form-control vds-zone-form__input"
                  placeholder="e.g. bind-01.sys.example.net:5300"
                  {...register('connection.primaryServer')}
                />
                <FieldHelp text="The IP Address or host name of the backing DNS server. This host will be the target for DNS updates. If the port is not specified, port 53 is assumed. If the port to connect to is different than 53, then include the port when specifying the DNS Server. For example: bind-01.sys.example.net:5300 would be used to connect to server bind-01.sys.example.net on port 5300." />
              </div>
            </div>
            <div className="d-flex justify-content-end">
              <button
                type="button"
                className="btn btn-sm vds-zone-form__clear-btn"
                disabled={!connDirty}
                onClick={clearConnection}
              >
                <i className="bi bi-x-circle me-1" />Clear Connection
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ════════════════════════════════════════════════
          SECTION 3 — Zone Transfer Server
          ════════════════════════════════════════════════ */}
      <div className="vds-zone-collapsible mb-4">
        <button
          type="button"
          className={`vds-zone-collapsible__trigger${transferOpen ? ' vds-zone-collapsible__trigger--open' : ''}`}
          onClick={() => setTransferOpen((v) => !v)}
          aria-expanded={transferOpen}
        >
          <div className="d-flex align-items-center gap-2">
            <div className="vds-zone-collapsible__icon-wrap vds-zone-collapsible__icon-wrap--transfer">
              <i className="bi bi-arrow-left-right" />
            </div>
            <div>
              <div className="vds-zone-collapsible__title">Zone Transfer Server</div>
              <div className="vds-zone-collapsible__subtitle">
                {transDirty ? 'Transfer connection configured' : 'Optional — used for zone sync/transfer'}
              </div>
            </div>
          </div>
          <div className="d-flex align-items-center gap-2">
            {transDirty && <span className="vds-zone-collapsible__badge vds-zone-collapsible__badge--transfer">Configured</span>}
            <i className={`bi bi-chevron-${transferOpen ? 'up' : 'down'} vds-zone-collapsible__chevron`} />
          </div>
        </button>

        {transferOpen && (
          <div className="vds-zone-collapsible__body">
            <div className="vds-zone-collapsible__help-banner">
              <i className="bi bi-info-circle me-2" />
              If no transfer connection is provided, the primary DNS server connection will be used for zone transfers.
            </div>
            <div className="row g-3 mb-2">
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Name</label>
                <input
                  className="form-control vds-zone-form__input"
                  placeholder="e.g. vinyldns-transfer."
                  {...register('transferConnection.keyName')}
                />
                <FieldHelp text="The name of the key used to sign requests to the DNS server. This is set when the zone is setup in the DNS server, and is used to connect to the DNS server when performing updates and transfers." />
              </div>
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Secret</label>
                <input
                  type="password"
                  className="form-control vds-zone-form__input"
                  placeholder={mode === 'edit' ? 'Current key hidden — enter to change' : ''}
                  {...register('transferConnection.key')}
                />
                <FieldHelp text="The secret key used to sign requests sent to the transfer server." />
              </div>
              <div className="col-md-4">
                <label className="vds-zone-form__label">Key Algorithm</label>
                <select
                  className="form-select vds-zone-form__input"
                  {...register('transferConnection.algorithm')}
                >
                  <option value="">— Select —</option>
                  {KEY_ALGORITHMS.map((a) => (
                    <option key={a} value={a}>{a}</option>
                  ))}
                </select>
                <FieldHelp text="The TSIG algorithm that matches the transfer key above." />
              </div>
            </div>
            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="vds-zone-form__label">DNS Transfer Server</label>
                <input
                  className="form-control vds-zone-form__input"
                  placeholder="e.g. bind-01.sys.example.net:5300"
                  {...register('transferConnection.primaryServer')}
                />
                <FieldHelp text="The IP Address or host name of the backing DNS server for zone transfers. This host will be the target for syncing zones with Vinyl. If the port is not specified, port 53 is assumed. If the port to connect to is different than 53, then include the port when specifying the DNS Server. For example: bind-01.sys.example.net:5300 would be used to connect to server bind-01.sys.example.net on port 5300." />
              </div>
            </div>
            <div className="d-flex justify-content-end">
              <button
                type="button"
                className="btn btn-sm vds-zone-form__clear-btn"
                disabled={!transDirty}
                onClick={clearTransferConnection}
              >
                <i className="bi bi-x-circle me-1" />Clear Transfer Connection
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Actions ── */}
      <div className="vds-zone-form__actions d-flex justify-content-between align-items-center w-100 pt-2">
        <button
          type="button"
          className="vds-modal__btn vds-modal__btn--utility"
          onClick={() => {
            reset(initialData ? { ...initialData } : {
              name: '', email: '', adminGroupId: '', backendId: 'default',
              connection: { keyName: '', key: '', algorithm: '', primaryServer: '' },
              transferConnection: { keyName: '', key: '', algorithm: '', primaryServer: '' },
            });
            setValue('adminGroupId', initialData?.adminGroupId ?? '');
            setConnOpen(false);
            setTransferOpen(false);
          }}
        >
          <i className="bi bi-arrow-counterclockwise me-1" />Clear
        </button>
        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="vds-modal__btn vds-modal__btn--secondary"
            onClick={onCancel}
          >
            <i className="bi bi-x-circle me-1" />
            Cancel
          </button>
          <button
            type="submit"
            className="vds-modal__btn vds-modal__btn--primary d-flex align-items-center gap-1"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <span className="spinner-border spinner-border-sm vds-spinner-xs" />
                Saving…
              </>
            ) : mode === 'create' ? (
              <><i className="bi bi-plug-fill" />Connect Zone</>
            ) : (
              <><i className="bi bi-check-circle-fill" />Update Zone</>
            )}
          </button>
        </div>
      </div>
    </form>
  );
}
