CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset ADD COLUMN data_hash VARCHAR(40) NOT NULL;
CREATE INDEX data_hash_index ON recordset (data_hash);
