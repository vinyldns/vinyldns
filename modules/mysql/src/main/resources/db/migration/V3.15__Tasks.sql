CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE task (
  name VARCHAR(255) NOT NULL,
  in_flight BIT(1) NOT NULL,
  created DATETIME NOT NULL,
  updated DATETIME,
  PRIMARY KEY (name),
  INDEX name_index (name),
  INDEX in_flight_index (in_flight),
  INDEX updated_index (updated)
);

INSERT IGNORE INTO task(name, in_flight, created, updated)
VALUES ("user_sync", 0, NOW(), NULL);
