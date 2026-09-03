-- ============================================================
-- 관리자 홈페이지별 역할(admin_user_site.role_code) 추가
-- 한 관리자가 사이트마다 다른 역할을 가질 수 있도록 매핑 테이블에 역할 컬럼을 둔다.
-- role_code가 NULL이면 그 관리자의 전역 역할(admin_user.role_code)로 폴백되므로,
-- 예약 역할(SUPER_ADMIN) 및 시스템 역할(role.is_system = true)은 backfill 대상에서 제외한다.
-- 전역 역할이 강등돼도 매핑 행에 옛 상위 역할이 박제되어 남지 않도록 하기 위함이다.
-- 재실행 시 이미 잘못 채워진 값도 같은 조건으로 NULL 정리된다(멱등).
-- (developer 프로파일은 ddl-auto=update로 컬럼이 자동 생성되므로 UPDATE만 실행해도 된다)
-- 실행 전 반드시 백업 권장
-- ============================================================

ALTER TABLE admin_user_site
    ADD COLUMN IF NOT EXISTS role_code VARCHAR(20);

UPDATE admin_user_site aus
   SET role_code = au.role_code
  FROM admin_user au
 WHERE au.id = aus.admin_user_id
   AND aus.role_code IS NULL
   AND au.role_code IS NOT NULL
   AND au.role_code <> 'SUPER_ADMIN'
   AND NOT EXISTS (
       SELECT 1
         FROM role r
        WHERE r.code = au.role_code
          AND r.is_system = true
   );

UPDATE admin_user_site aus
   SET role_code = NULL
 WHERE aus.role_code IS NOT NULL
   AND (
       aus.role_code = 'SUPER_ADMIN'
       OR EXISTS (
           SELECT 1
             FROM role r
            WHERE r.code = aus.role_code
              AND r.is_system = true
       )
   );

CREATE INDEX IF NOT EXISTS admin_user_site_role_code_idx ON admin_user_site (role_code);
