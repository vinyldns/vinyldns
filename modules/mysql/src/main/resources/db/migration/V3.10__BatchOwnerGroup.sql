CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change ADD owner_group_id CHAR(36) NULL;
