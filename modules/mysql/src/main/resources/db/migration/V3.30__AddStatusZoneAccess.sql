CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE zone_access
DROP CONSTRAINT fk_zone_access,
ADD zone_status CHAR(36) NOT NULL
;