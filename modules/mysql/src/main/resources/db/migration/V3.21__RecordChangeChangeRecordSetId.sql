CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE record_change ADD recordset_id CHAR(36) NULL;
CREATE INDEX recordset_id_index ON record_change (recordset_id);
