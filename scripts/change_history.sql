 CREATE INDEX IF NOT EXISTS idx_page_data_json_gin
      ON page_data USING GIN (data_json);