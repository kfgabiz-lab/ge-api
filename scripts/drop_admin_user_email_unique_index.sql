-- 관리자 인증 기준을 email → employee_id(사번)로 전환하면서 email의 unique 제약이 더 이상
-- 유효하지 않음 (AdminUser.email 엔티티 nullable/unique 제약 제거와 짝을 이루는 DB 반영분).
-- employee_id는 add_soft_delete_partial_unique_indexes.sql에서 이미 admin_user_employee_id_key로
-- unique 처리돼 있으므로 별도 추가 작업 불필요.
BEGIN;

ALTER TABLE admin_user DROP CONSTRAINT IF EXISTS admin_user_email_key;
DROP INDEX IF EXISTS admin_user_email_key;

COMMIT;
