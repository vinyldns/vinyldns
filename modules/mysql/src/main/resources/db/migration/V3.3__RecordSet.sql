CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE recordset (
  id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  name VARCHAR(256) NOT NULL,
  type TINYINT NOT NULL,
  record_data VARCHAR(4096) NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX zone_id_name_index (zone_id, name, type),
  data_hash VARCHAR(40) NOT NULL
);
