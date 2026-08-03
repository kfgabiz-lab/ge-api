CREATE INDEX idx_page_data_product_list_gin
  ON page_data USING gin ((data_json->'product_list') jsonb_path_ops)
  WHERE data_slug IN ('blog-data','press-data','articles-data');