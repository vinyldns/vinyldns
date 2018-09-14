CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Zone table  We are not storing the shared flag or the account here as the new Zone repo
is not planned on being backward compatible, and we would have data in the table that we do not need
*/
CREATE TABLE zone (
  id CHAR(36) NOT NULL,
  name VARCHAR(256) NOT NULL,
  admin_group_id CHAR(36) NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (id),
  INDEX zone_name_index (name),
  INDEX zone_admin_group_id_index (admin_group_id)
);

/*
The Zone Access table provides a lookup to easily find zones that an individual user has access to.
The accessor_id is either a group id OR a user id
The zone_id is the zone_id for the zone
*/
CREATE TABLE zone_access (
  accessor_id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  PRIMARY KEY (accessor_id, zone_id),
  CONSTRAINT fk_zone_access FOREIGN KEY (zone_id)
    REFERENCES zone(id)
    ON DELETE CASCADE,
  INDEX user_id_index (accessor_id),
  INDEX zone_id_index (zone_id)
);
