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
 * vite-plugin-hmac-proxy.ts
 *
 * A Vite dev-server plugin that replaces the Play/Scala portal's signing layer.
 * It:
 *  1. Handles POST /login  – authenticates via LDAP (username + password),
 *     then looks up the user's accessKey + secretKey from MySQL, and stores
 *     them in a server-side session.  Mirrors what processLogin() does in
 *     VinylDNS.scala / LdapAuthenticator.scala.
 *  2. Handles POST /logout – clears the session.
 *  3. Intercepts every other API path and:
 *       a. Reads the session cookie to get credentials.
 *       b. Signs the request using AWS Signature V4
 *          (service = "VinylDNS", region = "us-east-1").
 *       c. Proxies the signed request to http://localhost:9000.
 *
 * Configuration is read from `application.conf` in the `modules/frontend`
 * directory (the same file used for LDAP and MySQL settings).
 * Keys used:
 *
 *   LDAP.context.providerUrl
 *   LDAP.user  /  LDAP.password  /  LDAP.userNameAttribute
 *   LDAP.searchBase[].domainName
 *   mysql.endpoint  /  mysql.settings.{name,user,password}
 *   api.port
 */

import type { Plugin } from 'vite';
import crypto from 'crypto';
import http from 'http';
import fs from 'fs';
import path from 'path';
import ldapjs from 'ldapjs';
import mysql from 'mysql2/promise';
import { v4 as uuidv4 } from 'uuid';

// ── application.conf reader ───────────────────────────────────────────────────

interface AppConfig {
  ldap: {
    providerUrl: string;
    adminDn: string;
    adminPassword: string;
    userAttr: string;
    searchBases: string[];
  };
  mysql: {
    host: string;
    port: number;
    database: string;
    user: string;
    password: string;
  };
  api: {
    port: number;
  };
}

/**
 * Reads application.conf (HOCON) and extracts the keys needed by the proxy.
 * Strategy:
 *  - Walk lines, track nested block path with a stack.
 *  - Only capture lines of the form   key = "literal"   (skips ${?VAR} overrides).
 *  - Parse `LDAP.searchBase` array separately with a dedicated regex.
 */
function readAppConfig(): AppConfig {
  const confPath = path.resolve(process.cwd(), 'application.conf');
  const content = fs.readFileSync(confPath, 'utf8');

  const flat: Record<string, string> = {};
  const stack: string[] = [];

  for (const rawLine of content.split('\n')) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;

    // Block open: "word {" or "word-word {"
    const openMatch = line.match(/^([\w-]+)\s*\{$/);
    if (openMatch) { stack.push(openMatch[1]); continue; }

    // Block close
    if (line === '}' || line === '},') { stack.pop(); continue; }

    // Skip substitution-only lines like:  key = ${?VAR}
    if (/=\s*\$\{/.test(line)) continue;

    // key = "plain quoted string"  (no interpolation)
    const quoted = line.match(/^([\w.-]+)\s*=\s*"([^"]*)"(?:\s*#.*)?$/);
    if (quoted) {
      flat[[...stack, quoted[1]].join('.')] = quoted[2];
      continue;
    }

    // key = unquoted token  (numbers, booleans, bare words)
    const unquoted = line.match(/^([\w.-]+)\s*=\s*([^\s"$#{}\[\],][^\s#]*)(?:\s*#.*)?$/);
    if (unquoted) {
      flat[[...stack, unquoted[1]].join('.')] = unquoted[2];
    }
  }

  // searchBase is an array block – extract domainName values with a targeted regex
  const sbMatch = content.match(/searchBase\s*=\s*\[([\s\S]*?)\]/);
  const searchBases: string[] = [];
  if (sbMatch) {
    const re = /domainName\s*=\s*"([^"]*)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(sbMatch[1])) !== null) {
      if (m[1]) searchBases.push(m[1]);
    }
  }

  // mysql.endpoint = "host:port" or bare host:port
  const endpoint = flat['mysql.endpoint'] ?? 'localhost:19002';
  const colonIdx = endpoint.lastIndexOf(':');
  const mysqlHost = colonIdx !== -1 ? endpoint.slice(0, colonIdx) : endpoint;
  const mysqlPort = colonIdx !== -1 ? parseInt(endpoint.slice(colonIdx + 1), 10) : 19002;

  return {
    ldap: {
      providerUrl:   flat['LDAP.context.providerUrl'] ?? 'ldap://localhost:19004',
      adminDn:       flat['LDAP.user']                ?? 'cn=admin,dc=planetexpress,dc=com',
      adminPassword: flat['LDAP.password']            ?? 'GoodNewsEveryone',
      userAttr:      flat['LDAP.userNameAttribute']   ?? 'uid',
      searchBases:   searchBases.length ? searchBases : ['ou=people,dc=planetexpress,dc=com'],
    },
    mysql: {
      host:     mysqlHost,
      port:     isNaN(mysqlPort) ? 19002 : mysqlPort,
      database: flat['mysql.settings.name']     ?? 'vinyldns',
      user:     flat['mysql.settings.user']     ?? 'root',
      password: flat['mysql.settings.password'] ?? 'pass',
    },
    api: {
      port: parseInt(flat['api.port'] ?? '9000', 10),
    },
  };
}

