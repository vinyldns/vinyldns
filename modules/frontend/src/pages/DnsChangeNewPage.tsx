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
import { Link, useNavigate } from 'react-router-dom';
import { DnsChangeForm } from '../components/dnsChanges/DnsChangeForm';
import { useDnsChanges } from '../hooks/useDnsChanges';
import { useAlerts } from '../contexts/AlertContext';
import type { CreateDnsChangeRequest } from '../types/dnsChange';

export function DnsChangeNewPage() {
  const navigate = useNavigate();
  const { createBatchChange, isSubmitting } = useDnsChanges();
  const { addAlert } = useAlerts();
  const [serverRowErrors, setServerRowErrors] = useState<string[][]>([]);

  const handleSubmit = (data: CreateDnsChangeRequest, allowManualReview: boolean) => {
    setServerRowErrors([]);
    createBatchChange(
      { data, allowManualReview },
      {
        onSuccess: () => void navigate('/dnschanges'),
        onError: (err: unknown) => {
          const error = err as { response?: { status?: number; data?: unknown } };
          const status = error.response?.status;
          const responseData = error.response?.data;

          // 400 with an array: per-row validation errors (mirrors portal behavior)
          if (status === 400 && Array.isArray(responseData)) {
            const perRow = (responseData as Array<{ errors?: string[] }>).map(
              (change) => change.errors ?? []
            );
            setServerRowErrors(perRow);
            const hasErrors = perRow.some((e) => e.length > 0);
            if (hasErrors) {
              addAlert('danger', 'Errors found in one or more rows. Please correct and resubmit.');
            }
          }
        },
      }
    );
  };

  return (
    <div>
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/dnschanges">DNS Changes</Link>
          </li>
          <li className="breadcrumb-item active">New Batch Change</li>
        </ol>
      </nav>

      <h1 className="h3 fw-bold mb-4">New Batch Change</h1>

      <div className="card shadow-sm">
        <div className="card-body">
          <DnsChangeForm
            onSubmit={handleSubmit}
            onCancel={() => navigate('/dnschanges')}
            isSubmitting={isSubmitting}
            serverRowErrors={serverRowErrors}
          />
        </div>
      </div>
    </div>
  );
}

