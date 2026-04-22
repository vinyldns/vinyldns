#!/usr/bin/env bash
# =============================================================================
# VinylDNS app_config seed script
# Seeds all reloadable config keys into the app_config table.
#
# Reads connection details from the same env vars used by application.conf:
#   JDBC_USER, JDBC_PASSWORD, DATABASE_NAME, JDBC_URL
#
# Usage:
#   ./reloadble-config-db-scripts.sh [OPTIONS]
#
# Options (env vars or flags):
#   -u | --user     JDBC_USER      MySQL username   (default: root)
#   -p | --password JDBC_PASSWORD  MySQL password   (default: pass)
#   -h | --host     DB_HOST        MySQL host       (default: localhost)
#   -n | --name     DATABASE_NAME  Database name    (default: vinyldns)
#   -c | --port     DB_PORT        MySQL port       (default: 19002)
#
# The host and port are auto-parsed from JDBC_URL if set:
#   e.g. jdbc:mariadb://myhost:3306/vinyldns?...
#
# Example:
#   JDBC_USER=root JDBC_PASSWORD=pass ./reloadble-config-db-scripts.sh
#   ./reloadble-config-db-scripts.sh -h localhost -p pass
# =============================================================================

set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") [OPTIONS]"
  echo ""
  echo "Seeds all reloadable config keys into the VinylDNS app_config table."
  echo ""
  echo "Options:"
  echo "  -u | --user      MySQL username  (env: JDBC_USER,     default: root)"
  echo "  -p | --password  MySQL password  (env: JDBC_PASSWORD, default: pass)"
  echo "  -h | --host      MySQL host      (env: DB_HOST,       default: localhost)"
  echo "  -n | --name      Database name   (env: DATABASE_NAME, default: vinyldns)"
  echo "  -c | --port      MySQL port      (env: DB_PORT,       default: 19002)"
}

# ── Defaults matching application.conf values ─────────────────────────────────
DB_USER="${JDBC_USER:-root}"
DB_PASS="${JDBC_PASSWORD:-pass}"
DB_NAME="${DATABASE_NAME:-vinyldns}"

# Auto-parse host and port from JDBC_URL if set
# e.g. jdbc:mariadb://localhost:19002/vinyldns?...
if [[ -n "${JDBC_URL:-}" ]]; then
  _hostport=$(echo "$JDBC_URL" | sed -E 's|.*://([^/]+)/.*|\1|')
  DB_HOST=$(echo "$_hostport" | cut -d: -f1)
  DB_PORT=$(echo "$_hostport" | cut -d: -f2)
  DB_PORT="${DB_PORT:-19002}"
else
  DB_HOST="${DB_HOST:-localhost}"
  DB_PORT="${DB_PORT:-19002}"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -u | --user     ) DB_USER="$2"; shift 2 ;;
    -p | --password ) DB_PASS="$2"; shift 2 ;;
    -h | --host     ) DB_HOST="$2"; shift 2 ;;
    -n | --name     ) DB_NAME="$2"; shift 2 ;;
    -c | --port     ) DB_PORT="$2"; shift 2 ;;
    --help          ) usage; exit 0 ;;
    * ) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

echo "Seeding app_config into ${DB_NAME} at ${DB_HOST}:${DB_PORT} as ${DB_USER} ..."

# Write password to a temp file to avoid command-line warning and socket issues
_MYSQL_CONF=$(mktemp)
chmod 600 "$_MYSQL_CONF"
cat > "$_MYSQL_CONF" <<MYCNF
[client]
user=${DB_USER}
password=${DB_PASS}
host=${DB_HOST}
port=${DB_PORT}
protocol=TCP
MYCNF
trap 'rm -f "$_MYSQL_CONF"' EXIT

mysql \
  --defaults-extra-file="${_MYSQL_CONF}" \
  --database="${DB_NAME}" \
  <<'EOF'

-- =============================================================================
-- All keys below are reloadable via POST /appconfig/reload
-- (which re-queries MySQL then re-applies overrides, no application restart needed).
--
-- Keys that MUST stay in application.conf (NOT here):
--   - MySQL / SQS / SNS credentials  (security: keep in conf/env vars)
-- =============================================================================

