-- FO 통합검색 Pages 탭 + BO 검색관리 menu 연동: DB 적용 스크립트 (통합)

-- ── 1) menu_id FK 신설 (필수 — 이게 없으면 bo-api 기동 실패) ──
ALTER TABLE search_manage
  ADD COLUMN IF NOT EXISTS menu_id BIGINT REFERENCES menu(id) ON DELETE SET NULL;

-- ── 2) 기존 20건 백필 — 지금까지 쓰던 매칭 규칙(url 일치, 중복 시 id 최솟값) ──
UPDATE search_manage sm
SET menu_id = mn.id
FROM (
  SELECT DISTINCT ON (url) id, url
  FROM menu
  WHERE is_deleted = false
  ORDER BY url, id ASC
) mn
WHERE sm.url = mn.url
  AND sm.is_deleted = false
  AND sm.menu_id IS NULL;

-- ── 3) pg_trgm 확장 + 인덱스 (성능 개선용, 정확성에는 영향 없음) ──
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_search_manage_text_text_trgm
  ON search_manage_text USING gin (text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_search_manage_text_title_trgm
  ON search_manage_text USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_menu_meta_title_trgm
  ON menu USING gin (meta_title gin_trgm_ops);

-- 확인용
-- SELECT id, url, menu_id FROM search_manage WHERE is_deleted = false ORDER BY id;
