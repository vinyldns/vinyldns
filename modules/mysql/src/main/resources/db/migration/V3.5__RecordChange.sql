CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE record_change (
  id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  created BIGINT(13) NOT NULL,
  type TINYINT NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX zone_id_index (zone_id),
  INDEX created_index (created)
);