INSERT INTO app_config (config_key, config_value, created_at, updated_at, created_by, updated_by) VALUES

-- ── Core Scalars ──────────────────────────────────────────────────────────────
('sync-delay',                          '10000',  NOW(), NOW(), 'system', 'system'),
('processing-disabled',                 'false',  NOW(), NOW(), 'system', 'system'),
('max-zone-size',                       '60000',  NOW(), NOW(), 'system', 'system'),
('shared-approved-types',               'A,AAAA,CNAME,PTR,TXT', NOW(), NOW(), 'system', 'system'),
('batch-change-limit',                  '1000',   NOW(), NOW(), 'system', 'system'),
('manual-batch-review-enabled',         'true',   NOW(), NOW(), 'system', 'system'),
('scheduled-changes-enabled',           'true',   NOW(), NOW(), 'system', 'system'),
('multi-record-batch-change-enabled',   'true',   NOW(), NOW(), 'system', 'system'),
('use-recordset-cache',                 'false',  NOW(), NOW(), 'system', 'system'),
('load-test-data',                      'false',  NOW(), NOW(), 'system', 'system'),
('is-zone-sync-schedule-allowed',       'true',   NOW(), NOW(), 'system', 'system'),
('default-ttl',                         '7200',   NOW(), NOW(), 'system', 'system'),

-- ── Queue ─────────────────────────────────────────────────────────────────────
('queue.messages-per-poll',             '10',     NOW(), NOW(), 'system', 'system'),
('queue.polling-interval-millis',       '250',    NOW(), NOW(), 'system', 'system'),
('queue.max-retries',                   '100',    NOW(), NOW(), 'system', 'system'),

-- ── Validation / Flags ────────────────────────────────────────────────────────
('validate-recordset-lookup-against-dns-backend', 'false', NOW(), NOW(), 'system', 'system'),

-- ── Routing Limits ────────────────────────────────────────────────────────────
('batchchange-routing-max-items-limit',       '100',  NOW(), NOW(), 'system', 'system'),
('membership-routing-default-max-items',      '100',  NOW(), NOW(), 'system', 'system'),
('membership-routing-max-items-limit',        '1000', NOW(), NOW(), 'system', 'system'),
('membership-routing-max-groups-list-limit',  '1500', NOW(), NOW(), 'system', 'system'),
('recordset-routing-default-max-items',       '100',  NOW(), NOW(), 'system', 'system'),
('zone-routing-default-max-items',            '100',  NOW(), NOW(), 'system', 'system'),
('zone-routing-max-items-limit',              '100',  NOW(), NOW(), 'system', 'system'),

-- ── Approved Name Servers ─────────────────────────────────────────────────────
('approved-name-servers',
 '172.17.42.1.,ns1.parent.com.,ns1.parent.com1.,ns1.parent.com2.,ns1.parent.com3.,ns1.parent.com4.',
 NOW(), NOW(), 'system', 'system'),

-- ── Valid Email Config ────────────────────────────────────────────────────────
('valid-email',
 '{"email-domains": ["test.com", "*dummy.com"], "number-of-dots": 2}',
 NOW(), NOW(), 'system', 'system'),

-- ── Dotted Hosts ──────────────────────────────────────────────────────────────
('dotted-hosts',
 '{"allowed-settings":[{"zone":"zonenamehere.","user-list":[],"group-list":[],"record-types":[],"dots-limit":0}]}',
 NOW(), NOW(), 'system', 'system'),

-- ── High Value Domains ────────────────────────────────────────────────────────
('high-value-domains',
 '{"fqdn-regex-list":["high-value-domain.*"],"ip-list":["192.0.1.252","192.0.1.253","192.0.2.252","192.0.2.253","192.0.3.252","192.0.3.253","192.0.4.252","192.0.4.253","fd69:27cc:fe91:0:0:0:0:ffff","fd69:27cc:fe91:0:0:0:ffff:0","fd69:27cc:fe92:0:0:0:0:ffff","fd69:27cc:fe92:0:0:0:ffff:0","fd69:27cc:fe93:0:0:0:0:ffff","fd69:27cc:fe93:0:0:0:ffff:0","fd69:27cc:fe94:0:0:0:0:ffff","fd69:27cc:fe94:0:0:0:ffff:0"]}',
 NOW(), NOW(), 'system', 'system'),

