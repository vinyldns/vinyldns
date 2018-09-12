CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Zone Change table
*/
CREATE TABLE zone_change (
  change_id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  data BLOB NOT NULL,
  created_time_change_id VARCHAR(52) NOT NULL,
  PRIMARY KEY (change_id)
);
