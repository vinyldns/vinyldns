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

import { describe, it, expect } from 'vitest';
import { formatDateTime, toApiIso, stripQuotes, buildErrorMessage } from '../../utils/dateUtils';

describe('dateUtils', () => {
  it('formatDateTime returns a locale string', () => {
    const result = formatDateTime('2024-01-15T12:00:00Z');
    expect(result).toContain('2024');
  });

  it('toApiIso trims milliseconds', () => {
    const result = toApiIso('2024-01-15T12:00:00.000Z');
    expect(result).toBe('2024-01-15T12:00:00Z');
  });

  it('stripQuotes removes surrounding double quotes', () => {
    expect(stripQuotes('"hello"')).toBe('hello');
    expect(stripQuotes('hello')).toBe('hello');
  });

  it('buildErrorMessage composes HTTP error string', () => {
    const error = {
      response: {
        status: 400,
        statusText: 'Bad Request',
        data: { errors: ['Zone name is required'] },
      },
    };
    const msg = buildErrorMessage(error);
    expect(msg).toContain('HTTP 400');
    expect(msg).toContain('Zone name is required');
  });
});