let _config: AppConfig | undefined;
function getConfig(): AppConfig {
  if (!_config) _config = readAppConfig();
  return _config;
}

// ── fixed constants ───────────────────────────────────────────────────────────

const SERVICE = 'VinylDNS';
const REGION  = 'us-east-1';

/** API paths intercepted, signed, and proxied to the VinylDNS API. */
const API_PREFIXES = [
  '/groups',
  '/zones',
  '/recordsets',
  '/batchrecordsets',
  '/dnschanges',
  '/users',
  '/regenerate-creds', //not needed
  '/download-creds-file',//not needed
];

// ── session store ─────────────────────────────────────────────────────────────

interface Session {
  username: string;
  accessKey: string;
  secretKey: string;
  userId: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  isSuper: boolean;
  isSupport: boolean;
  lockStatus: string;
}

const sessions = new Map<string, Session>();

function randomToken(): string {
  return crypto.randomBytes(32).toString('hex');
}

function getSession(cookieHeader: string | undefined): Session | undefined {
  if (!cookieHeader) return undefined;
  const match = cookieHeader.match(/(?:^|;\s*)vinyldns_session=([^;]+)/);
  if (!match) return undefined;
  return sessions.get(match[1]);
}

// ── Protobuf encode/decode for VinylDNS User message ────────────────────────
// Message definition matches VinylDNSProto.proto:
//   field 1 string userName, 2 string accessKey, 3 string secretKey,
//   4 int64 created, 5 string id, 6 bool isSuper, 7 string lockStatus,
//   8 string firstName, 9 string lastName, 10 string email,
//   11 bool isSupport, 12 bool isTest

interface UserProto {
  userName: string;
  accessKey: string;
  secretKey: string;
  created: bigint;
  id: string;
  isSuper: boolean;
  lockStatus: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  isSupport?: boolean;
  isTest?: boolean;
}

function encodeVarint(value: bigint): Buffer {
  const bytes: number[] = [];
  while (value >= 128n) {
    bytes.push(Number((value & 0x7fn) | 0x80n));
    value >>= 7n;
  }
  bytes.push(Number(value));
  return Buffer.from(bytes);
}

function pbString(fieldNum: number, value: string): Buffer {
  const tag = Buffer.from([(fieldNum << 3) | 2]);
  const encoded = Buffer.from(value, 'utf8');
  const lenBuf = encodeVarint(BigInt(encoded.length));
  return Buffer.concat([tag, lenBuf, encoded]);
}

function pbBool(fieldNum: number, value: boolean): Buffer {
  return Buffer.from([(fieldNum << 3) | 0, value ? 1 : 0]);
}

function pbInt64(fieldNum: number, value: bigint): Buffer {
  return Buffer.concat([Buffer.from([(fieldNum << 3) | 0]), encodeVarint(value)]);
}

/** Encode a UserProto to the protobuf binary format stored in MySQL `data` column. */
function encodeUserProto(u: UserProto): Buffer {
  const parts: Buffer[] = [
    pbString(1, u.userName),
    pbString(2, u.accessKey),
    pbString(3, u.secretKey),
    pbInt64(4, u.created),
    pbString(5, u.id),
    pbBool(6, u.isSuper),
    pbString(7, u.lockStatus),
  ];
  if (u.firstName) parts.push(pbString(8, u.firstName));
  if (u.lastName)  parts.push(pbString(9, u.lastName));
  if (u.email)     parts.push(pbString(10, u.email));
  if (u.isSupport) parts.push(pbBool(11, u.isSupport));
  if (u.isTest)    parts.push(pbBool(12, u.isTest));
  return Buffer.concat(parts);
}

