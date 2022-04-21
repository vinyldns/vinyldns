CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

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



