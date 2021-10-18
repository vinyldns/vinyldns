CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE recordset_data (
  id VARCHAR(36) NOT NULL,
  recordset_id VARCHAR(36) NOT NULL,
  zone_id VARCHAR(36) NOT NULL,
  fqdn VARCHAR(255) NOT NULL,
  type VARCHAR(25) NOT NULL,
  record_data VARCHAR(4096) NOT NULL,
  ip CHAR(36),
  PRIMARY KEY (id),
  INDEX zone_id_name_index (zone_id, type, fqdn, ip,recordset_id)
);

