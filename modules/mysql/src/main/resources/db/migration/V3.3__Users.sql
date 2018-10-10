CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the User table
*/
CREATE TABLE user (
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(256) NOT NULL,
    created_timestamp BIGINT(13) NOT NULL,
    access_key VARCHAR(256) NOT NULL,
    secret_key VARCHAR(256) NOT NULL,
    is_super BIT,
    lock_status VARCHAR(9),
    first_name VARCHAR(256),
    last_name VARCHAR(256),
    email VARCHAR(256),
    PRIMARY KEY (user_id),
    INDEX access_key_index (access_key),
    INDEX user_name_index (user_name)
);
