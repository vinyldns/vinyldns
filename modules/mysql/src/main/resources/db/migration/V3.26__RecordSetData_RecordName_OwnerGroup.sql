CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset_data ADD COLUMN record_name VARCHAR(256) AFTER zone_id;
CREATE INDEX recordset_data_record_name_index ON recordset_data (record_name);

ALTER TABLE recordset_data ADD COLUMN owner_group_id CHAR(36) AFTER ip;
CREATE INDEX recordset_data_owner_group_id_index ON recordset_data (owner_group_id);

/*
Drop recordset_data_fdqn_fulltext_index index from recordset_data table
*/
ALTER TABLE recordset_data DROP INDEX recordset_data_fdqn_fulltext_index;

/*
Renaming for fulltext index record_data
*/
ALTER TABLE recordset_data DROP INDEX recordset_data_record_data_fulltex_index;
CREATE FULLTEXT INDEX recordset_data_recorddata_fulltext_index ON recordset_data (record_data);

/*
Drop data column from recordset_data table
*/
ALTER TABLE recordset_data DROP COLUMN data;

