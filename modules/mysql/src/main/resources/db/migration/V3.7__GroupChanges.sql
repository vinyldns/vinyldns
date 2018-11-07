CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create table to store group changes
*/
CREATE TABLE group_change (
  group_change_id CHAR(36) NOT NULL,
  group_id CHAR(36) NOT NULL,
  created_timestamp BIGINT(13) NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (group_change_id),
  INDEX group_id_index (group_id)
);
