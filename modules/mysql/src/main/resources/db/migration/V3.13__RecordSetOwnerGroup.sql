CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset ADD COLUMN owner_group_id CHAR(36) NULL;
CREATE INDEX owner_group_id_index ON recordset (owner_group_id);
