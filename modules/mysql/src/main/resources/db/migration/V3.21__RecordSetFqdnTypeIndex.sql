CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE UNIQUE INDEX fqdn_type_index ON recordset (fqdn, type);
