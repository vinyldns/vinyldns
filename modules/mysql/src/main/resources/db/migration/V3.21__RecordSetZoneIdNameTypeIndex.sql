CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE recordset DROP INDEX zone_id_name_index;
CREATE UNIQUE INDEX zone_id_name_type_index on recordset (zone_id, name, type);
