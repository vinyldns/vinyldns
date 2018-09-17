CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Zone Change table
*/
CREATE TABLE zone_change (
  change_id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  data BLOB NOT NULL,
  created_timestamp BIGINT(13) NOT NULL,
  PRIMARY KEY (change_id),
  INDEX zone_id_index (zone_id),
  INDEX created_timestamp_index (created_timestamp)
);