/** Decode only the fields needed from the `data` protobuf blob. */
function decodeUserProto(buf: Buffer): Partial<UserProto> {
  let pos = 0;
  const r: Partial<UserProto> = {};
  while (pos < buf.length) {
    const tagByte = buf[pos++];
    const fieldNum = tagByte >> 3;
    const wireType = tagByte & 0x7;
    if (wireType === 2) {
      let len = 0, shift = 0;
      while (pos < buf.length) {
        const b = buf[pos++]; len |= (b & 0x7f) << shift; shift += 7;
        if (!(b & 0x80)) break;
      }
      const val = buf.slice(pos, pos + len).toString('utf8');
      pos += len;
      if (fieldNum === 1) r.userName  = val;
      if (fieldNum === 2) r.accessKey = val;
      if (fieldNum === 3) r.secretKey = val;
      if (fieldNum === 5) r.id        = val;
      if (fieldNum === 7) r.lockStatus = val;
      if (fieldNum === 8) r.firstName = val;
      if (fieldNum === 9) r.lastName  = val;
      if (fieldNum === 10) r.email    = val;
    } else if (wireType === 0) {
      let val = 0n, shift = 0n;
      while (pos < buf.length) {
        const b = buf[pos++]; val |= BigInt(b & 0x7f) << shift; shift += 7n;
        if (!(b & 0x80)) break;
      }
      if (fieldNum === 4)  r.created   = val;
      if (fieldNum === 6)  r.isSuper   = val !== 0n;
      if (fieldNum === 11) r.isSupport = val !== 0n;
      if (fieldNum === 12) r.isTest    = val !== 0n;
    } else {
      // Unknown wire type – stop to avoid infinite loop
      break;
    }
  }
  return r;
}

// ── LDAP authentication ───────────────────────────────────────────────────────

interface LdapUserDetails {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
}

/**
 * Replicates LdapAuthenticator.authenticate():
 *  1. Bind as the admin service account.
 *  2. Search each configured base DN for the user entry,
 *     collecting mail / givenName / sn attributes (mirrors LdapUserDetails.apply).
 *  3. Re-bind using the user's full DN + password to verify credentials.
 */
/**
 * LDAP search-only lookup (no password verification).
 * Mirrors authenticator.lookup(username) in VinylDNS.scala / LdapAuthenticator.scala.
 * Used by the /users/lookupuser/:username handler so we can find users who
 * exist in LDAP but haven't logged into the new portal yet.
 */
function ldapLookupUser(username: string): Promise<LdapUserDetails | null> {
  const cfg = getConfig().ldap;
  const safeUsername = username.replace(/[*\\()\x00]/g, '\\$&');
  const filter = `(${cfg.userAttr}=${safeUsername})`;

  return new Promise((resolve) => {
    const adminClient = ldapjs.createClient({ url: cfg.providerUrl });
    adminClient.on('error', () => resolve(null));

    adminClient.bind(cfg.adminDn, cfg.adminPassword, (bindErr) => {
      if (bindErr) { adminClient.destroy(); return resolve(null); }

      const trySearch = (baseDNs: string[]) => {
        if (baseDNs.length === 0) { adminClient.destroy(); return resolve(null); }

        const [baseDN, ...rest] = baseDNs;
        adminClient.search(baseDN, {
          scope: 'sub', filter,
          attributes: ['dn', cfg.userAttr, 'mail', 'givenName', 'sn'],
        }, (searchErr, searchRes) => {
          if (searchErr) return trySearch(rest);

          let found = false;
          let email: string | undefined;
          let firstName: string | undefined;
          let lastName: string | undefined;

          searchRes.on('searchEntry', (entry) => {
            if (!found) {
              found = true;
              for (const attr of entry.attributes) {
                const name = attr.type.toLowerCase();
                const val  = attr.values[0];
                if (name === 'mail')      email     = val;
                if (name === 'givenname') firstName = val;
                if (name === 'sn')        lastName  = val;
              }
            }
          });
          searchRes.on('error', () => trySearch(rest));
          searchRes.on('end', () => {
            adminClient.destroy();
            if (found) resolve({ username, email, firstName, lastName });
            else trySearch(rest);
          });
        });
      };
      trySearch(cfg.searchBases);
    });
  });
}

