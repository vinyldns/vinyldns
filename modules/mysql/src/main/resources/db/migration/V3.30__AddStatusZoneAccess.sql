CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE zone_access
DROP FOREIGN KEY fk_zone_access,
ADD COLUMN zone_status CHAR(36) NOT NULL;