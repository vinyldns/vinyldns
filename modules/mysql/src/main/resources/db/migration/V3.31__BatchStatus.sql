CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change ADD batch_status VARCHAR(25) NOT NULL;