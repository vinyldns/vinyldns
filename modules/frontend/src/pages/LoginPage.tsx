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
import { useForm } from 'react-hook-form';
import { portalConfig } from '../config/portalConfig';

interface LoginFormData {
  username: string;
  password: string;
}

export function LoginPage() {
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>();

  const onSubmit = async (data: LoginFormData) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          username: data.username,
          password: data.password,
        }),
      });

      if (res.ok) {
        window.location.href = '/';
      } else {
        const body = await res.json().catch(() => ({}));
        setError((body as { error?: string }).error ?? 'Invalid credentials. Please try again.');
      }
    } catch {
      setError('Unable to connect. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="d-flex justify-content-center align-items-center"
      style={{ minHeight: '100vh', background: 'url(/img/jumbotron_pattern.png) repeat center' }}
    >
      <div className="card shadow-lg" style={{ width: '100%', maxWidth: 440 }}>
        <div className="card-body p-5">
          {/* Logo & welcome */}
          <div className="text-center mb-4">
            <img
              src="/img/sidebar_brand2x.png"
              alt="VinylDNS"
              style={{ maxWidth: 200 }}
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
            <h4 className="mt-3 fw-bold text-dark">Welcome to VinylDNS</h4>
            <p className="text-muted small mb-0">
              DNS automation &amp; governance for streamlining DNS operations
              and secure DNS self-service.
            </p>
          </div>

          {error && (
            <div className="alert alert-danger py-2" role="alert">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="mb-3">
              <label className="form-label fw-semibold">Username</label>
              <input
                type="text"
                autoComplete="username"
                className={`form-control ${errors.username ? 'is-invalid' : ''}`}
                placeholder="Enter username"
                {...register('username', { required: 'Username is required' })}
              />
              {errors.username && (
                <div className="invalid-feedback">{errors.username.message}</div>
              )}
            </div>

            <div className="mb-4">
              <label className="form-label fw-semibold">Password</label>
              <input
                type="password"
                autoComplete="current-password"
                className={`form-control ${errors.password ? 'is-invalid' : ''}`}
                placeholder="Enter password"
                {...register('password', { required: 'Password is required' })}
              />
              {errors.password && (
                <div className="invalid-feedback">{errors.password.message}</div>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary w-100"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-1" />
                  Signing in…
                </>
              ) : (
                'Log In'
              )}
            </button>
          </form>
        </div>

        {/* Footer — version, docs, support */}
        <div
          className="card-footer bg-light px-5 py-3 d-flex justify-content-between align-items-start flex-wrap gap-2"
          style={{ fontSize: '0.8rem' }}
        >
          <span className="text-muted">VinylDNS&nbsp;v{portalConfig.version}</span>
          <ul className="list-unstyled mb-0 d-flex gap-3">
            {portalConfig.loginLinks.map((link) => (
              <li key={link.href}>
                <a
                  href={link.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-decoration-none"
                >
                  {link.title}
                </a>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
