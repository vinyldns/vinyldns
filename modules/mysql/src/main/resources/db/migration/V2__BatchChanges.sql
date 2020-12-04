CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the batch_change table. This table stores the metadata of a batch change.
It supports easy query by batch change ID, user_id, and combination of user_id & created_time.  
*/
CREATE TABLE batch_change (
  id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  user_name VARCHAR(45) NOT NULL,
  created_time DATETIME NOT NULL,
  comments VARCHAR(1024) NULL,
  PRIMARY KEY (id),
  INDEX batch_change_user_id_index  (user_id ASC),
  INDEX batch_change_user_id_created_time_index  (user_id ASC, created_time ASC));
 
/*
Create the single_change table. This table stores the single changes and associated them with batch change via foreign key.
It stores single change data as encoded protobuf in the data BLOB. Whenever any column in the table is updated, the data column must be updated too.
Just reading from the data column and decode the protobuf format can get all the data for a single change.
It also stores other IDs to associate with zone, record set and record set change. These IDs allow getting additional data from the dynamodb where they're stored.
*/ 
CREATE TABLE single_change (
  id CHAR(36) NOT NULL,
  seq_num SMALLINT NOT NULL,
  input_name VARCHAR(45) NOT NULL,
  change_type VARCHAR(20) NOT NULL,
  data BLOB NOT NULL,
  status VARCHAR(10) NOT NULL,
  batch_change_id CHAR(36) NOT NULL,
  record_set_change_id CHAR(36) NULL,
  record_set_id CHAR(36) NULL,
  zone_id CHAR(36) NOT NULL,
  PRIMARY KEY (id),
  INDEX batch_change_id_index (batch_change_id ASC),
  INDEX record_set_change_id_index (record_set_change_id ASC),
  CONSTRAINT fk_single_change_batch_change1
    FOREIGN KEY (batch_change_id)
    REFERENCES ${dbName}.batch_change (id)
    ON DELETE CASCADE);
 

