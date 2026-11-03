CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

ALTER TABLE batch_change
ADD INDEX batch_change_owner_group_id_created_time_index
    (owner_group_id, created_time DESC),
ADD INDEX batch_change_owner_group_id_approval_status_created_time_index
    (owner_group_id, approval_status, created_time DESC);