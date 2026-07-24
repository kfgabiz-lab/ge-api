# 초기화/마이그레이션 SQL 모음 (사이트별 timezone 설정 작업)

이번 세션(사이트별 시간대 설정 기능 개발)에서 DB에 직접 실행한 SQL과, 아직 실행 전인 SQL을 정리한다.

---

## 1. 이번 세션에서 이미 실행한 SQL

### 1-1. `site.label.timezone` 메시지키 등록 (사용자가 직접 실행)

사이트관리 화면의 timezone select 필드 라벨이 번역 안 되던 문제(`site.label.timezone` 키 미등록) 해결용.

```sql
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES ('site.label.timezone', '시간대', 'Timezone', true, 'WORD', 'system', now(), 'system', now());
```

### 1-2. 대표 사이트(site 1/3) `name_msg_key` 마이그레이션 (실행 완료)

`site.name_msg_key`가 null이라 사이트관리 화면 저장(필수검증 실패)이 막혀 있던 site 1(북미홈페이지)·site 3(테스트홈페이지)를 `docs/db/site/db_site_msg_key.md`의 마이그레이션 절차대로 복구.

```sql
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES ('site.1.name', '북미홈페이지', 'North America Site', true, 'WORD', 'system', now(), 'system', now());

INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES ('site.3.name', '테스트홈페이지', 'Test Site', true, 'WORD', 'system', now(), 'system', now());

UPDATE site SET name_msg_key = 'site.1.name' WHERE id = 1;
UPDATE site SET name_msg_key = 'site.3.name' WHERE id = 3;
```

### 1-3. `site.timezone` 컬럼 추가 (`ddl-auto: update`로 자동 반영, 참고용)

로컬은 Hibernate `ddl-auto: update`가 자동 실행했으나, dev/prod(`ddl-auto: validate`)는 수동 실행 필요. (`docs/db/site/db_site.md` 3-5 참고)

```sql
ALTER TABLE site ADD COLUMN timezone TEXT;
```

---

## 2. 새로 실행해야 하는 SQL (아직 미실행 — 승인 대기)

### 2-1. TIMESTAMP → TIMESTAMPTZ 마이그레이션 (핵심)

**배경**: `created_at`/`updated_at`이 사이트 timezone을 반영하려면 컬럼이 `TIMESTAMPTZ`여야 한다. 그런데 아래 15개 테이블은 여전히 `TIMESTAMP WITHOUT TIME ZONE`이라, BE 코드(`OffsetDateTime.now(zone)`)가 어떤 zone을 구하든 Hibernate/JDBC가 저장 시 무조건 UTC로 정규화해버려 사이트별 timezone이 반영되지 않는다.

이 마이그레이션은 이미 `docs/db/timestamptz/db_timestamptz.md`에 설계되어 있었으나(BE 엔티티/DTO는 이미 `OffsetDateTime`으로 전환 완료된 상태) **DB 컬럼 변경(ALTER)만 실행되지 않고 남아있던 것**이다. 아래는 그 문서의 SQL 그대로다.

```sql
-- ============================================================
-- TIMESTAMPTZ 마이그레이션
-- 기존 데이터: KST(Asia/Seoul, UTC+9) 기준으로 저장되어 있으므로
--             AT TIME ZONE 'Asia/Seoul' 으로 타임존 정보 부여
-- ============================================================

-- admin_user
ALTER TABLE admin_user
  ALTER COLUMN last_login_at TYPE TIMESTAMPTZ USING last_login_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN locked_until  TYPE TIMESTAMPTZ USING locked_until  AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at    TYPE TIMESTAMPTZ USING updated_at    AT TIME ZONE 'Asia/Seoul';

-- role
ALTER TABLE role
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- role_menu
ALTER TABLE role_menu
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul';

-- menu
ALTER TABLE menu
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- site
ALTER TABLE site
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- admin_user_site
ALTER TABLE admin_user_site
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul';

-- code_group
ALTER TABLE code_group
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- code_detail
ALTER TABLE code_detail
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- page_template
ALTER TABLE page_template
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- page_data
ALTER TABLE page_data
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- page_file
ALTER TABLE page_file
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul';

-- tsx_generation
ALTER TABLE tsx_generation
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul';

-- slug_registry
ALTER TABLE slug_registry
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- api_info
ALTER TABLE api_info
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';

-- message_resource
ALTER TABLE message_resource
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Seoul',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'Asia/Seoul';
```

