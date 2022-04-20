CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset_data ADD COLUMN record_name VARCHAR(256) AFTER zone_id;
CREATE INDEX recordset_data_record_name_index ON recordset_data (record_name);

ALTER TABLE recordset_data ADD COLUMN owner_group_id CHAR(36) AFTER ip;
CREATE INDEX recordset_data_owner_group_id_index ON recordset_data (owner_group_id);

ALTER TABLE recordset_data DROP INDEX recordset_data_fdqn_fulltext_index;
ALTER TABLE recordset_data RENAME INDEX recordset_data_record_data_fulltex_index TO recordset_data_record_data_fulltext_index
