-- login_log.login_email을 더 이상 채우지 않고 항상 null로 기록하도록 코드 변경
-- (LoginLogService.saveAsync) — DB NOT NULL 제약도 함께 제거.
ALTER TABLE login_log ALTER COLUMN login_email DROP NOT NULL;
