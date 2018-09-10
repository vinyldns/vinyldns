CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the Zone Change table.
Note: during dynamodb -> mysql migration, refactor zone change class to use `Datetime`
for  its`created` attribute instead of `Datetime.getmillis.toString`, so we can use the
mysql DATETIME type. This should be noted when adjusting the protobufs
*/
CREATE TABLE zone_change (
  change_id CHAR(36) NOT NULL,
  zone_id CHAR(36) NOT NULL,
  status DATETIME NOT NULL,
  data BLOB NOT NULL,
  created_time VARCHAR(20) NOT NULL,
  PRIMARY KEY (change_id)
);
