CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE recordset_data (
  recordset_id VARCHAR(36) NOT NULL,
  zone_id VARCHAR(36) NOT NULL,
  fqdn VARCHAR(255) NOT NULL,
  reverse_fqdn VARCHAR(255) NOT NULL,
  type VARCHAR(25) NOT NULL,
  record_data VARCHAR(4096) NOT NULL,
  ip VARBINARY(16),
  data BLOB NOT NULL,
  INDEX recordset_data_zone_id_index(zone_id),
  INDEX recordset_data_type_index(type),
  INDEX recordset_data_fqdn_index(fqdn),
  INDEX recordset_data_ip_index(ip),
  INDEX recordset_data_recordset_id_index(recordset_id),
  INDEX recordset_data_reverse_fqdn_index(reverse_fqdn),
  FULLTEXT INDEX recordset_data_fdqn_fulltext_index(fqdn),
  FULLTEXT INDEX recordset_data_reverse_fqdn_fulltext_index(reverse_fqdn),
  FULLTEXT INDEX recordset_data_record_data_fulltext_index(record_data)
);