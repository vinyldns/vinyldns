CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE zone ADD zone_sync_schedule VARCHAR(256) NULL;
