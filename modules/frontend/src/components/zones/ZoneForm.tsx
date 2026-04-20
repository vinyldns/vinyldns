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
import { useForm } from 'react-hook-form';
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

export function ZoneForm({
  initialData,
  groups,
  backendIds = [],
  onSubmit,
  onCancel,
  isSubmitting,
  mode,
}: ZoneFormProps) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<Zone>({
    defaultValues: initialData ?? {},
  });

  useEffect(() => {
    if (initialData) reset(initialData);
  }, [initialData, reset]);

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {/* Basic info */}
      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label fw-semibold">
            Zone Name <span className="text-danger">*</span>
          </label>
          <input
            className={`form-control ${errors.name ? 'is-invalid' : ''}`}
            placeholder="e.g. example.com."
            {...register('name', { required: 'Zone name is required' })}
            disabled={mode === 'edit'}
          />
          {errors.name && <div className="invalid-feedback">{errors.name.message}</div>}
        </div>
        <div className="col-md-6">
          <label className="form-label fw-semibold">
            Email <span className="text-danger">*</span>
          </label>
          <input
            type="email"
            className={`form-control ${errors.email ? 'is-invalid' : ''}`}
            placeholder="zone-admin@example.com"
            {...register('email', { required: 'Email is required' })}
          />
          {errors.email && <div className="invalid-feedback">{errors.email.message}</div>}
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label fw-semibold">
            Admin Group <span className="text-danger">*</span>
          </label>
          <select
            className={`form-select ${errors.adminGroupId ? 'is-invalid' : ''}`}
            {...register('adminGroupId', { required: 'Admin group is required' })}
          >
            <option value="">— Select a group —</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>{g.name}</option>
            ))}
          </select>
          {errors.adminGroupId && (
            <div className="invalid-feedback">{errors.adminGroupId.message}</div>
          )}
        </div>
        <div className="col-md-3">
          <label className="form-label fw-semibold">Backend ID</label>
          <select className="form-select" {...register('backendId')}>
            <option value="">— Default —</option>
            {backendIds.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
        </div>
        <div className="col-md-3 d-flex align-items-end pb-1">
          <div className="form-check">
            <input
              type="checkbox"
              id="sharedCheck"
              className="form-check-input"
              {...register('shared')}
            />
            <label htmlFor="sharedCheck" className="form-check-label">Shared Zone</label>
          </div>
        </div>
      </div>

      {/* Connection */}
      <details className="mb-3">
        <summary className="fw-semibold text-secondary cursor-pointer mb-2">
          Zone Connection (optional)
        </summary>
        <div className="row g-3 mt-1 border rounded p-3 bg-white">
          <div className="col-md-4">
            <label className="form-label">Key Name</label>
            <input className="form-control" {...register('connection.keyName')} />
          </div>
          <div className="col-md-4">
            <label className="form-label">Key</label>
            <input type="password" className="form-control" {...register('connection.key')} />
          </div>
          <div className="col-md-4">
            <label className="form-label">Algorithm</label>
            <select className="form-select" defaultValue="">
              {KEY_ALGORITHMS.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </select>
          </div>
          <div className="col-md-6">
            <label className="form-label">Primary Server</label>
            <input className="form-control" {...register('connection.primaryServer')} />
          </div>
        </div>
      </details>

      {/* Transfer connection */}
      <details className="mb-4">
        <summary className="fw-semibold text-secondary cursor-pointer mb-2">
          Transfer Connection (optional)
        </summary>
        <div className="row g-3 mt-1 border rounded p-3 bg-white">
          <div className="col-md-4">
            <label className="form-label">Key Name</label>
            <input className="form-control" {...register('transferConnection.keyName')} />
          </div>
          <div className="col-md-4">
            <label className="form-label">Key</label>
            <input type="password" className="form-control" {...register('transferConnection.key')} />
          </div>
          <div className="col-md-6">
            <label className="form-label">Primary Server</label>
            <input className="form-control" {...register('transferConnection.primaryServer')} />
          </div>
        </div>
      </details>

      <div className="d-flex gap-2">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? (
            <>
              <span className="spinner-border spinner-border-sm me-1" />
              Saving…
            </>
          ) : mode === 'create' ? (
            'Create Zone'
          ) : (
            'Update Zone'
          )}
        </button>
        <button type="button" className="btn btn-outline-secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
