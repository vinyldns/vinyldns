CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE zone ADD CONSTRAINT zone_name_unique UNIQUE (name);
