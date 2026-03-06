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

export type RecordType =
  | 'A'
  | 'AAAA'
  | 'CNAME'
  | 'MX'
  | 'NS'
  | 'PTR'
  | 'SOA'
  | 'SPF'
  | 'SRV'
  | 'SSHFP'
  | 'TXT'
  | 'NAPTR'
  | 'DS'
  | 'CAA';

export interface RecordData {
  address?: string;
  cname?: string;
  ptrdname?: string;
  exchange?: string;
  preference?: number;
  nsdname?: string;
  mname?: string;
  rname?: string;
  serial?: number;
  refresh?: number;
  retry?: number;
  expire?: number;
  minimum?: number;
  text?: string;
  priority?: number;
  weight?: number;
  port?: number;
  target?: string;
  algorithm?: number;
  fingerprint?: string;
  fingerprintType?: number;
  order?: number;
  replacement?: string;
  regexp?: string;
  flags?: string | number;
  service?: string;
  keytag?: number;
  digesttype?: number;
  digest?: string;
  tag?: string;
  value?: string;
}

export interface RecordSet {
  id: string;
  zoneId: string;
  zoneName?: string;
  name: string;
  type: RecordType;
  status: 'Active' | 'Inactive' | 'Pending' | 'PendingDelete' | 'PendingUpdate';
  created?: string;
  updated?: string;
  ttl: number;
  records: RecordData[];
  account?: string;
  accessLevel?: string;
  ownerGroupId?: string;
}

export interface RecordSetListResponse {
  recordSets: RecordSet[];
  startFrom?: string;
  nextId?: string;
  maxItems: number;
}

export interface RecordSetChange {
  recordSet: RecordSet;
  changeType: string;
  status: string;
  systemMessage?: string;
  created: string;
  userId: string;
  id: string;
  zoneId: string;
}

export interface RecordSetChangesResponse {
  recordSetChanges: RecordSetChange[];
  startFrom?: string;
  nextId?: string;
  maxItems: number;
}
