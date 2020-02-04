CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

CREATE TABLE stats (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  count BIGINT NOT NULL,
  created DATETIME NOT NULL,
  PRIMARY KEY (id),
  INDEX stats_name_index (name)
);