function ldapAuthenticate(username: string, password: string): Promise<LdapUserDetails> {
  const cfg = getConfig().ldap;
  // Escape special characters in the username for use in an LDAP filter
  const safeUsername = username.replace(/[*\\()\x00]/g, '\\$&');
  const filter = `(${cfg.userAttr}=${safeUsername})`;

  return new Promise((resolve, reject) => {
    const adminClient = ldapjs.createClient({ url: cfg.providerUrl });

    adminClient.on('error', (err: Error) => {
      reject(new Error(`LDAP connection error: ${err.message}`));
    });

    // Step 1 – bind as admin
    adminClient.bind(cfg.adminDn, cfg.adminPassword, (bindErr) => {
      if (bindErr) {
        adminClient.destroy();
        return reject(new Error(`LDAP admin bind failed: ${bindErr.message}`));
      }

      // Step 2 – search for the user, fetching profile attributes too
      const trySearch = (baseDNs: string[]) => {
        if (baseDNs.length === 0) {
          adminClient.destroy();
          return reject(new Error(`User '${username}' not found in LDAP`));
        }

        const [baseDN, ...rest] = baseDNs;
        adminClient.search(
          baseDN,
          {
            scope: 'sub',
            filter,
            // fetch uid + profile fields, mirrors LdapUserDetails.apply
            attributes: ['dn', cfg.userAttr, 'mail', 'givenName', 'sn'],
          },
          (searchErr, searchRes) => {
            if (searchErr) {
              return trySearch(rest);
            }

            let userDN = '';
            let email: string | undefined;
            let firstName: string | undefined;
            let lastName: string | undefined;

            searchRes.on('searchEntry', (entry) => {
              if (!userDN) {
                userDN = entry.dn.toString();
                // Retrieve optional profile attributes
                const attrs = entry.attributes;
                for (const attr of attrs) {
                  const name = attr.type.toLowerCase();
                  const val  = attr.values[0];
                  if (name === 'mail')       email     = val;
                  if (name === 'givenname')  firstName = val;
                  if (name === 'sn')          lastName  = val;
                }
              }
            });
            searchRes.on('error', () => trySearch(rest));
            searchRes.on('end', () => {
              if (!userDN) {
                return trySearch(rest);
              }

              // Step 3 – re-bind as user to verify password
              const userClient = ldapjs.createClient({ url: cfg.providerUrl });
              userClient.on('error', (e: Error) => {
                adminClient.destroy();
                reject(new Error(`LDAP user bind error: ${e.message}`));
              });
              userClient.bind(userDN, password, (userBindErr) => {
                userClient.destroy();
                adminClient.destroy();
                if (userBindErr) {
                  reject(new Error('Invalid credentials'));
                } else {
                  resolve({ username, email, firstName, lastName });
                }
              });
            });
          },
        );
      };

      trySearch(cfg.searchBases);
    });
  });
}

// ── MySQL credential lookup ───────────────────────────────────────────────────

interface VinylUser {
  userName: string;
  accessKey: string;
  secretKey: string;
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  isSuper: boolean;
  isSupport: boolean;
  lockStatus: string;
}

/** Open a pooled MySQL connection using app config. */
function openMysqlConnection() {
  const cfg = getConfig().mysql;
  return mysql.createConnection({
    host: cfg.host, port: cfg.port, database: cfg.database,
    user: cfg.user, password: cfg.password,
  });
}

/**
 * Replicates userAccountAccessor.get(username).
 * The MySQL table is `user` with columns id, user_name, access_key, data.
 * `data` is a protobuf-encoded User message (VinylDNSProto.User).
 * NoOpCrypto stores secretKey unencrypted so no decryption needed.
 */
async function getUserCredentials(username: string): Promise<VinylUser | null> {
  const conn = await openMysqlConnection();
  try {
    const [rows] = await conn.execute<mysql.RowDataPacket[]>(
      'SELECT access_key, data FROM `user` WHERE user_name = ? LIMIT 1',
      [username],
    );
    if (rows.length === 0) return null;
    const row = rows[0];
    const dataBlob: Buffer = row.data as Buffer;
    const proto = decodeUserProto(dataBlob);
    return {
      userName:   username,
      accessKey:  proto.accessKey  ?? (row.access_key as string),
      secretKey:  proto.secretKey  ?? '',
      id:         proto.id          ?? '',
      firstName:  proto.firstName,
      lastName:   proto.lastName,
      email:      proto.email,
      isSuper:    proto.isSuper    ?? false,
      isSupport:  proto.isSupport  ?? false,
      lockStatus: proto.lockStatus ?? 'Unlocked',
    };
  } finally {
    await conn.end();
  }
}

/** Generates a 20-character random alphanumeric key (mirrors User.generateKey in Scala). */
function generateKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let key = '';
  const bytes = crypto.randomBytes(20);
  for (const b of bytes) key += chars[b % chars.length];
  return key;
}

/**
 * Replicates createNewUser() in VinylDNS.scala.
 * Generates accessKey + secretKey, inserts a protobuf-encoded User record
 * into MySQL, and returns the new credentials.
 */
