# 초기화/마이그레이션 SQL 모음 (교육등록 신청 API 반영 세션)

## 1. `training_request` 테이블 — DDL은 별도 파일에 이미 작성됨

FO Training Request(비정기 교육 신청, Step1~4) 저장용. Entity: `com.ge.bo.entity.TrainingRequest`, 진입 API: `POST /api/v1/fo/training/requests`(`FoTrainingController`).

local은 `ddl-auto: update`로 자동 생성되지만, dev/prod(`ddl-auto: validate`)는 `scripts/training_request.sql`을 그대로 실행할 것 — `CREATE TABLE IF NOT EXISTS training_request`(Step1~4 전 컬럼 + 감사 컬럼 2개) + 인덱스 2개(`email`, `created_at`) + 테이블/컬럼 코멘트 포함.
