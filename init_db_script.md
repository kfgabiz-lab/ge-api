# 초기화/마이그레이션 SQL 모음 (Training Request Step4 VFD 공통코드 전환 세션)

FO `services/request-for-training` Step4에서 VFD 제품 선택 시 조건부로 노출되는 체크박스 3그룹(직책/참여영역/VFD 이해도 후속주제)을 하드코딩 배열 → 공통코드(`code_group`/`code_detail`) 방식으로 전환.

local은 BO 관리자 화면과 동일한 REST API(`POST /api/v1/message-resources`, `POST /api/v1/codes`, `POST /api/v1/codes/{groupId}/details`)로 이미 등록 완료. dev/prod는 아래 SQL을 **반드시 순서대로** 실행할 것.

## 1. `code_detail.name` 컬럼 길이 확장 (선행 필수)

신규 옵션 중 "Unit troubleshooting (to determine damaged components)"(54자), "Motor technology (construction, theory of operation)"(52자), "Drive technology (construction, theory of operation)"(52자)가 기존 `VARCHAR(50)`을 초과한다. 이 ALTER를 먼저 실행하지 않으면 3번 INSERT에서 DB 무결성 오류(`value too long for type character varying(50)`)가 발생한다.

```sql
ALTER TABLE code_detail ALTER COLUMN name TYPE VARCHAR(100);
```

local은 `ddl-auto: update`로도 컬럼 길이 축소/확장이 반영되지 않으므로 dev/prod 전부 수동 실행 필요.

**실행 순서: ALTER → bo-api 재기동 → 2번/3번 INSERT.** 순서를 바꾸면 안 된다.

## 2. `message_resource` 다국어 키 20건 등록

prefix `training.label.*`, `resource_type`은 전부 `WORD`. ko=en 동일(번역하지 않고 영문 원문을 양쪽에 그대로 입력하기로 결정함).

```sql
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at) VALUES
('training.label.eiTechnicianMaintenance', 'E & I Technician and/or Maintenance', 'E & I Technician and/or Maintenance', true, 'WORD', 'system', now(), 'system', now()),
('training.label.mechanic', 'Mechanic', 'Mechanic', true, 'WORD', 'system', now(), 'system', now()),
('training.label.electricalEngineer', 'Electrical Engineer', 'Electrical Engineer', true, 'WORD', 'system', now(), 'system', now()),
('training.label.salesEngineer', 'Sales Engineer', 'Sales Engineer', true, 'WORD', 'system', now(), 'system', now()),
('training.label.insideTech', 'Inside Tech', 'Inside Tech', true, 'WORD', 'system', now(), 'system', now()),
('training.label.fieldServiceEngineer', 'Field Service Engineer', 'Field Service Engineer', true, 'WORD', 'system', now(), 'system', now()),
('training.label.jobTitleOther', 'Other', 'Other', true, 'WORD', 'system', now(), 'system', now()),
('training.label.installationStartup', 'Installation, Start-up', 'Installation, Start-up', true, 'WORD', 'system', now(), 'system', now()),
('training.label.integrationParameterSetup', 'Integration and Parameter setup', 'Integration and Parameter setup', true, 'WORD', 'system', now(), 'system', now()),
('training.label.fieldTroubleshooting', 'Field Troubleshooting (understanding fault codes)', 'Field Troubleshooting (understanding fault codes)', true, 'WORD', 'system', now(), 'system', now()),
('training.label.salesExplanationProduct', 'Sales and Explanation of the Product', 'Sales and Explanation of the Product', true, 'WORD', 'system', now(), 'system', now()),
('training.label.serialCommunications', 'Serial communications', 'Serial communications', true, 'WORD', 'system', now(), 'system', now()),
('training.label.unitTroubleshooting', 'Unit troubleshooting (to determine damaged components)', 'Unit troubleshooting (to determine damaged components)', true, 'WORD', 'system', now(), 'system', now()),
('training.label.involvementOther', 'Other', 'Other', true, 'WORD', 'system', now(), 'system', now()),
('training.label.motorTechnology', 'Motor technology (construction, theory of operation)', 'Motor technology (construction, theory of operation)', true, 'WORD', 'system', now(), 'system', now()),
('training.label.driveTechnology', 'Drive technology (construction, theory of operation)', 'Drive technology (construction, theory of operation)', true, 'WORD', 'system', now(), 'system', now()),
('training.label.driveViewSoftware', 'DriveView Software', 'DriveView Software', true, 'WORD', 'system', now(), 'system', now()),
('training.label.pidApplications', 'PID Applications', 'PID Applications', true, 'WORD', 'system', now(), 'system', now()),
('training.label.autoTuning', 'Auto-Tuning', 'Auto-Tuning', true, 'WORD', 'system', now(), 'system', now()),
('training.label.otherTopics', 'Other Topics', 'Other Topics', true, 'WORD', 'system', now(), 'system', now());
```

