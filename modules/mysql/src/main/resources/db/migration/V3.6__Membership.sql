CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create table to store membership relationships
*/
CREATE TABLE membership (
  user_id CHAR(36) NOT NULL,
  group_id CHAR(36) NOT NULL,
  PRIMARY KEY (user_id, group_id)
);