async function createUser(ldapDetails: LdapUserDetails): Promise<VinylUser> {
  const accessKey = generateKey();
  const secretKey = generateKey();
  const id        = uuidv4();
  const created   = BigInt(Date.now());

  const proto: UserProto = {
    userName:   ldapDetails.username,
    accessKey,
    secretKey,
    created,
    id,
    // Mirror MySqlUserRepository.save() → user.copy(isSuper = true):
    // every user is treated as a super user, matching old portal behaviour.
    isSuper:    true,
    lockStatus: 'Unlocked',
    firstName:  ldapDetails.firstName,
    lastName:   ldapDetails.lastName,
    email:      ldapDetails.email,
    isSupport:  false,
    isTest:     false,
  };

  const data = encodeUserProto(proto);
  const conn = await openMysqlConnection();
  try {
    await conn.execute(
      'REPLACE INTO `user` (id, user_name, access_key, data) VALUES (?, ?, ?, ?)',
      [id, ldapDetails.username, accessKey, data],
    );
    console.log(`[hmac-proxy] Created new VinylDNS user: ${ldapDetails.username}`);
  } finally {
    await conn.end();
  }

  return {
    userName:   ldapDetails.username,
    accessKey,
    secretKey,
    id,
    firstName:  ldapDetails.firstName,
    lastName:   ldapDetails.lastName,
    email:      ldapDetails.email,
    // Mirror MySqlUserRepository.save() → user.copy(isSuper = true)
    isSuper:    true,
    isSupport:  false,
    lockStatus: 'Unlocked',
  };
}

/**
 * Regenerates accessKey and secretKey for a user in MySQL and returns the new values.
 * Mirrors User.regenerateCredentials() in Scala.
 */
async function updateUserCredentials(username: string): Promise<{ accessKey: string; secretKey: string }> {
  const newAccessKey = generateKey();
  const newSecretKey = generateKey();
  const conn = await openMysqlConnection();
  try {
    const [rows] = await conn.execute<mysql.RowDataPacket[]>(
      'SELECT data FROM `user` WHERE user_name = ? LIMIT 1',
      [username],
    );
    if (rows.length === 0) throw new Error(`User ${username} not found`);
    const proto = decodeUserProto(rows[0].data as Buffer);
    const updatedProto: UserProto = {
      userName:   proto.userName   ?? username,
      accessKey:  newAccessKey,
      secretKey:  newSecretKey,
      created:    proto.created    ?? BigInt(Date.now()),
      id:         proto.id         ?? '',
      isSuper:    proto.isSuper    ?? false,
      lockStatus: proto.lockStatus ?? 'Unlocked',
      firstName:  proto.firstName,
      lastName:   proto.lastName,
      email:      proto.email,
      isSupport:  proto.isSupport  ?? false,
      isTest:     proto.isTest     ?? false,
    };
    await conn.execute(
      'UPDATE `user` SET access_key = ?, data = ? WHERE user_name = ?',
      [newAccessKey, encodeUserProto(updatedProto), username],
    );
    console.log(`[hmac-proxy] Regenerated credentials for ${username}`);
  } finally {
    await conn.end();
  }
  return { accessKey: newAccessKey, secretKey: newSecretKey };
}

// ── AWS Signature V4 ─────────────────────────────────────────────────────────

function hmacSha256(key: Buffer | string, data: string): Buffer {
  const k = Buffer.isBuffer(key) ? key : Buffer.from(key, 'utf8');
  return crypto.createHmac('sha256', k).update(data, 'utf8').digest();
}

function sha256Hex(data: string | Buffer): string {
  const h = crypto.createHash('sha256');
  if (Buffer.isBuffer(data)) h.update(data);
  else h.update(data, 'utf8');
  return h.digest('hex');
}

function awsEncode(s: string): string {
  return encodeURIComponent(s)
    .replace(/\+/g, '%20')
    .replace(/%7E/g, '~')
    .replace(/\*/g, '%2A');
}

/** Produces `key=value&key=value` sorted by key then value, AWS-encoded. */
function canonicalQueryString(rawQuery: string): string {
  if (!rawQuery) return '';
  const pairs: [string, string][] = rawQuery.split('&').map((p) => {
    const eq = p.indexOf('=');
    if (eq === -1) return [decodeURIComponent(p), ''];
    return [decodeURIComponent(p.slice(0, eq)), decodeURIComponent(p.slice(eq + 1))];
  });
  pairs.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] < b[1] ? -1 : 1));
  return pairs.map(([k, v]) => `${awsEncode(k)}=${awsEncode(v)}`).join('&');
}

function getSigningKey(
  secretKey: string,
  dateStamp: string,
  region: string,
  service: string,
): Buffer {
  const kDate    = hmacSha256(Buffer.from('AWS4' + secretKey, 'utf8'), dateStamp);
  const kRegion  = hmacSha256(kDate,    region);
  const kService = hmacSha256(kRegion,  service);
  const kSigning = hmacSha256(kService, 'aws4_request');
  return kSigning;
}

