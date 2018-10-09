CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Message Queue table
*/
CREATE TABLE message_queue (
  id CHAR(36) NOT NULL,
  message_type TINYINT,
  in_flight BIT(1),
  data BLOB NOT NULL,
  created DATETIME NOT NULL,
  updated DATETIME NOT NULL,
  timeout_seconds INT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  INDEX updated_index (updated),
  INDEX inflight_index (in_flight),
  INDEX timeout_index (timeout_seconds)
);
