CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change ADD scheduled_time DATETIME NULL;

CREATE INDEX scheduled_time_index ON batch_change (scheduled_time);
