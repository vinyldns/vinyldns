CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE INDEX batch_change_created_time_index ON batch_change (created_time DESC);