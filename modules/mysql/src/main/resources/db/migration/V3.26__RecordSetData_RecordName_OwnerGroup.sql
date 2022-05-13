CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Drop data column from recordset_data table
*/
ALTER TABLE recordset_data DROP COLUMN data;



