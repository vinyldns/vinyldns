CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Removing recordSet blob hash column and index in recordset table as its been no longer use
*/

ALTER TABLE recordset DROP COLUMN data_hash;
