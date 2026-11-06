CREATE TABLE queue_command_execution (
    command_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(3) NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (command_key)
) ENGINE=InnoDB;
