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
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from '../../pages/LoginPage';

// ── Mock fetch ────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();

beforeEach(() => {
  mockFetch.mockReset();
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockImplementation(async (input) => {
    if (input === '/api/authmode') {
      return { json: async () => ({ mode: 'ldap' }) };
    }
    throw new Error(`Unhandled fetch call in test: ${String(input)}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderLoginPage() {
  return render(<LoginPage />);
}

// ── LoginPage ─────────────────────────────────────────────────────────────────

describe('LoginPage', () => {
  describe('rendering', () => {
    it('renders the VinylDNS welcome heading', () => {
      renderLoginPage();
      expect(screen.getByText(/welcome to vinyldns/i)).toBeInTheDocument();
    });

    it('renders a username input field', () => {
      renderLoginPage();
      expect(screen.getByPlaceholderText(/enter username/i)).toBeInTheDocument();
    });

    it('renders a password input field', () => {
      renderLoginPage();
      expect(screen.getByPlaceholderText(/enter password/i)).toBeInTheDocument();
    });

    it('renders the Log In submit button', () => {
      renderLoginPage();
      expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
    });

    it('does not display an error alert on initial render', () => {
      renderLoginPage();
      expect(screen.queryByRole('alert')).toBeNull();
    });
  });

  describe('form validation', () => {
    it('shows a validation error when username is submitted empty', async () => {
      renderLoginPage();

      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(await screen.findByText(/username is required/i)).toBeInTheDocument();
    });

    it('shows a validation error when password is submitted empty', async () => {
      renderLoginPage();

      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(await screen.findByText(/password is required/i)).toBeInTheDocument();
    });
  });

  describe('successful login', () => {
    it('redirects to / when credentials are accepted', async () => {
      mockFetch.mockImplementation(async (input) => {
        if (input === '/api/authmode') {
          return { json: async () => ({ mode: 'ldap' }) };
        }
        if (input === '/login') {
          return { json: async () => ({ ok: true }) };
        }
        throw new Error(`Unhandled fetch call in test: ${String(input)}`);
      });
      // jsdom does not navigate, so spy on window.location.href assignment
      const locationSpy = vi.spyOn(window, 'location', 'get').mockReturnValue(
        { ...window.location, href: '/' } as Location
      );

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'correct-password');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalledWith(
          '/login',
          expect.objectContaining({ method: 'POST' })
        );
      });

      locationSpy.mockRestore();
    });

    it('sends the username and password in the request body', async () => {
      mockFetch.mockImplementation(async (input) => {
        if (input === '/api/authmode') {
          return { json: async () => ({ mode: 'ldap' }) };
        }
        if (input === '/login') {
          return { json: async () => ({ ok: true }) };
        }
        throw new Error(`Unhandled fetch call in test: ${String(input)}`);
      });

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'theOneRing');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      await waitFor(() => {
        const loginCall = mockFetch.mock.calls.find(([url]) => url === '/login');
        expect(loginCall).toBeTruthy();
        const [, options] = loginCall as [string, RequestInit];
        const body = JSON.parse((options as RequestInit).body as string);
        expect(body.username).toBe('fbaggins');
        expect(body.password).toBe('theOneRing');
      });
    });
  });

  describe('failed login', () => {
    it('displays the error message returned from the server', async () => {
      mockFetch.mockImplementation(async (input) => {
        if (input === '/api/authmode') {
          return { json: async () => ({ mode: 'ldap' }) };
        }
        if (input === '/login') {
          return {
            json: async () => ({ ok: false, error: 'Authentication failed, please try again' }),
          };
        }
        throw new Error(`Unhandled fetch call in test: ${String(input)}`);
      });

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'wrong-password');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(
        await screen.findByText(/authentication failed, please try again/i)
      ).toBeInTheDocument();
    });

    it('displays a fallback error when the server returns no error message', async () => {
      mockFetch.mockImplementation(async (input) => {
        if (input === '/api/authmode') {
          return { json: async () => ({ mode: 'ldap' }) };
        }
        if (input === '/login') {
          return {
            json: async () => ({ ok: false }),
          };
        }
        throw new Error(`Unhandled fetch call in test: ${String(input)}`);
      });

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'wrong-password');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(
        await screen.findByText(/invalid credentials/i)
      ).toBeInTheDocument();
    });

    it('displays a connection error when fetch rejects', async () => {
      mockFetch.mockImplementation(async (input) => {
        if (input === '/api/authmode') {
          return { json: async () => ({ mode: 'ldap' }) };
        }
        if (input === '/login') {
          throw new Error('Network Error');
        }
        throw new Error(`Unhandled fetch call in test: ${String(input)}`);
      });

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'some-password');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(
        await screen.findByText(/unable to connect/i)
      ).toBeInTheDocument();
    });
  });

  describe('loading state', () => {
    it('disables the submit button while login is in progress', async () => {
      // /login never resolves — keeps the component in loading state
      mockFetch.mockImplementation((input) => {
        if (input === '/api/authmode') {
          return Promise.resolve({ json: async () => ({ mode: 'ldap' }) });
        }
        if (input === '/login') {
          return new Promise(() => {});
        }
        return Promise.reject(new Error(`Unhandled fetch call in test: ${String(input)}`));
      });

      renderLoginPage();
      await userEvent.type(screen.getByPlaceholderText(/enter username/i), 'fbaggins');
      await userEvent.type(screen.getByPlaceholderText(/enter password/i), 'correct-password');
      await userEvent.click(screen.getByRole('button', { name: /log in/i }));

      expect(await screen.findByRole('button', { name: /signing in/i })).toBeDisabled();
    });
  });
});
