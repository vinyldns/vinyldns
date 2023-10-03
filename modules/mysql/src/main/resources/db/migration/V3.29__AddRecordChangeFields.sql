CREATE SCHEMA IF NOT EXISTS ${dbName};

USE ${dbName};

DELIMITER $$

CREATE PROCEDURE AddColumnIfNotExists(IN columnName VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'record_change'
        AND column_name = columnName
    ) THEN
        SET @sql = CONCAT('ALTER TABLE record_change ADD COLUMN ', columnName, ' VARCHAR(255) NOT NULL');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL AddColumnIfNotExists('record_type');
CALL AddColumnIfNotExists('fqdn');

CREATE INDEX fqdn_index ON record_change (fqdn);
CREATE INDEX record_type_index ON record_change (record_type);
