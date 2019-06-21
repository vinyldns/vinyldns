CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE single_change MODIFY zone_id CHAR(36) NULL;
