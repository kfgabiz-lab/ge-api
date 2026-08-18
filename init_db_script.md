# init_db_script

## (기존) 메뉴 SEO Meta Title/Description 추가

```sql
ALTER TABLE menu ADD COLUMN meta_title VARCHAR(60); ALTER TABLE menu ADD COLUMN meta
```

## 2026-08-18 — menu.url 정정 (Training URL 개편 누락분)

`fo/next.config.ts`의 Training URL 개편(구 `/services/{variant}-training` → 신규
`/services/training/{variant}`) 당시 FE 라우트와 `next.config.ts` 리다이렉트만
갱신되고, `menu.url`은 옛 URL 그대로 남아있어 FO 페이지의 SEO 메타(title/description)
조회가 매번 빈 값으로 실패하던 문제 수정.

```sql
UPDATE menu SET url = '/services/training/sales' WHERE id = 223;
UPDATE menu SET url = '/services/training/engineering' WHERE id = 86;
UPDATE menu SET url = '/services/training/service' WHERE id = 224;
UPDATE menu SET url = '/services/training/request' WHERE id = 178;
```
