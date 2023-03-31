CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE record_change ADD COLUMN fqdn VARCHAR(255) NOT NULL;
CREATE INDEX fqdn_index ON record_change (fqdn);
