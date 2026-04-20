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
import type { Group } from '../../types/group';

interface GroupFormData {
  name: string;
  email: string;
  description?: string;
}

interface GroupFormProps {
  initialData?: Partial<Group>;
  onSubmit: (data: GroupFormData) => void;
  onCancel: () => void;
  isSubmitting: boolean;
  mode: 'create' | 'edit';
  validEmailDomains?: string[];
}

export function GroupForm({
  initialData,
  onSubmit,
  onCancel,
  isSubmitting,
  mode,
  validEmailDomains = [],
}: GroupFormProps) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<GroupFormData>({
    defaultValues: {
      name: initialData?.name ?? '',
      email: initialData?.email ?? '',
      description: initialData?.description ?? '',
    },
  });

  useEffect(() => {
    if (initialData) {
      reset({
        name: initialData.name ?? '',
        email: initialData.email ?? '',
        description: initialData.description ?? '',
      });
    }
  }, [initialData, reset]);

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <div className="mb-3">
        <label className="form-label fw-semibold">
          Group Name <span className="text-danger">*</span>
        </label>
        <input
          className={`form-control ${errors.name ? 'is-invalid' : ''}`}
          placeholder="Enter group name"
          {...register('name', { required: 'Group name is required' })}
          disabled={mode === 'edit'}
        />
        {errors.name && <div className="invalid-feedback">{errors.name.message}</div>}
      </div>

      <div className="mb-3">
        <label className="form-label fw-semibold">
          Email <span className="text-danger">*</span>
        </label>
        <input
          type="email"
          className={`form-control ${errors.email ? 'is-invalid' : ''}`}
          placeholder="group@example.com"
          {...register('email', { required: 'Email is required' })}
        />
        {errors.email && <div className="invalid-feedback">{errors.email.message}</div>}
        {Array.isArray(validEmailDomains) && validEmailDomains.length > 0 && (
          <div className="form-text">
            Valid domains: {validEmailDomains.join(', ')}
          </div>
        )}
      </div>

      <div className="mb-4">
        <label className="form-label fw-semibold">Description</label>
        <textarea
          className="form-control"
          rows={2}
          placeholder="Optional description"
          {...register('description')}
        />
      </div>

      <div className="d-flex gap-2">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? (
            <>
              <span className="spinner-border spinner-border-sm me-1" />
              Saving…
            </>
          ) : mode === 'create' ? (
            'Create Group'
          ) : (
            'Update Group'
          )}
        </button>
        <button type="button" className="btn btn-outline-secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
