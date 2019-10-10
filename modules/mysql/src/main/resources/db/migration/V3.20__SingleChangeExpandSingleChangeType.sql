CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE single_change MODIFY change_type VARCHAR(25) NOT NULL;