/**
 * Signs `method path?query` using AWS V4 and returns the Authorization header
 * plus the X-Amz-Date header that must be forwarded verbatim.
 */
function buildAuthHeaders(
  method: string,
  rawPath: string,
  rawQuery: string,
  bodyBuf: Buffer,
  accessKey: string,
  secretKey: string,
): Record<string, string> {
  const apiPort = getConfig().api.port;
  const now = new Date();
  // yyyyMMddTHHmmssZ  (ISO-8601 compact, UTC, no milliseconds)
  const dateTime =
    now.getUTCFullYear().toString() +
    String(now.getUTCMonth() + 1).padStart(2, '0') +
    String(now.getUTCDate()).padStart(2, '0') +
    'T' +
    String(now.getUTCHours()).padStart(2, '0') +
    String(now.getUTCMinutes()).padStart(2, '0') +
    String(now.getUTCSeconds()).padStart(2, '0') +
    'Z';
  const dateStamp = dateTime.slice(0, 8);

  const hostHeader = `localhost:${apiPort}`;
  const bodyHash   = sha256Hex(bodyBuf);

  // Headers to sign (sorted alphabetically by key)
  const signMap: Record<string, string> = {
    host:                 hostHeader,
    'x-amz-content-sha256': bodyHash,
    'x-amz-date':         dateTime,
  };

  const sortedNames   = Object.keys(signMap).sort();
  const signedHeaders = sortedNames.join(';');

  // Canonical headers: "key:value" elements then empty string, as per Scala code
  const canonHeaders = sortedNames.map((k) => `${k}:${signMap[k]}`);

  // Normalise path (remove /./ and /../)
  let normalPath: string;
  try {
    const u = new URL('http://x' + (rawPath || '/'));
    normalPath = u.pathname;
  } catch {
    normalPath = rawPath || '/';
  }

  const canonRequest = [
    method.toUpperCase(),
    normalPath,
    canonicalQueryString(rawQuery),
    ...canonHeaders,
    '',
    signedHeaders,
    bodyHash,
  ].join('\n');

  const scope        = `${dateStamp}/${REGION}/${SERVICE}/aws4_request`;
  const stringToSign = ['AWS4-HMAC-SHA256', dateTime, scope, sha256Hex(canonRequest)].join('\n');
  const signingKey   = getSigningKey(secretKey, dateStamp, REGION, SERVICE);
  const signature    = hmacSha256(signingKey, stringToSign).toString('hex');

  const authorization =
    `AWS4-HMAC-SHA256 Credential=${accessKey}/${scope}, ` +
    `SignedHeaders=${signedHeaders}, ` +
    `Signature=${signature}`;

  return {
    authorization,
    'x-amz-date':            dateTime,
    'x-amz-content-sha256':  bodyHash,
    host:                    hostHeader,
  };
}

// ── HTTP proxy helper ───────────────────────────────────────────────────────── not needed

/** Forwards a request to the VinylDNS API and pipes the response back. */
function proxyToApi(
  method: string,
  path: string,
  query: string,
  reqHeaders: Record<string, string>,
  bodyBuf: Buffer,
  serverRes: http.ServerResponse,
): void {
  const target = query ? `${path}?${query}` : path;

  const options: http.RequestOptions = {
    hostname: 'localhost',
    port:     getConfig().api.port,
    method:   method.toUpperCase(),
    path:     target,
    headers:  reqHeaders,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    // Pass through status + headers
    serverRes.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers as Record<string, string>);
    proxyRes.pipe(serverRes, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error('[hmac-proxy] upstream error:', err.message);
    if (!serverRes.headersSent) serverRes.writeHead(502);
    serverRes.end(JSON.stringify({ error: 'Upstream API unavailable', detail: err.message }));
  });

  if (bodyBuf.length > 0) proxyReq.write(bodyBuf);
  proxyReq.end();
}

