CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE recordset_data (
  recordset_id VARCHAR(36) NOT NULL,
  zone_id VARCHAR(36) NOT NULL,
  fqdn VARCHAR(255) NOT NULL,
  reverse_fqdn VARCHAR(255) NOT NULL,
  type VARCHAR(25) NOT NULL,
  record_data VARCHAR(4096) NOT NULL,
  ip CHAR(36),
  INDEX zone_id_name_type_fdqn_ip_rsid_rfdqn_index (zone_id, type, fqdn, ip, recordset_id, reverse_fqdn),
  FULLTEXT INDEX fulltext_fdqn_recorddata_index(fqdn, record_data)
);

