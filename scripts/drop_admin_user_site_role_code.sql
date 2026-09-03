-- ============================================================
-- 관리자 홈페이지별 역할(admin_user_site.role_code) 제거
-- add_admin_user_site_role_code.sql 로 추가했던 컬럼/인덱스를 되돌린다.
-- 사이트별 역할 오버라이드 방식을 폐기하고, 역할(role)의 메뉴 권한을
-- 사이트별로 다르게 적용하는 방식으로 전환했기 때문이다.
-- 엔티티에서 필드를 제거해도 ddl-auto=update 는 컬럼을 자동 삭제하지 않으므로
-- 컬럼 자체를 지우려면 이 스크립트를 수동으로 실행해야 한다.
-- 실행 전 반드시 백업 권장
-- ============================================================

DROP INDEX IF EXISTS admin_user_site_role_code_idx;

ALTER TABLE admin_user_site
    DROP COLUMN IF EXISTS role_code;
