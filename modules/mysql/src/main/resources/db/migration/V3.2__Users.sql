CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

/*
Create the User table
*/
CREATE TABLE user (
    id CHAR(36) NOT NULL,
    user_name VARCHAR(256) NOT NULL,
    access_key VARCHAR(256) NOT NULL,
    data BLOB NOT NULL,
    PRIMARY KEY (id),
    INDEX access_key_index (access_key),
    INDEX user_name_index (user_name)
);
