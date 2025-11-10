CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE zone_change
ADD COLUMN zone_name VARCHAR(256) NOT NULL;
CREATE INDEX zone_name_index ON zone_change(zone_name);


ALTER TABLE zone_change
ADD COLUMN zone_status CHAR(36) NOT NULL;
CREATE INDEX zone_status_index ON zone_change(zone_status);