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

import React from 'react';
import { useAlerts } from '../../contexts/AlertContext';

export function AlertBanner() {
  const { alerts, removeAlert } = useAlerts();
  if (alerts.length === 0) return null;

  return (
    <div className="alert-wrapper" style={{ position: 'fixed', top: 60, right: 20, zIndex: 9999, minWidth: 320 }}>
      {alerts.map((alert) => (
        <div
          key={alert.id}
          className={`alert alert-${alert.type} alert-dismissible fade show`}
          role="alert"
        >
          <span style={{ whiteSpace: 'pre-line' }}>{alert.content}</span>
          <button
            type="button"
            className="btn-close"
            aria-label="Close"
            onClick={() => removeAlert(alert.id)}
          />
        </div>
      ))}
    </div>
  );
}
