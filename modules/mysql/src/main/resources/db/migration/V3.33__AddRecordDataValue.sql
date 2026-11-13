CREATE SCHEMA IF NOT EXISTS ${dbName};
 
USE ${dbName};
 
ALTER TABLE recordset_data
ADD COLUMN record_data_value VARCHAR(1000) GENERATED ALWAYS AS (
  CASE
    WHEN type IN ('TXT', 'SPF') THEN NULL
    ELSE TRIM(BOTH '"' FROM REGEXP_SUBSTR(record_data, '"[^"]*"'))
  END
) VIRTUAL,
ADD INDEX idx_record_data_value (record_data_value);