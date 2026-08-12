BEGIN;

ALTER TABLE role DROP CONSTRAINT IF EXISTS ukc36say97xydpmgigg38qv5l2p;
CREATE UNIQUE INDEX IF NOT EXISTS ukc36say97xydpmgigg38qv5l2p
    ON role (code)
    WHERE is_deleted = false;

ALTER TABLE code_group DROP CONSTRAINT IF EXISTS ukanywi85ga0c2v0jc8q8uekwm5;
CREATE UNIQUE INDEX IF NOT EXISTS ukanywi85ga0c2v0jc8q8uekwm5
    ON code_group (group_code)
    WHERE is_deleted = false;

ALTER TABLE code_detail DROP CONSTRAINT IF EXISTS uq_code_detail_group_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_code_detail_group_code
    ON code_detail (group_id, code)
    WHERE is_deleted = false;

ALTER TABLE message_resource DROP CONSTRAINT IF EXISTS uk463pfu33wvgvudeqxnyxynqes;
CREATE UNIQUE INDEX IF NOT EXISTS uk463pfu33wvgvudeqxnyxynqes
    ON message_resource ("key")
    WHERE is_deleted = false;

ALTER TABLE admin_user DROP CONSTRAINT IF EXISTS admin_user_email_key;
CREATE UNIQUE INDEX IF NOT EXISTS admin_user_email_key
    ON admin_user (email)
    WHERE is_deleted = false;

ALTER TABLE admin_user DROP CONSTRAINT IF EXISTS admin_user_employee_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS admin_user_employee_id_key
    ON admin_user (employee_id)
    WHERE is_deleted = false;

COMMIT;
