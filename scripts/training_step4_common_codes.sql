-- TRAININGJOBTITLE(group_id=64) / TRAININGJOIN(group_id=63) code_detail 등록
-- message_resource.training.label.* 는 이미 전부 등록되어 있음 (id 1446~1459) — 여기서는 code_detail만 채움
-- FO 노출값은 name_msg_key -> message_resource.en 으로 치환되어 나감 (CodeService.java:50-56), name 컬럼은 관리자 화면 fallback용
-- 재실행해도 안전하도록 (group_id, code) 충돌 시 스킵

INSERT INTO code_detail (group_id, code, name, name_msg_key, sort_order, is_active, created_by, created_at, updated_by, updated_at)
VALUES
  (64, '01', 'E & I Technician and/or Maintenance', 'training.label.eiTechnicianMaintenance', 1, true, 'comlbg', now(), 'comlbg', now()),
  (64, '02', 'Mechanic',                             'training.label.mechanic',               2, true, 'comlbg', now(), 'comlbg', now()),
  (64, '03', 'Electrical Engineer',                  'training.label.electricalEngineer',     3, true, 'comlbg', now(), 'comlbg', now()),
  (64, '04', 'Sales Engineer',                        'training.label.salesEngineer',          4, true, 'comlbg', now(), 'comlbg', now()),
  (64, '05', 'Inside Tech',                           'training.label.insideTech',             5, true, 'comlbg', now(), 'comlbg', now()),
  (64, '06', 'Field Service Engineer',                'training.label.fieldServiceEngineer',   6, true, 'comlbg', now(), 'comlbg', now()),
  (64, '07', 'Other',                                 'training.label.jobTitleOther',          7, true, 'comlbg', now(), 'comlbg', now())
ON CONFLICT (group_id, code) DO NOTHING;

INSERT INTO code_detail (group_id, code, name, name_msg_key, sort_order, is_active, created_by, created_at, updated_by, updated_at)
VALUES
  (63, '01', 'Installation, Start-up',                                   'training.label.installationStartup',        1, true, 'comlbg', now(), 'comlbg', now()),
  (63, '02', 'Integration and Parameter setup',                          'training.label.integrationParameterSetup',  2, true, 'comlbg', now(), 'comlbg', now()),
  (63, '03', 'Field Troubleshooting (understanding fault codes)',        'training.label.fieldTroubleshooting',       3, true, 'comlbg', now(), 'comlbg', now()),
  (63, '04', 'Sales and Explanation of the Product',                     'training.label.salesExplanationProduct',    4, true, 'comlbg', now(), 'comlbg', now()),
  (63, '05', 'Serial communications',                                    'training.label.serialCommunications',       5, true, 'comlbg', now(), 'comlbg', now()),
  (63, '06', 'Unit troubleshooting (to determine damaged components)',   'training.label.unitTroubleshooting',        6, true, 'comlbg', now(), 'comlbg', now()),
  (63, '07', 'Other',                                                    'training.label.involvementOther',           7, true, 'comlbg', now(), 'comlbg', now())
ON CONFLICT (group_id, code) DO NOTHING;

-- 확인용
SELECT g.group_code, COUNT(d.id) AS detail_count
FROM code_group g
LEFT JOIN code_detail d ON d.group_id = g.id
WHERE g.group_code IN ('TRAININGJOBTITLE','TRAININGJOIN')
GROUP BY g.group_code;
