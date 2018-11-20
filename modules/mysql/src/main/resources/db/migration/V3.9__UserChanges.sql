CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the user change table
*/

CREATE TABLE user_change (
  change_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  data BLOB NOT NULL,
  created_timestamp BIGINT(13) NOT NULL,
  PRIMARY KEY (change_id),
  INDEX user_id_index (user_id),
  INDEX created_timestamp_index (created_timestamp)
);
