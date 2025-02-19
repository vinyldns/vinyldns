CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Zone table  We are not storing the shared flag or the account here as the new Zone repo
is not planned on being backward compatible, and we would have data in the table that we do not need
*/
CREATE TABLE generate_zone (
  id CHAR(36) NOT NULL,
  name VARCHAR(256) NOT NULL,
  admin_group_id CHAR(36) NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX generate_zone_name_index (name),
  INDEX generate_zone_admin_group_id_index (admin_group_id)
);