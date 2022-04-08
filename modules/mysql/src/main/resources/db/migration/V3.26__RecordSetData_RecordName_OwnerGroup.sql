CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset_data ADD COLUMN record_name VARCHAR(256) AFTER zone_id;
CREATE INDEX recordset_data_record_name_index ON recordset_data (record_name);

ALTER TABLE recordset_data ADD COLUMN owner_group_id CHAR(36) AFTER ip;
CREATE INDEX recordset_data_owner_group_id_index ON recordset_data (owner_group_id);