CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create table to store groups
*/
CREATE TABLE `groups` (
  id CHAR(36) NOT NULL,
  name VARCHAR(256) NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX group_name_index (name)
);
