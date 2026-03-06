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

import React, { type ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useProfile } from '../../contexts/ProfileContext';
import { profileService } from '../../services/profileService';
import { useAlerts } from '../../contexts/AlertContext';
import { AlertBanner } from './AlertBanner';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const { profile } = useProfile();
  const { addAlert } = useAlerts();
  const navigate = useNavigate();

  const handleRegenerateCredentials = async () => {
    try {
      await profileService.regenerateCredentials();
      addAlert('success', 'Credentials regenerated successfully');
      setTimeout(() => window.location.reload(), 2000);
    } catch {
      addAlert('danger', 'Failed to regenerate credentials');
    }
  };

  return (
    <div className="container-fluid p-0" style={{ minHeight: '100vh' }}>
      <AlertBanner />

      {/* Sidebar + content wrapper */}
      <div className="d-flex" style={{ minHeight: '100vh' }}>
        {/* ===== SIDEBAR ===== */}
        <nav
          className="d-flex flex-column bg-dark text-white"
          style={{ width: 240, minWidth: 240, minHeight: '100vh', padding: '1rem 0' }}
        >
          {/* Logo */}
          <div className="px-3 mb-4">
            <NavLink to="/index">
              <img
                src="/assets/images/vinyldns-portal.png"
                alt="VinylDNS"
                style={{ maxWidth: '100%', height: 'auto' }}
                onError={(e) => {
                  (e.target as HTMLImageElement).style.display = 'none';
                }}
              />
            </NavLink>
            <div className="text-white fw-bold mt-1" style={{ fontSize: '1.1rem' }}>
              VinylDNS
            </div>
          </div>

          {/* Nav items */}
          <ul className="nav flex-column px-2">
            <li className="nav-item">
              <NavLink
                to="/dnschanges"
                className={({ isActive }) =>
                  `nav-link text-white-50 px-3 py-2 rounded ${isActive ? 'bg-secondary text-white' : ''}`
                }
              >
                <i className="bi bi-list-ol me-2" />
                DNS Changes
              </NavLink>
            </li>
            <li className="nav-item">
              <NavLink
                to="/recordsets"
                className={({ isActive }) =>
                  `nav-link text-white-50 px-3 py-2 rounded ${isActive ? 'bg-secondary text-white' : ''}`
                }
              >
                <i className="bi bi-search me-2" />
                RecordSet Search
              </NavLink>
            </li>
            <li className="nav-item">
              <NavLink
                to="/groups"
                className={({ isActive }) =>
                  `nav-link text-white-50 px-3 py-2 rounded ${isActive ? 'bg-secondary text-white' : ''}`
                }
              >
                <i className="bi bi-people me-2" />
                Groups
              </NavLink>
            </li>
            <li className="nav-item">
              <NavLink
                to="/zones"
                className={({ isActive }) =>
                  `nav-link text-white-50 px-3 py-2 rounded ${isActive ? 'bg-secondary text-white' : ''}`
                }
              >
                <i className="bi bi-table me-2" />
                Zones
              </NavLink>
            </li>
          </ul>

          {/* Spacer */}
          <div className="mt-auto px-3 pb-2">
            {profile && (
              <div className="dropdown">
                <button
                  className="btn btn-sm btn-outline-light dropdown-toggle w-100"
                  data-bs-toggle="dropdown"
                >
                  <i className="bi bi-person-circle me-1" />
                  {profile.userName}
                </button>
                <ul className="dropdown-menu dropdown-menu-dark">
                  <li>
                    <button
                      className="dropdown-item"
                      onClick={handleRegenerateCredentials}
                    >
                      Regenerate Credentials
                    </button>
                  </li>
                  <li>
                    <a
                      className="dropdown-item"
                      href="/logout"
                      onClick={() => navigate('/login')}
                    >
                      Logout
                    </a>
                  </li>
                </ul>
              </div>
            )}
          </div>
        </nav>

        {/* ===== MAIN CONTENT ===== */}
        <main className="flex-grow-1 p-4 bg-light">
          {children}
        </main>
      </div>
    </div>
  );
}
