CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset ADD COLUMN fqdn VARCHAR(512) NOT NULL;
CREATE INDEX fqdn_index ON recordset (fqdn);
