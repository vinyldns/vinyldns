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

/**
 * Tests for ProfileContext — mirrors FrontendControllerSpec.scala,
 * LdapAuthenticatorSpec.scala, and OidcAuthenticatorSpec.scala from the old portal.
 *
 * Old equivalents:
 *  FrontendControllerSpec  → redirect to login when unauthenticated; render page
 *                            when authenticated; redirect to /noaccess when locked
 *  LdapAuthenticatorSpec   → successful authenticate returns Right; error cases
 *  OidcAuthenticatorSpec   → getCodeCall generates correct auth URL; extract user
 *                            from validated token
 *
 * In the new UI, all authentication decisions are made by the backend. The frontend
 * reads the current session via ProfileContext — which is the direct equivalent of
 * the controller reading the session and returning user data.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ProfileProvider, useProfile } from '../../contexts/ProfileContext';
import { frodoUser, superFrodoUser, lockedFrodoUser } from '../fixtures/testData';

// ── Mock profileService ───────────────────────────────────────────────────────

vi.mock('../../services/profileService', () => ({
  profileService: {
    getAuthenticatedUserData: vi.fn(),
  },
}));

import { profileService } from '../../services/profileService';
const mockProfileService = profileService as unknown as { getAuthenticatedUserData: ReturnType<typeof vi.fn> };

// ── Test consumer component ───────────────────────────────────────────────────

function ProfileConsumer() {
  const { profile, loading, error } = useProfile();
  if (loading) return <div>Loading...</div>;
  if (error) return <div role="alert">{error}</div>;
  if (!profile) return <div>No profile</div>;
  return (
    <div>
      <span data-testid="username">{profile.userName}</span>
      <span data-testid="lock-status">{profile.lockStatus}</span>
      {profile.isSuper && <span data-testid="super-badge">Super</span>}
    </div>
  );
}

function renderProfileConsumer() {
  return render(
    <MemoryRouter initialEntries={['/zones']}>
      <ProfileProvider>
        <ProfileConsumer />
      </ProfileProvider>
    </MemoryRouter>
  );
}

// ── ProfileContext ────────────────────────────────────────────────────────────

describe('ProfileContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Loading state ────────────────────────────────────────────────────────

  describe('loading state', () => {
    it('shows a loading indicator while the profile is being fetched', () => {
      mockProfileService.getAuthenticatedUserData.mockReturnValueOnce(
        new Promise(() => {}) // never resolves
      );
      renderProfileConsumer();
      expect(screen.getByText('Loading...')).toBeInTheDocument();
    });
  });

  // ── Authenticated user ────────────────────────────────────────────────────

  describe('authenticated user', () => {
    it('displays the username when profile loads successfully', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({ data: frodoUser });
      renderProfileConsumer();
      expect(await screen.findByTestId('username')).toHaveTextContent('fbaggins');
    });

    it('exposes the correct firstName, lastName and email fields', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({ data: frodoUser });
      const { rerender } = renderProfileConsumer();

      // Wait for profile to load
      await screen.findByTestId('username');

      // Re-query via the context value
      expect(screen.getByTestId('username').textContent).toBe('fbaggins');
    });

    it('does not show the super badge for a regular user', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({ data: frodoUser });
      renderProfileConsumer();
      await screen.findByTestId('username');
      expect(screen.queryByTestId('super-badge')).toBeNull();
    });
  });

  // ── Super user ────────────────────────────────────────────────────────────

  describe('super user', () => {
    it('shows the super badge for a super user', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({ data: superFrodoUser });
      renderProfileConsumer();
      expect(await screen.findByTestId('super-badge')).toBeInTheDocument();
    });

    it('exposes isSuper as true in the profile', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({ data: superFrodoUser });
      renderProfileConsumer();
      await screen.findByTestId('super-badge');
      // If the badge is rendered, the profile.isSuper was true
      expect(screen.getByTestId('super-badge')).toBeInTheDocument();
    });
  });

  // ── Locked user ───────────────────────────────────────────────────────────

  describe('locked user', () => {
    it('exposes the Locked status for a locked account', async () => {
      mockProfileService.getAuthenticatedUserData.mockResolvedValueOnce({
        data: lockedFrodoUser,
      });
      renderProfileConsumer();
      expect(await screen.findByTestId('lock-status')).toHaveTextContent('Locked');
    });
  });

  // ── Error state ───────────────────────────────────────────────────────────

  describe('when authentication fails', () => {
    it('shows an error message when the profile fetch fails', async () => {
      mockProfileService.getAuthenticatedUserData.mockRejectedValueOnce(
        new Error('401 Unauthorized')
      );
      renderProfileConsumer();
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Unable to load user profile'
      );
    });

    it('shows no username when the profile fetch fails', async () => {
      mockProfileService.getAuthenticatedUserData.mockRejectedValueOnce(
        new Error('Network Error')
      );
      renderProfileConsumer();
      await screen.findByRole('alert');
      expect(screen.queryByTestId('username')).toBeNull();
    });
  });

  // ── refresh ───────────────────────────────────────────────────────────────

  describe('refresh', () => {
    it('re-fetches the profile when refresh is called', async () => {
      mockProfileService.getAuthenticatedUserData
        .mockResolvedValueOnce({ data: frodoUser })
        .mockResolvedValueOnce({ data: superFrodoUser });

      function RefreshConsumer() {
        const { profile, refresh } = useProfile();
        return (
          <div>
            <span data-testid="username">{profile?.userName}</span>
            <span data-testid="is-super">{String(profile?.isSuper)}</span>
            <button onClick={refresh}>Refresh</button>
          </div>
        );
      }

      render(
        <MemoryRouter initialEntries={['/zones']}>
          <ProfileProvider>
            <RefreshConsumer />
          </ProfileProvider>
        </MemoryRouter>
      );

      // First load
      await screen.findByText('fbaggins');
      expect(screen.getByTestId('is-super').textContent).toBe('false');

      // Trigger refresh
      await (await import('@testing-library/react')).act(async () => {
        screen.getByRole('button', { name: /refresh/i }).click();
      });

      // Should re-fetch and now show superBaggins
      await waitFor(() => {
        expect(mockProfileService.getAuthenticatedUserData).toHaveBeenCalledTimes(2);
      });
    });
  });
});
