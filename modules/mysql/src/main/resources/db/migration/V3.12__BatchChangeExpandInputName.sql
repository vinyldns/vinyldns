CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE single_change MODIFY input_name VARCHAR(255) NOT NULL;
