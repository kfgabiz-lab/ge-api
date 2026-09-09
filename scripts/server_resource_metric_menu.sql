INSERT INTO menu (
    name, url, parent_id, menu_type, site_id, is_system, is_visible, is_deleted, sort_order, icon,
    created_by, updated_by, created_at, updated_at
)
SELECT
    '서버 리소스 모니터링', '/admin/system/monitor', 40, 'BO', 1, true, true, false, 7, '',
    'comlbg', 'comlbg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM menu WHERE url = '/admin/system/monitor'
);
