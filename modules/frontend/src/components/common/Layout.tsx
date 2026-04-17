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

import React, { useState, useRef, useEffect, type ReactNode } from 'react';
import { NavLink, Link, useLocation } from 'react-router-dom';
import { useBreadcrumbs, type Crumb } from '../../contexts/BreadcrumbContext';
import { useProfile } from '../../contexts/ProfileContext';
import { useAlerts } from '../../contexts/AlertContext';
import { AlertBanner } from './AlertBanner';

const ROUTE_LABELS: Record<string, string> = {
  '/dnschanges':  'DNS Changes',
  '/recordsets':  'RecordSet Search',
  '/groups':      'Groups',
  '/zones':       'Zones',
  '/admin':       'Control Panel',
};

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const { profile } = useProfile();
  const { addAlert } = useAlerts();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [regenModalOpen, setRegenModalOpen] = useState(false);
  const [darkMode, setDarkMode] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  // Apply theme attribute to root element
  useEffect(() => {
    document.documentElement.setAttribute('data-vds-theme', darkMode ? 'dark' : 'light');
  }, [darkMode]);

  // Derive breadcrumb trail — pages can override via BreadcrumbContext
  const { crumbs } = useBreadcrumbs();
  const pathRoot = '/' + location.pathname.split('/')[1];
  const fallbackLabel = ROUTE_LABELS[pathRoot];
  const trail: Crumb[] = crumbs ?? (fallbackLabel ? [{ label: fallbackLabel }] : []);

  // Close popover on outside click
  useEffect(() => {
    if (!userMenuOpen) return;
    const handler = (e: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [userMenuOpen]);

  const handleRegenerateCredentials = () => {
    setUserMenuOpen(false);
    setRegenModalOpen(true);
  };

  const confirmRegenerateCredentials = async () => {
    setRegenModalOpen(false);
    try {
      await fetch('/regenerate-creds', { method: 'POST' });
      addAlert('success', 'Credentials regenerated successfully');
    } catch {
      addAlert('danger', 'Failed to regenerate credentials');
    }
  };

  const handleLogout = async () => {
    try {
      await fetch('/logout', { method: 'POST' });
    } finally {
      window.location.href = '/login';
    }
  };

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `nav-link vds-nav-link d-flex align-items-center px-3 py-2 rounded-2${isActive ? ' vds-nav-link--active' : ''}`;

  return (
    <div className="vds-layout">
      <AlertBanner />

      <div className="vds-layout__body">
        {/* ===== SIDEBAR ===== */}
        <nav className={`vds-sidebar d-flex flex-column text-white${collapsed ? ' vds-sidebar--collapsed' : ''}`}>
          {/* ---- Logo + toggle ---- */}
          <div className="vds-sidebar__header">
            {/* Logo row */}
            <div className="vds-logo-row">
              {!collapsed ? (
                <NavLink to="/index" className="vds-logo-link">
                  <img
                    src="/img/sidebar_brand.png"
                    alt="VinylDNS icon"
                    className="vds-logo-icon"
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                  />
                  <span className="vds-logo-wordmark">VINYLDNS</span>
                </NavLink>
              ) : (
                <NavLink to="/index" className="vds-logo-link vds-logo-link--sm">
                  <img
                    src="/img/sidebar_brand.png"
                    alt="VinylDNS"
                    className="vds-logo-icon"
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                  />
                </NavLink>
              )}
            </div>

            {/* Toggle button */}
            <button
              className="vds-toggle-btn"
              onClick={() => setCollapsed(!collapsed)}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              <span className="vds-toggle-inner">
                <i className={`bi ${collapsed ? 'bi-chevron-double-right' : 'bi-chevron-double-left'}`} />
                {!collapsed && <span className="vds-toggle-label">Collapse</span>}
              </span>
            </button>
          </div>

          {/* ---- Scrollable nav area ---- */}
          <div className="flex-grow-1 py-2 vds-sidebar__scroll">

            {/* ── MAIN section ── */}
            {!collapsed && (
              <div className="vds-section-label px-3 pt-3 pb-1">Main</div>
            )}
            {collapsed && <div className="vds-spacer-main" />}

            <ul className="nav flex-column px-2 mb-2 vds-nav-list">
              <li className="nav-item">
                <NavLink
                  to="/dnschanges"
                  className={({ isActive }) =>
                    `${navLinkClass({ isActive })}${collapsed ? ' vds-tip' : ''}`
                  }
                  data-vds-tip="DNS Changes"
                >
                  <i className="bi bi-list-ol vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">DNS Changes</span>}
                </NavLink>
              </li>
              <li className="nav-item">
                <NavLink
                  to="/recordsets"
                  className={({ isActive }) =>
                    `${navLinkClass({ isActive })}${collapsed ? ' vds-tip' : ''}`
                  }
                  data-vds-tip="RecordSet Search"
                >
                  <i className="bi bi-search vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">RecordSet Search</span>}
                </NavLink>
              </li>
              <li className="nav-item">
                <NavLink
                  to="/groups"
                  className={({ isActive }) =>
                    `${navLinkClass({ isActive })}${collapsed ? ' vds-tip' : ''}`
                  }
                  data-vds-tip="Groups"
                >
                  <i className="bi bi-people-fill vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Groups</span>}
                </NavLink>
              </li>
              <li className="nav-item">
                <NavLink
                  to="/zones"
                  className={({ isActive }) =>
                    `${navLinkClass({ isActive })}${collapsed ? ' vds-tip' : ''}`
                  }
                  data-vds-tip="Zones"
                >
                  <i className="bi bi-globe2 vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Zones</span>}
                </NavLink>
              </li>
            </ul>

            {/* Divider */}
            <hr className="vds-divider mx-3 my-0" />

            {/* ── ADMIN section ── */}
            {!collapsed && (
              <div className="vds-section-label px-3 pt-3 pb-1">Admin</div>
            )}
            {collapsed && <div className="vds-spacer-help" />}

            <ul className="nav flex-column px-2 mb-2 vds-nav-list">
              <li className="nav-item">
                <NavLink
                  to="/admin"
                  className={({ isActive }) =>
                    `${navLinkClass({ isActive })}${collapsed ? ' vds-tip' : ''}`
                  }
                  data-vds-tip="Control Panel"
                >
                  <i className="bi bi-shield-shaded vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Control Panel</span>}
                </NavLink>
              </li>
            </ul>

            {/* Divider */}
            <hr className="vds-divider mx-3 my-0" />
            {!collapsed && (
              <div className="vds-section-label px-3 pt-3 pb-1">Help</div>
            )}
            {collapsed && <div className="vds-spacer-help" />}

            <ul className="nav flex-column px-2 vds-nav-list">
              <li className="nav-item">
                <a
                  href="https://comcast.github.io/vinyldns/user-guide/2_getting-started-user-guide/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className={`nav-link vds-nav-link d-flex align-items-center px-3 py-2 rounded-2${collapsed ? ' vds-tip' : ''}`}
                  data-vds-tip="Comcast User Guide"
                >
                  <i className="bi bi-book-half vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Comcast User Guide</span>}
                </a>
              </li>
              <li className="nav-item">
                <a
                  href="https://comcast.github.io/vinyldns/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className={`nav-link vds-nav-link d-flex align-items-center px-3 py-2 rounded-2${collapsed ? ' vds-tip' : ''}`}
                  data-vds-tip="Portal Guide"
                >
                  <i className="bi bi-file-earmark-text vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Portal Guide</span>}
                </a>
              </li>
              <li className="nav-item">
                <a
                  href="https://github.com/vinyldns/vinyldns/issues"
                  target="_blank"
                  rel="noopener noreferrer"
                  className={`nav-link vds-nav-link d-flex align-items-center px-3 py-2 rounded-2${collapsed ? ' vds-tip' : ''}`}
                  data-vds-tip="Support"
                >
                  <i className="bi bi-life-preserver vds-nav-icon" />
                  {!collapsed && <span className="vds-nav-text">Support</span>}
                </a>
              </li>
            </ul>
          </div>

          {/* ---- User profile ---- */}
          <div className="vds-sidebar__footer px-2 py-3">
            {profile && (
              <div ref={userMenuRef} className="vds-user-menu">
                <button
                  className={`btn vds-user-btn w-100 d-flex align-items-center gap-2${collapsed ? ' vds-tip vds-user-btn--collapsed' : ''}`}
                  onClick={() => setUserMenuOpen(o => !o)}
                  data-vds-tip={profile.userName}
                  aria-expanded={userMenuOpen}
                >
                  <i className="bi bi-person-circle vds-user-btn__icon" />
                  {!collapsed && (
                    <span className="text-truncate vds-user-btn__name">
                      {profile.userName}
                    </span>
                  )}
                  {!collapsed && (
                    <i className={`bi bi-chevron-${userMenuOpen ? 'down' : 'up'} ms-auto vds-user-btn__chevron`} />
                  )}
                </button>

                {userMenuOpen && (
                  <div className={`vds-user-popover${collapsed ? ' vds-user-popover--right' : ''}`}>
                    {/* User header */}
                    <div className="vds-user-popover__header">
                      <div className="vds-user-popover__avatar">
                        <i className="bi bi-person-fill" />
                      </div>
                      <div>
                        <div className="vds-user-popover__name">{profile.userName}</div>
                        <div className="vds-user-popover__status">Signed in</div>
                      </div>
                    </div>

                    {/* Actions */}
                    <div className="vds-user-popover__actions">
                      <a
                        href={`/download-creds-file/${profile.userName}-vinyldns-credentials.csv`}
                        onClick={() => setUserMenuOpen(false)}
                        className="vds-user-popover__item"
                      >
                        <i className="bi bi-download vds-user-popover__item-icon" />
                        <span>Download API Credentials</span>
                      </a>
                      <button
                        onClick={handleRegenerateCredentials}
                        className="vds-user-popover__item vds-user-popover__item--btn"
                      >
                        <i className="bi bi-arrow-repeat vds-user-popover__item-icon" />
                        <span>Regenerate Credentials</span>
                      </button>
                    </div>

                    {/* Logout */}
                    <div className="vds-user-popover__logout-section">
                      <button
                        onClick={handleLogout}
                        className="vds-user-popover__item vds-user-popover__item--btn vds-user-popover__item--danger"
                      >
                        <i className="bi bi-box-arrow-right vds-user-popover__item-icon" />
                        <span>Logout</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </nav>

        {/* ===== MAIN CONTENT ===== */}
        <main className="vds-main">
          {/* ── Top bar ── */}
          <div className="vds-topbar">
            {/* Left: breadcrumb — same style as original */}
            <nav aria-label="breadcrumb" className="mb-0">
              <ol className="breadcrumb mb-0 vds-breadcrumb">
                <li className="breadcrumb-item">
                  <a href="/" className="text-decoration-none vds-topbar__home-link">
                    <i className="bi bi-house-door me-1" />Home
                  </a>
                </li>
                {trail.map((crumb, i) =>
                  i < trail.length - 1 ? (
                    <li key={crumb.label} className="breadcrumb-item">
                      <Link to={crumb.to!} className="text-decoration-none vds-topbar__crumb-link">
                        {crumb.label}
                      </Link>
                    </li>
                  ) : (
                    <li key={crumb.label} className="breadcrumb-item active vds-topbar__page-label" aria-current="page">
                      {crumb.label}
                    </li>
                  )
                )}
              </ol>
            </nav>

            {/* Right: theme toggle */}
            <button
              className="vds-theme-toggle"
              onClick={() => setDarkMode(d => !d)}
              title={darkMode ? 'Switch to Light mode' : 'Switch to Dark mode'}
              aria-label="Toggle theme"
            >
              <span className={`vds-theme-toggle__track${darkMode ? ' vds-theme-toggle__track--dark' : ''}`}>
                <span className="vds-theme-toggle__thumb">
                  <i className={`bi ${darkMode ? 'bi-moon-stars-fill' : 'bi-sun-fill'}`} />
                </span>
              </span>
              <span className="vds-theme-toggle__label">{darkMode ? 'Dark' : 'Light'}</span>
            </button>
          </div>

          {children}
        </main>
      </div>

      {/* ===== REGENERATE CREDENTIALS MODAL ===== */}
      {regenModalOpen && (
        <div
          className="vds-modal-backdrop"
          onMouseDown={e => { if (e.target === e.currentTarget) setRegenModalOpen(false); }}
        >
          <div className="vds-modal">
            {/* Header */}
            <div className="vds-modal__header">
              <div className="vds-modal__icon vds-modal__icon--warning">
                <i className="bi bi-arrow-repeat" />
              </div>
              <span className="vds-modal__title">Regenerate Credentials?</span>
              <button
                onClick={() => setRegenModalOpen(false)}
                className="vds-modal__close"
                aria-label="Close"
              >
                <i className="bi bi-x-lg" />
              </button>
            </div>

            {/* Body */}
            <div className="vds-modal__body">
              <p className="vds-modal__text">
                If you regenerate your credentials you will receive new credentials and your existing
                credentials will be invalidated. If you use any VinylDNS tools beyond this portal you
                will need to provide those tools with your new credentials.
              </p>
              <p className="vds-modal__text vds-modal__text--muted">
                Are you sure you want to regenerate your credentials?
              </p>
            </div>

            {/* Footer */}
            <div className="vds-modal__footer">
              <button
                onClick={() => setRegenModalOpen(false)}
                className="vds-modal__btn vds-modal__btn--secondary"
              >
                No, Cancel
              </button>
              <button
                onClick={confirmRegenerateCredentials}
                className="vds-modal__btn vds-modal__btn--primary"
              >
                Yes, Regenerate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
