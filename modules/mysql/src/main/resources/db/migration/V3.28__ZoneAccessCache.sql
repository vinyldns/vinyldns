CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE zone_access_cache (
  accessor_id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL
  );