-- ── Manual Review Domains ─────────────────────────────────────────────────────
('manual-review-domains',
 '{"domain-list":["needs-review.*"],"ip-list":["192.0.1.254","192.0.1.255","192.0.2.254","192.0.2.255","192.0.3.254","192.0.3.255","192.0.4.254","192.0.4.255","fd69:27cc:fe91:0:0:0:ffff:1","fd69:27cc:fe91:0:0:0:ffff:2","fd69:27cc:fe92:0:0:0:ffff:1","fd69:27cc:fe92:0:0:0:ffff:2","fd69:27cc:fe93:0:0:0:ffff:1","fd69:27cc:fe93:0:0:0:ffff:2","fd69:27cc:fe94:0:0:0:ffff:1","fd69:27cc:fe94:0:0:0:ffff:2"],"zone-name-list":["zone.requires.review.","zone.requires.review1.","zone.requires.review2.","zone.requires.review3.","zone.requires.review4."]}',
 NOW(), NOW(), 'system', 'system'),

-- ── Global ACL Rules ──────────────────────────────────────────────────────────
('global-acl-rules',
 '[{"group-ids":["global-acl-group-id"],"fqdn-regex-list":[".*shared[0-9]{1}."]},{"group-ids":["another-global-acl-group"],"fqdn-regex-list":[".*ok[0-9]{1}."]}]',
 NOW(), NOW(), 'system', 'system'),

-- ── Backend DNS Config ────────────────────────────────────────────────────────
-- Server WILL NOT START if this key is missing.
('backend-dns-zone',
 '{"default-backend-id":"default","backend-providers":[{"class-name":"vinyldns.api.backend.dns.DnsBackendProviderLoader","settings":{"legacy":false,"backends":[{"id":"default","zone-connection":{"name":"vinyldns.","key-name":"vinyldns.","key":"nzisn+4G2ldMn0q1CV3vsg==","primary-server":"127.0.0.1:19001"},"transfer-connection":{"name":"vinyldns.","key-name":"vinyldns.","key":"nzisn+4G2ldMn0q1CV3vsg==","primary-server":"127.0.0.1:19001"},"tsig-usage":"always"},{"id":"func-test-backend","zone-connection":{"name":"vinyldns.","key-name":"vinyldns.","key":"nzisn+4G2ldMn0q1CV3vsg==","primary-server":"127.0.0.1:19001"},"transfer-connection":{"name":"vinyldns.","key-name":"vinyldns.","key":"nzisn+4G2ldMn0q1CV3vsg==","primary-server":"127.0.0.1:19001"},"tsig-usage":"always"}]}}]}',
 NOW(), NOW(), 'system', 'system'),

-- ── Email / SMTP (non-secret settings only) ───────────────────────────────────
-- Secrets (smtp.host, smtp.username, smtp.password) stay in application.conf.
('email.settings.from',                'VinylDNS <do-not-reply@vinyldns.io>', NOW(), NOW(), 'system', 'system'),
('email.settings.smtp.auth',           'true',                                NOW(), NOW(), 'system', 'system'),
('email.settings.smtp.starttls.enable','true',                                NOW(), NOW(), 'system', 'system'),
('email.settings.smtp.portal.url',     'https://vinyldns.example.com',        NOW(), NOW(), 'system', 'system'),

-- ── Notifiers ─────────────────────────────────────────────────────────────────
-- Controls which notifiers are active. SNS credentials stay in application.conf.
('notifiers', '["email","sns"]', NOW(), NOW(), 'system', 'system')

ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  updated_at   = NOW(),
  updated_by   = VALUES(updated_by);

EOF

echo "Done. app_config seeded successfully."

