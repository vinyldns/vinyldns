CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change ADD scheduled_time DATETIME NULL;