> `error_log`는 이미 `timestamptz`로 마이그레이션 완료된 상태라 이번 대상에서 제외했다(원본 문서 목록엔 포함돼 있었음).

### 2-2. 주의 — 오늘 세션 중 이미 UTC로 저장된 행 (예외 처리 필요)

위 SQL은 "기존 데이터는 전부 KST 기준"이라는 전제로 `AT TIME ZONE 'Asia/Seoul'`을 일괄 적용한다. 그런데 오늘 STEP2-BE(JpaConfig) 수정 배포 **이후** JPA `save()`로 갱신된 행은 이미 UTC 기준으로 저장돼 있어 이 가정이 깨진다. 확인된 예:

- `site` id=1 — 오늘 QA 캐시무효화 검증 중 여러 번 PATCH됨(마지막 `updated_at` raw `2026-07-24 05:41:52` = UTC 기준, KST 아님)

일괄 마이그레이션 실행 전, 이런 예외 행이 있는지 다시 확인하고 필요하면 개별 `UPDATE`로 보정하거나(로컬 개발 DB라 리스크가 낮으므로) 그대로 두고 넘어갈지 결정이 필요하다.

### 2-3. 롤백 SQL (참고, `docs/db/timestamptz/db_timestamptz.md` 원본 그대로)

```sql
-- TIMESTAMPTZ → TIMESTAMP 롤백
-- 주의: 롤백 시 타임존 정보가 손실됨 (UTC 기준으로 변환된 후 타임존 제거)

ALTER TABLE admin_user
  ALTER COLUMN last_login_at TYPE TIMESTAMP USING last_login_at AT TIME ZONE 'UTC',
  ALTER COLUMN locked_until  TYPE TIMESTAMP USING locked_until  AT TIME ZONE 'UTC',
  ALTER COLUMN created_at    TYPE TIMESTAMP USING created_at    AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at    TYPE TIMESTAMP USING updated_at    AT TIME ZONE 'UTC';

ALTER TABLE role
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE role_menu
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';

ALTER TABLE menu
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE site
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE admin_user_site
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';

ALTER TABLE code_group
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE code_detail
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE page_template
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE page_data
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE page_file
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';

ALTER TABLE tsx_generation
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';

ALTER TABLE slug_registry
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE api_info
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE message_resource
  ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';
```

---

## 3. 마이그레이션 이후 별도로 되돌려야 하는 코드 (참고)

TIMESTAMPTZ 마이그레이션이 실행되면 `PageDataService.java`의 `create()`/`update()`/`patchField()`에 오늘 임시로 넣은 `OffsetDateTime.now(zone)` → UTC 정규화 회피용 우회 로직이 더 이상 필요 없어진다(컬럼이 `timestamptz`가 되면 오프셋이 그대로 보존되므로). 마이그레이션 실행 후 이 부분 재검토 필요.


-- 적용: SW 4건에만 page_type 추가
UPDATE page_data
SET data_json = data_json || '{"page_type": "SW"}'::jsonb
WHERE data_slug = 'product-data'
  AND id IN (1727, 1728, 1729, 1730);

-- 확인
SELECT id, data_json->>'page_type' AS page_type, data_json->'product'->>'product_name' AS name
FROM page_data
WHERE data_slug = 'product-data'
  AND id IN (1727, 1728, 1729, 1730);

되돌릴 때는 아래로 키만 제거하면 됩니다(다른 필드 영향 없음):
UPDATE page_data
SET data_json = data_json - 'page_type'
WHERE data_slug = 'product-data'
  AND id IN (1727, 1728, 1729, 1730);