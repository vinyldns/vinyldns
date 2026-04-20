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

/** Format a timestamp string for display (mirrors Angular utilityService.formatDateTime) */
export function formatDateTime(timeStamp: string): string {
  return new Date(timeStamp).toLocaleString('en-us', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  });
}

/** Convert a JS date string back to the VinylDNS API ISO format (no ms) */
export function toApiIso(date: string): string {
  return new Date(date).toISOString().slice(0, 19) + 'Z';
}

/** Copy a string to the clipboard */
export async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text);
  } else {
    const input = document.createElement('input');
    input.style.position = 'absolute';
    input.style.left = '-9999px';
    input.value = text;
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    document.body.removeChild(input);
  }
}

/** Strip leading/trailing double-quotes from a JSON string value */
export function stripQuotes(str: string): string {
  if (str.startsWith('"') && str.endsWith('"')) {
    return str.slice(1, -1);
  }
  return str;
}

/** Build an axios-friendly error message matching the existing portal format */
export function buildErrorMessage(error: {
  response?: {
    status?: number;
    statusText?: string;
    data?: string | { errors?: string[] };
  };
}): string {
  const status = error.response?.status ?? 0;
  const statusText = error.response?.statusText ?? 'Unknown';
  const data = error.response?.data;
  let msg = `HTTP ${status} (${statusText}): `;
  if (data && typeof data === 'object' && 'errors' in data && Array.isArray(data.errors)) {
    msg += data.errors.join('\n');
  } else if (typeof data === 'string') {
    msg += stripQuotes(data);
  }
  return msg;
}
