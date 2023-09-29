CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE record_change ADD COLUMN IF NOT EXISTS fqdn VARCHAR(255) NOT NULL,
ALTER TABLE record_change ADD COLUMN IF NOT EXISTS record_type VARCHAR(255) NOT NULL;
CREATE INDEX fqdn_index ON record_change (fqdn);
CREATE INDEX record_type_index ON record_change (record_type);
