CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change ADD approval_status TINYINT NULL;
ALTER TABLE batch_change ADD reviewer_id CHAR(36) NULL;
ALTER TABLE batch_change ADD review_comment VARCHAR(1024) NULL;
ALTER TABLE batch_change ADD review_timestamp DATETIME NULL;
CREATE INDEX approval_status_index ON batch_change (approval_status);
