CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE groups ADD COLUMN description VARCHAR(256) NULL;
ALTER TABLE groups ADD COLUMN created_timestamp DATETIME NOT NULL;
ALTER TABLE groups ADD COLUMN email VARCHAR(256) NOT NULL;