> 그룹명(`code_group.group_name_msg_key`)은 신규 키가 아니라 기존 키를 재사용한다 — `training.label.studentJobTitle` / `training.label.areaOfInvolvement` / `training.label.studentProductKnowledgeLevel`. 이미 존재하므로 여기서 등록하지 않음.

## 3. `code_group` 신규 그룹 3건 등록

```sql
INSERT INTO code_group (group_code, group_name, group_name_msg_key, is_active, created_by, created_at, updated_by, updated_at) VALUES
('TRAININGJOBTITLE', '교육생 직위', 'training.label.studentJobTitle', true, 'system', now(), 'system', now()),
('TRAININGJOIN', '관련 분야', 'training.label.areaOfInvolvement', true, 'system', now(), 'system', now()),
('TRAININGVFD', '교육생의 제품 이해도', 'training.label.studentProductKnowledgeLevel', true, 'system', now(), 'system', now());
```

## 4. `code_detail` 코드 상세 20건 등록

`group_id`는 환경마다 값이 다를 수 있어 `group_code` 서브쿼리로 조회한다.

```sql
INSERT INTO code_detail (group_id, code, name, name_msg_key, sort_order, is_active, created_by, created_at, updated_by, updated_at)
SELECT g.id, v.code, v.name, v.name_msg_key, v.sort_order, true, 'system', now(), 'system', now()
FROM code_group g
JOIN (VALUES
  ('TRAININGJOBTITLE', '01', 'E & I Technician and/or Maintenance', 'training.label.eiTechnicianMaintenance', 1),
  ('TRAININGJOBTITLE', '02', 'Mechanic', 'training.label.mechanic', 2),
  ('TRAININGJOBTITLE', '03', 'Electrical Engineer', 'training.label.electricalEngineer', 3),
  ('TRAININGJOBTITLE', '04', 'Sales Engineer', 'training.label.salesEngineer', 4),
  ('TRAININGJOBTITLE', '05', 'Inside Tech', 'training.label.insideTech', 5),
  ('TRAININGJOBTITLE', '06', 'Field Service Engineer', 'training.label.fieldServiceEngineer', 6),
  ('TRAININGJOBTITLE', '07', 'Other', 'training.label.jobTitleOther', 7),
  ('TRAININGJOIN', '01', 'Installation, Start-up', 'training.label.installationStartup', 1),
  ('TRAININGJOIN', '02', 'Integration and Parameter setup', 'training.label.integrationParameterSetup', 2),
  ('TRAININGJOIN', '03', 'Field Troubleshooting (understanding fault codes)', 'training.label.fieldTroubleshooting', 3),
  ('TRAININGJOIN', '04', 'Sales and Explanation of the Product', 'training.label.salesExplanationProduct', 4),
  ('TRAININGJOIN', '05', 'Serial communications', 'training.label.serialCommunications', 5),
  ('TRAININGJOIN', '06', 'Unit troubleshooting (to determine damaged components)', 'training.label.unitTroubleshooting', 6),
  ('TRAININGJOIN', '07', 'Other', 'training.label.involvementOther', 7),
  ('TRAININGVFD', '01', 'Motor technology (construction, theory of operation)', 'training.label.motorTechnology', 1),
  ('TRAININGVFD', '02', 'Drive technology (construction, theory of operation)', 'training.label.driveTechnology', 2),
  ('TRAININGVFD', '03', 'DriveView Software', 'training.label.driveViewSoftware', 3),
  ('TRAININGVFD', '04', 'PID Applications', 'training.label.pidApplications', 4),
  ('TRAININGVFD', '05', 'Auto-Tuning', 'training.label.autoTuning', 5),
  ('TRAININGVFD', '06', 'Other Topics', 'training.label.otherTopics', 6)
) AS v(group_code, code, name, name_msg_key, sort_order)
  ON v.group_code = g.group_code;
```

## 5. 이번 세션에서 건드리지 않은 것 (참고)

- `TRAININGSCHEDULETYPE`/`TRAININGFEETYPE` 그룹코드 리네임 — 스코프 제외(`page_template.config_json` 5건, `slug_entity_field.code_group_code` 1건에서 실참조 중이라 리네임 시 다른 화면이 깨짐)
- `training_request` 테이블 스키마 — 변경 없음(Step4 3개 컬럼은 `TEXT[]`로 라벨 텍스트 그대로 저장, 코드값 전환 안 함)
