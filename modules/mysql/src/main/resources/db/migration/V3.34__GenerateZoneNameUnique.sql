CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE generate_zone DROP INDEX generate_zone_name_index;
CREATE UNIQUE INDEX generate_zone_name_index ON generate_zone (name);
