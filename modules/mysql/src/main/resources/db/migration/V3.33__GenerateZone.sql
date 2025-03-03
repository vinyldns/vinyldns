CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Generate Zone table
*/
CREATE TABLE generate_zone (
  id CHAR(36) NOT NULL,
  name VARCHAR(256) NOT NULL,
  admin_group_id CHAR(36) NOT NULL,
  response BLOB NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX generate_zone_name_index (name),
  INDEX generate_zone_admin_group_id_index (admin_group_id)
);