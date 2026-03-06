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
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
} from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';

import { AlertProvider } from './contexts/AlertContext';
import { ProfileProvider } from './contexts/ProfileContext';
import { Layout } from './components/common/Layout';
import { ErrorBoundary } from './components/common/ErrorBoundary';

// Pages
import { LoginPage } from './pages/LoginPage';
import { ZonesPage } from './pages/ZonesPage';
import { ZoneDetailPage } from './pages/ZoneDetailPage';
import { GroupsPage } from './pages/GroupsPage';
import { GroupDetailPage } from './pages/GroupDetailPage';
import { RecordsPage } from './pages/RecordsPage';
import { DnsChangesPage } from './pages/DnsChangesPage';
import { DnsChangeDetailPage } from './pages/DnsChangeDetailPage';
import { DnsChangeNewPage } from './pages/DnsChangeNewPage';

// Bootstrap CSS + icons
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import './styles/vinyldns.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000, // 30 s
      retry: 1,
    },
  },
});

/**
 * Wrapper that renders pages inside the shared Layout (sidebar + alerts).
 */
function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <ProfileProvider>
      <Layout>
        <ErrorBoundary>{children}</ErrorBoundary>
      </Layout>
    </ProfileProvider>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AlertProvider>
        <BrowserRouter>
          <Routes>
            {/* Public */}
            <Route path="/login" element={<LoginPage />} />

            {/* Protected – wrapped in sidebar layout */}
            <Route
              path="/zones"
              element={
                <AppLayout>
                  <ZonesPage />
                </AppLayout>
              }
            />
            <Route
              path="/zones/:id"
              element={
                <AppLayout>
                  <ZoneDetailPage />
                </AppLayout>
              }
            />
            <Route
              path="/groups"
              element={
                <AppLayout>
                  <GroupsPage />
                </AppLayout>
              }
            />
            <Route
              path="/groups/:id"
              element={
                <AppLayout>
                  <GroupDetailPage />
                </AppLayout>
              }
            />
            <Route
              path="/recordsets"
              element={
                <AppLayout>
                  <RecordsPage />
                </AppLayout>
              }
            />
            <Route
              path="/dnschanges"
              element={
                <AppLayout>
                  <DnsChangesPage />
                </AppLayout>
              }
            />
            <Route
              path="/dnschanges/new"
              element={
                <AppLayout>
                  <DnsChangeNewPage />
                </AppLayout>
              }
            />
            <Route
              path="/dnschanges/:id"
              element={
                <AppLayout>
                  <DnsChangeDetailPage />
                </AppLayout>
              }
            />

            {/* Default redirect */}
            <Route path="/" element={<Navigate to="/zones" replace />} />
            <Route path="/index" element={<Navigate to="/zones" replace />} />
            <Route path="*" element={<Navigate to="/zones" replace />} />
          </Routes>
        </BrowserRouter>
        <ReactQueryDevtools initialIsOpen={false} />
      </AlertProvider>
    </QueryClientProvider>
  );
}
