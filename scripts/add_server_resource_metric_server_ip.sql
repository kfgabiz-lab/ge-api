ALTER TABLE server_resource_metric
    ADD COLUMN IF NOT EXISTS server_ip VARCHAR(45);

UPDATE server_resource_metric
    SET server_ip = '10.13.123.86'
    WHERE server_ip IS NULL;

ALTER TABLE server_resource_metric
    ALTER COLUMN server_ip SET NOT NULL;