/** Reads the full request body as a Buffer. */
function readBody(req: http.IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (c: Buffer) => chunks.push(c));
    req.on('end',  () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

/** Like proxyToApi but logs the upstream response status. */
function proxyToApiWithLog(
  method: string,
  path: string,
  query: string,
  reqHeaders: Record<string, string>,
  bodyBuf: Buffer,
  serverRes: http.ServerResponse,
): void {
  const target = query ? `${path}?${query}` : path;
  const options: http.RequestOptions = {
    hostname: 'localhost',
    port:     getConfig().api.port,
    method:   method.toUpperCase(),
    path:     target,
    headers:  reqHeaders,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    const status = proxyRes.statusCode ?? 502;
    const qs = query ? `?${query}` : '';
    console.log(`[hmac-proxy] ← ${method} ${path}${qs} → HTTP ${status}`);
    serverRes.writeHead(status, proxyRes.headers as Record<string, string>);
    proxyRes.pipe(serverRes, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error('[hmac-proxy] upstream error:', err.message);
    if (!serverRes.headersSent) serverRes.writeHead(502);
    serverRes.end(JSON.stringify({ error: 'Upstream API unavailable', detail: err.message }));
  });

  if (bodyBuf.length > 0) proxyReq.write(bodyBuf);
  proxyReq.end();
}

// ── Vite plugin ───────────────────────────────────────────────────────────────

export function hmacProxyPlugin(): Plugin {
  return {
    name: 'vinyldns-hmac-proxy',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url   = req.url ?? '/';
        const qIdx  = url.indexOf('?');
        const path  = qIdx === -1 ? url  : url.slice(0, qIdx);
        const query = qIdx === -1 ? ''   : url.slice(qIdx + 1);
        const method = (req.method ?? 'GET').toUpperCase();

        // ── POST /login ────────────────────────────────────────────────────
        if (path === '/login' && method === 'POST') {
          try {
            const bodyBuf = await readBody(req);
            const bodyStr = bodyBuf.toString('utf8');

            // Accept both JSON and form-encoded bodies
            let username = '';
            let password = '';

            const ct = (req.headers['content-type'] ?? '').toLowerCase();
            if (ct.includes('application/json')) {
              const parsed = JSON.parse(bodyStr) as Record<string, string>;
              username = parsed.username ?? '';
              password = parsed.password ?? '';
            } else {
              // x-www-form-urlencoded
              const params = new URLSearchParams(bodyStr);
              username = params.get('username') ?? '';
              password = params.get('password') ?? '';
            }

            if (!username || !password) {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: 'username and password are required' }));
              return;
            }

            // Step 1 – authenticate via LDAP (mirrors LdapAuthenticator.authenticate)
            let ldapDetails: LdapUserDetails;
            try {
              ldapDetails = await ldapAuthenticate(username, password);
            } catch (ldapErr) {
              console.error('[hmac-proxy] LDAP auth failed:', (ldapErr as Error).message);
              res.writeHead(401, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: 'Authentication failed, please try again' }));
              return;
            }

            // Step 2 – fetch or create user record in MySQL
            // Mirrors processLoginWithDetails(): get existing OR createNewUser()
            let user = await getUserCredentials(username);
            if (!user) {
              console.log(`[hmac-proxy] First login for '${username}' – creating VinylDNS account.`);
              user = await createUser(ldapDetails);
            }

            // Step 3 – create session
            const sessionId = randomToken();
            sessions.set(sessionId, {
              username:   user.userName,
              accessKey:  user.accessKey,
              secretKey:  user.secretKey,
              userId:     user.id,
              firstName:  user.firstName,
              lastName:   user.lastName,
              email:      user.email,
              isSuper:    user.isSuper,
              isSupport:  user.isSupport,
              lockStatus: user.lockStatus,
            });

            res.writeHead(200, {
              'Content-Type': 'application/json',
              'Set-Cookie': `vinyldns_session=${sessionId}; Path=/; HttpOnly; SameSite=Strict`,
            });
            res.end(JSON.stringify({ ok: true, username: user.userName }));
          } catch (err) {
            console.error('[hmac-proxy] /login error:', err);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Login failed' }));
          }
          return;
        }

        // ── POST /logout ───────────────────────────────────────────────────
        if (path === '/logout' && (method === 'POST' || method === 'GET')) {
          const cookieHeader = req.headers['cookie'];
          const match = cookieHeader?.match(/(?:^|;\s*)vinyldns_session=([^;]+)/);
          if (match) sessions.delete(match[1]);

          res.writeHead(200, {
            'Content-Type': 'application/json',
            'Set-Cookie': 'vinyldns_session=; Path=/; HttpOnly; Max-Age=0',
          });
          res.end(JSON.stringify({ ok: true }));
          return;
        }

        // ── POST /regenerate-creds ─────────────────────────────────────────
        if (path === '/regenerate-creds' && method === 'POST') {
          const session = getSession(req.headers['cookie']);
          if (!session) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not authenticated' }));
            return;
          }
          try {
            const { accessKey, secretKey } = await updateUserCredentials(session.username);
            session.accessKey = accessKey;
            session.secretKey = secretKey;
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ ok: true }));
          } catch (err) {
            console.error('[hmac-proxy] regenerate-creds error:', err);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to regenerate credentials' }));
          }
          return;
        }

        // ── GET /download-creds-file/:filename ─────────────────────────────
        if (method === 'GET' && path.startsWith('/download-creds-file/')) {
          const session = getSession(req.headers['cookie']);
          if (!session) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not authenticated' }));
            return;
          }
          const fileName = path.slice('/download-creds-file/'.length);
          const apiUrl = `http://localhost:${getConfig().api.port}`;
          const csv = `NT ID, access key, secret key,api url\n${session.username},${session.accessKey},${session.secretKey},${apiUrl}`;
          res.writeHead(200, {
            'Content-Type': 'text/csv',
            'Content-Disposition': `attachment; filename="${fileName}"`,
          });
          res.end(csv);
          return;
        }

        // ── GET /users/currentuser ─────────────────────────────────────────
        // This is a portal-only endpoint (not in the VinylDNS API).
        // Return the signed-in user's profile from the session.
        if (path === '/users/currentuser' && method === 'GET') {
          const session = getSession(req.headers['cookie']);
          if (!session) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not authenticated' }));
            return;
          }
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            id:          session.userId,
            userName:    session.username,
            firstName:   session.firstName  ?? null,
            lastName:    session.lastName   ?? null,
            email:       session.email      ?? null,
            isSuper:     session.isSuper,
            isSupport:   session.isSupport,
            lockStatus:  session.lockStatus,
          }));
          return;
        }


        // Only intercept genuine API calls, not browser page navigations.
        // Browser navigations have Accept: text/html,...  (user typed /groups in URL bar)
        // Axios / fetch API calls have Accept: application/json,...
        const accept = (req.headers['accept'] ?? '').toLowerCase();
        const isBrowserNavigation = accept.startsWith('text/html');
        const isApiPath = API_PREFIXES.some((prefix) => path.startsWith(prefix));

        if (!isApiPath || isBrowserNavigation) {
          next();
          return;
        }

        const session = getSession(req.headers['cookie']);
        if (!session) {
          res.writeHead(401, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Not authenticated' }));
          return;
        }

        try {
          const bodyBuf = await readBody(req);

          // ── /users/lookupuser/:username  (portal-only, mirrors old VinylDNS.scala) ──
          // Strategy (same as old portal's getUserDataByUsername):
          //   1. Try MySQL   – user already exists (has logged in before)
          //   2. Try LDAP    – user exists in directory but hasn't logged in yet → create in MySQL
          //   3. Return 404  – truly unknown user
          const lookupMatch = path.match(/^\/users\/lookupuser\/(.+)$/);
          if (lookupMatch && method === 'GET') {
            const username = decodeURIComponent(lookupMatch[1]);
            let user = await getUserCredentials(username);
            if (!user) {
              console.log(`[hmac-proxy] lookupuser: '${username}' not in MySQL, trying LDAP…`);
              const ldapDetails = await ldapLookupUser(username);
              if (ldapDetails) {
                user = await createUser(ldapDetails);
                console.log(`[hmac-proxy] lookupuser: created VinylDNS account for '${username}'`);
              }
            }
            if (!user) {
              res.writeHead(404, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: `User ${username} was not found` }));
              return;
            }
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              id:         user.id,
              userName:   user.userName,
              firstName:  user.firstName  ?? null,
              lastName:   user.lastName   ?? null,
              email:      user.email      ?? null,
              isSuper:    user.isSuper,
              isSupport:  user.isSupport,
              lockStatus: user.lockStatus,
            }));
            return;
          }

          // Rewrite portal-specific paths to core-API equivalents (none remaining after above).
          const apiPath = path;

          const authHdrs  = buildAuthHeaders(method, apiPath, query, bodyBuf, session.accessKey, session.secretKey);

          const forwardHeaders: Record<string, string> = {
            ...authHdrs,
            'content-type':   (req.headers['content-type'] as string) ?? 'application/json',
            'content-length': String(bodyBuf.length),
            'accept':         (req.headers['accept'] as string) ?? 'application/json',
          };

          const qs = query ? `?${query}` : '';
          console.log(`[hmac-proxy] → ${method} ${apiPath}${qs} (body: ${bodyBuf.length} bytes, user: ${session.username})`);

          proxyToApiWithLog(method, apiPath, query, forwardHeaders, bodyBuf, res as unknown as http.ServerResponse);
        } catch (err) {
          console.error('[hmac-proxy] proxy error:', err);
          if (!res.headersSent) res.writeHead(500);
          (res as unknown as http.ServerResponse).end(JSON.stringify({ error: 'Proxy error' }));
        }
      });
    },
  };
}
