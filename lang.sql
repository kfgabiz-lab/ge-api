-- ────────────── 공통 라벨 (site v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.label.domain',
 '도메인',
 'Domain',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.description',
 '설명',
 'Description',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.isActive',
 '사용여부',
 'Status',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.manage',
 '관리',
 'Manage',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.createdAt',
 '등록일',
 'Registered Date',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.field.optional',
 '선택 입력',
 'Optional',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 사이트 관리 신규 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('site.description',
 '홈페이지 기본 정보를 입력해 주세요.',
 'Please enter the basic site information.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.label.name',
 '홈페이지명',
 'Site Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('site.placeholder.name',
 '예: 북미홈페이지',
 'e.g. North America Site',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.placeholder.domain',
 '예: www.example.com',
 'e.g. www.example.com',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.btn.add',
 '홈페이지 추가',
 'Add Site',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('site.title.new',
 '홈페이지 등록',
 'Add Site',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.title.edit',
 '홈페이지 수정',
 'Edit Site',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 공통 추가 키 (메뉴 v2 공통화) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.label.type',
 '유형',
 'Type',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.type.folder',
 '폴더',
 'Folder',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.type.program',
 '프로그램',
 'Program',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.icon',
 '아이콘',
 'Icon',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.none',
 '없음',
 'None',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.sortOrder',
 '정렬순서',
 'Sort Order',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.visible',
 '노출여부',
 'Visibility',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.visible.show',
 '노출',
 'Show',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.visible.hide',
 '숨김',
 'Hide',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.delete',
 '삭제',
 'Delete',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.saving',
 '저장 중...',
 'Saving...',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.badge.parent',
 '상위',
 'Parent',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.badge.child',
 '하위',
 'Child',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.tab.bo',
 'BO',
 'BO',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.tab.fo',
 'FO',
 'FO',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── validation 공통 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('validation.name.required',
 '메뉴명을 선택해 주세요.',
 'Please select a menu name.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.url.xss',
 'XSS 문자가 포함되어 있습니다.',
 'Contains invalid XSS characters.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.url.slash',
 '/로 시작해야 합니다.',
 'Must start with /.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.url.double_slash',
 '//는 사용할 수 없습니다.',
 'Double slashes are not allowed.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.url.pattern',
 '유효하지 않은 URL 형식입니다.',
 'Invalid URL format.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.url.required',
 'URL을 입력해 주세요.',
 'Please enter a URL.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.sort.required',
 '정렬순서를 입력해 주세요.',
 'Please enter a sort order.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.sort.integer',
 '정수를 입력해 주세요.',
 'Please enter an integer.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.sort.min',
 '1 이상 입력해 주세요.',
 'Must be 1 or greater.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.sort.max',
 '999 이하로 입력해 주세요.',
 'Must be 999 or less.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 메뉴 관리 신규 키 (v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('menu.template.error',
 '페이지 목록을 불러오지 못했습니다.',
 'Failed to load page list.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.template.title',
 '페이지 연결',
 'Link Page',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.template.dropdown_title',
 '페이지 선택',
 'Select Page',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.template.empty',
 '등록된 페이지가 없습니다.',
 'No pages registered.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.notice.folder_type',
 '폴더 전환 시 URL이 초기화됩니다.',
 'URL will be cleared when switching to folder.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.label.name',
 '메뉴명',
 'Menu Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.placeholder.url',
 '/admin/settings/example',
 '/admin/settings/example',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.sort_error',
 '정렬 저장에 실패했습니다.',
 'Failed to save sort order.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.btn.add',
 '메뉴 추가',
 'Add Menu',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.btn.add_under',
 '{name} 하위에 추가',
 'Add under {name}',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.notice.depth3',
 '3depth 이상은 추가할 수 없습니다.',
 'Cannot add more than 3 levels deep.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.empty',
 '등록된 메뉴가 없습니다.',
 'No menus registered.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.created',
 '{name} 메뉴가 생성되었습니다.',
 'Menu {name} has been created.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.empty.title',
 '메뉴를 선택해 주세요.',
 'Please select a menu.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.empty.description',
 '왼쪽 트리에서 메뉴를 선택하면 상세 정보를 확인하고 수정할 수 있습니다.',
 'Select a menu from the tree on the left to view and edit its details.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.error.has_children',
 '하위 메뉴가 있어 변경할 수 없습니다.',
 'Cannot change type because it has child menus.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.no_change',
 '변경된 내용이 없습니다.',
 'No changes to save.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.updated',
 '저장되었습니다.',
 'Saved successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.save_error',
 '저장에 실패했습니다.',
 'Failed to save.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.confirm.delete',
 '{name} 메뉴를 삭제하겠습니까?',
 'Are you sure you want to delete {name}?',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.confirm.delete_children',
 '하위 메뉴도 함께 삭제됩니다.',
 'Child menus will also be deleted.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.deleted',
 '삭제되었습니다.',
 'Deleted successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.delete_error',
 '삭제에 실패했습니다.',
 'Failed to delete.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.title',
 '메뉴 상세',
 'Menu Details',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.badge.modified',
 '수정됨',
 'Modified',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('menu.confirm.folder_type',
 'URL을 초기화하겠습니까?',
 'URL will be cleared. Continue?',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('menu.role.error',
 '권한 저장에 실패했습니다.',
 'Failed to save permissions.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 공통 추가 키 (사이트/헤더 v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.status.active',
 '활성',
 'Active',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.status.inactive',
 '비활성',
 'Inactive',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.logout',
 '로그아웃',
 'Logout',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 사이트 관리 추가 키 (v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('site.confirm.delete',
 '홈페이지를 삭제하겠습니까?',
 'Are you sure you want to delete this site?',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.deleted',
 '삭제되었습니다.',
 'Deleted successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.load_error',
 '데이터를 불러오지 못했습니다.',
 'Failed to load data.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.created',
 '등록되었습니다.',
 'Created successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.updated',
 '수정되었습니다.',
 'Updated successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('site.selector.empty',
 '홈페이지 선택',
 'Select Site',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('site.selector.no_sites',
 '배정된 홈페이지가 없습니다.',
 'No sites assigned.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── validation 추가 키 (사이트/로그인 v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('validation.site.name.required',
 '홈페이지명을 선택해 주세요.',
 'Please select a site name.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.id.required',
 '아이디를 입력해 주세요.',
 'Please enter your ID.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.id.max',
 '아이디는 30자 이하로 입력해 주세요.',
 'ID must be 30 characters or less.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.id.pattern',
 '영문, 숫자, 마침표(.)만 입력해 주세요.',
 'Only letters and numbers are allowed.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.password.min',
 '비밀번호는 4자 이상 입력해 주세요.',
 'Password must be at least 4 characters.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 로그인 추가 키 (v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('login.error.no_server',
 '서버에 연결할 수 없습니다.',
 'Cannot connect to server.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('login.error.invalid',
 '아이디 또는 비밀번호가 올바르지 않습니다.',
 'Invalid ID or password.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('login.error.forbidden',
 '접근 권한이 없습니다.',
 'Access denied.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('login.error.server',
 '서버 오류가 발생했습니다.',
 'A server error occurred.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('login.error.unknown',
 '알 수 없는 오류가 발생했습니다.',
 'An unknown error occurred.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 메뉴 관리 구키 삭제 ──────────────
DELETE FROM message_resource WHERE "key" IN (
    'menu.template.picker.toast.error',
    'menu.template.picker.title',
    'menu.template.picker.dropdown.title',
    'menu.template.picker.loading',
    'menu.template.picker.empty',
    'menu.validation.url.xss',
    'menu.validation.url.slash',
    'menu.validation.url.double_slash',
    'menu.validation.url.pattern',
    'menu.validation.sort.required',
    'menu.validation.sort.integer',
    'menu.validation.sort.min',
    'menu.validation.sort.max',
    'menu.form.type.label',
    'menu.form.type.folder',
    'menu.form.type.program',
    'menu.form.type.folder.warning',
    'menu.form.name.label',
    'menu.form.url.placeholder',
    'menu.form.description.label',
    'menu.form.description.hint',
    'menu.form.icon.label',
    'menu.form.sort.label',
    'menu.form.visible.label',
    'menu.form.visible.show',
    'menu.form.visible.hide',
    'menu.validation.name.required',
    'menu.create.validation.url.required',
    'menu.create.folder.confirm',
    'menu.tree.btn.add',
    'menu.create.btn.cancel',
    'menu.create.btn.submitting',
    'menu.tree.btn.add_under',
    'menu.detail.empty.title',
    'menu.detail.empty.description',
    'menu.detail.folder.confirm',
    'menu.detail.program.error.has_children',
    'menu.detail.toast.save.no_change',
    'menu.detail.toast.save.success',
    'menu.detail.toast.save.error',
    'menu.detail.delete.confirm',
    'menu.detail.delete.confirm.children',
    'menu.detail.toast.delete.success',
    'menu.detail.toast.delete.error',
    'menu.detail.title',
    'menu.detail.badge.parent',
    'menu.detail.badge.child',
    'menu.detail.badge.modified',
    'menu.detail.btn.delete',
    'menu.detail.btn.saving',
    'menu.detail.btn.save',
    'menu.tree.toast.sort_error',
    'menu.tree.tab.bo',
    'menu.tree.tab.fo',
    'menu.tree.loading',
    'menu.tree.empty',
    'menu.tree.depth3.notice',
    'menu.create.toast.success',
    'menu.role.toast.error'
);

-- ────────────── 사이트/헤더/로그인 구키 삭제 ──────────────
DELETE FROM message_resource WHERE "key" IN (
    'common.active',
    'common.inactive',
    'common.word.logout',
    'site.toast.deleted',
    'site.toast.load_error',
    'site.toast.created',
    'site.toast.updated',
    'site.validation.name.required',
    'login.validation.id.required',
    'login.validation.id.max',
    'login.validation.id.pattern',
    'login.validation.password.min',
    'login.toast.error.no_server',
    'login.toast.error.invalid',
    'login.toast.error.forbidden',
    'login.toast.error.server',
    'login.toast.error.unknown'
);

-- ────────────── common.word.* → common.label.* 일괄 변경 ──────────────
-- message_resource 키 이름 변경 (logout은 별도 삭제 처리)
UPDATE message_resource
SET "key" = REPLACE("key", 'common.word.', 'common.label.')
WHERE "key" LIKE 'common.word.%'
  AND "key" <> 'common.word.logout';

-- menu 테이블 name_msg_key 동기화
UPDATE menu
SET name_msg_key = REPLACE(name_msg_key, 'common.word.', 'common.label.')
WHERE name_msg_key LIKE 'common.word.%';

-- site 테이블 name_msg_key 동기화
UPDATE site
SET name_msg_key = REPLACE(name_msg_key, 'common.word.', 'common.label.')
WHERE name_msg_key LIKE 'common.word.%';

-- ────────────── 중복 키 정리 (v2 통합) ──────────────

-- 1. common.deleted 신규 등록 (menu.deleted + site.deleted 통합)
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES
('common.deleted', '삭제되었습니다.', 'Deleted successfully.', true, 'SENTENCE', 'system', NOW(), 'system', NOW())
ON CONFLICT ("key") DO NOTHING;

-- 2. "메뉴 추가" 중복 키 삭제 (menu.btn.add 로 통합, 미사용 v1 키 제거)
DELETE FROM message_resource WHERE "key" IN (
    'menu.create.title',
    'menu.create.btn.submit'
);

-- 3. "메뉴 관리" 중복 키 삭제 (common.label.menumanage 유지, v1 page 세그먼트 키 제거)
DELETE FROM message_resource WHERE "key" IN (
    'menu.page.title',
    'menu.page.description'
);

-- 4. "메뉴 관리 설명" 중복 키 삭제 (미사용 desc 키 제거)
DELETE FROM message_resource WHERE "key" = 'common.label.menumanage.desc';

-- 5. "삭제되었습니다." 도메인 키 삭제 (common.deleted 로 통합)
DELETE FROM message_resource WHERE "key" IN (
    'menu.deleted',
    'site.deleted'
);

-- 6. common.label.usermanage ko 오타 수정 ("관리자 관리" → "사용자 관리")
UPDATE message_resource
SET ko = '사용자 관리', en = 'User Manage', updated_at = NOW()
WHERE "key" = 'common.label.usermanage';

-- ────────────── 공통 추가 키 (관리자 v2 공통화) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.btn.cancel',
 '취소',
 'Cancel',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.save',
 '저장',
 'Save',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.loading',
 '불러오는 중...',
 'Loading...',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.all',
 '전체',
 'All',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.id',
 '아이디',
 'ID',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.name',
 '이름',
 'Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.deptCode',
 '부서코드',
 'Dept. Code',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.deptName',
 '부서명',
 'Dept. Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.remark',
 '비고',
 'Remark',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.role',
 '권한',
 'Role',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 관리자 관리 신규 키 (v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('admin.title.new',
 '관리자 등록',
 'Add Admin',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.title.edit',
 '관리자 수정',
 'Edit Admin',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.description',
 '필수 입력 항목은 * 로 표시됩니다.',
 'Required fields are marked with *.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.title.sitePerm',
 '홈페이지 접근 권한',
 'Site Access Permission',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.description.sitePerm',
 '접근을 허용할 홈페이지를 선택하세요.',
 'Select the sites to grant access.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.btn.history',
 '접속이력',
 'Access History',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('admin.label.sitePerm',
 '홈페이지',
 'Site',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('admin.placeholder.id',
 '아이디 검색',
 'Search by ID',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.name',
 '이름 검색',
 'Search by Name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.deptName',
 '부서명 검색',
 'Search by Dept. Name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.username',
 '사용자명 입력',
 'Enter username',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.deptCode',
 '부서코드 입력',
 'Enter dept. code',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.deptNameInput',
 '부서명 입력',
 'Enter dept. name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.placeholder.remark',
 '비고 입력 (선택)',
 'Enter remark (optional)',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.updated',
 '관리자 정보가 수정되었습니다.',
 'Admin updated successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.error.roles_load',
 '권한 목록을 불러오지 못했습니다.',
 'Failed to load role list.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.error.load',
 '관리자 정보를 불러오지 못했습니다.',
 'Failed to load admin info.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('admin.error.save',
 '저장 중 오류가 발생했습니다.',
 'An error occurred while saving.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── validation 추가 키 (관리자 v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('validation.admin.email.required',
 '아이디를 입력해주세요.',
 'Please enter an ID.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.admin.name.required',
 '사용자명을 입력해주세요.',
 'Please enter a username.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.admin.role.required',
 '권한을 선택해주세요.',
 'Please select a role.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── common.status 값 통일 (활성/비활성 → 사용/미사용) ──────────────
UPDATE message_resource SET ko = '사용',   en = 'Active'   WHERE "key" = 'common.status.active';
UPDATE message_resource SET ko = '미사용', en = 'Inactive' WHERE "key" = 'common.status.inactive';

-- menu.badge.modified → common.status.dirty 로 통합 (같은 텍스트 '수정됨')
UPDATE message_resource SET "key" = 'common.status.dirty' WHERE "key" = 'menu.badge.modified';

-- ────────────── common 신규 키 (공통코드 v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.noChange',
 '변경사항이 없습니다.',
 'No changes.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('common.status.dirty',
 '수정됨',
 'Modified',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.groupCode',
 '그룹코드',
 'Group Code',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.groupName',
 '그룹명',
 'Group Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.code',
 '코드값',
 'Code',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.codeName',
 '코드명',
 'Code Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.sort',
 '정렬',
 'Sort',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.search',
 '검색',
 'Search',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.reset',
 '초기화',
 'Reset',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.add',
 '추가',
 'Add',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── code 도메인 신규 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('code.title.group',
 '코드 그룹',
 'Code Group',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('code.title.groupDetail',
 '그룹 상세',
 'Group Detail',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('code.title.groupNew',
 '그룹 추가',
 'New Group',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('code.title.detail',
 '코드 상세',
 'Code Detail',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('code.placeholder.search',
 '그룹코드 / 그룹명 검색',
 'Search group code / name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.placeholder.groupCode',
 '예: STATUS, CATEGORY',
 'e.g. STATUS, CATEGORY',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.placeholder.groupName',
 '예: 상태코드, 분류코드',
 'e.g. Status, Category',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.placeholder.description',
 '선택사항',
 'Optional',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('code.placeholder.codeSearch',
 '코드값 / 코드명',
 'Code / Code Name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.empty',
 '등록된 코드 그룹이 없습니다.',
 'No code groups registered.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.searchEmpty',
 '검색 결과가 없습니다.',
 'No results found.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.selectHint1',
 '왼쪽에서 코드 그룹을 선택하거나',
 'Select a code group on the left,',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.selectHint2',
 '상단 "그룹 추가" 버튼을 눌러 새 그룹을 생성하세요',
 'or click "New Group" to create one.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.deactivateConfirm',
 '그룹을 비활성화하면 하위 코드도 조회되지 않습니다. 계속하시겠습니까?',
 'Deactivating the group will hide its codes. Continue?',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.created',
 '그룹이 추가되었습니다.',
 'Group added successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.saved',
 '그룹이 저장되었습니다.',
 'Group saved successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.group.deleted',
 '그룹이 삭제되었습니다.',
 'Group deleted successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.detail.added',
 '코드가 추가되었습니다.',
 'Code added successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.detail.saved',
 '코드가 수정되었습니다.',
 'Code updated successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.detail.deleted',
 '코드가 삭제되었습니다.',
 'Code deleted successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.detail.toggleError',
 '사용여부 변경에 실패했습니다.',
 'Failed to update code status.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('code.detail.duplicate',
 '동일 그룹 내에 같은 코드값이 존재합니다.',
 'Duplicate code value in the same group.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── validation.code 신규 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('validation.code.groupCode.required',
 '그룹코드를 입력해주세요.',
 'Please enter a group code.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.code.groupCode.format',
 '영문 대문자, 숫자, _만 사용 가능합니다. (최대 30자)',
 'Only uppercase letters, numbers, and _ are allowed. (max 30)',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.code.groupName.required',
 '그룹명을 입력해주세요.',
 'Please enter a group name.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.code.code.required',
 '코드값을 입력해주세요.',
 'Please enter a code value.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.code.code.format',
 '영문 대문자, 숫자, _만 사용 가능합니다.',
 'Only uppercase letters, numbers, and _ are allowed.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.code.codeName.required',
 '코드명을 입력해주세요.',
 'Please enter a code name.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.xss',
 '허용되지 않는 문자가 포함되어 있습니다.',
 'Contains disallowed characters.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── 검색/페이지네이션 공통 버튼 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.btn.prev',
 '이전',
 'Previous',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.btn.next',
 '다음',
 'Next',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.search.detail',
 '상세 검색',
 'Advanced Search',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── common 신규 키 (권한 관리 v2) ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('common.label.displayName',
 '표시명',
 'Display Name',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.memberCount',
 '인원',
 'Members',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.color',
 '색상',
 'Color',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.label.extra',
 '기타',
 'Extra',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('common.type.general',
 '일반',
 'General',
 true, 'WORD', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── role 도메인 신규 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('role.title.new',
 '권한 등록',
 'New Role',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('role.title.edit',
 '권한 수정',
 'Edit Role',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('role.btn.add',
 '권한 추가',
 'Add Role',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('role.label.code',
 '권한 코드',
 'Role Code',
 true, 'WORD', 'system', NOW(), 'system', NOW()),

('role.placeholder.search',
 '표시명 검색',
 'Search by display name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.placeholder.displayName',
 '표시명 입력',
 'Enter display name',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.placeholder.description',
 '권한 설명 (선택)',
 'Role description (optional)',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.created',
 '권한이 등록되었습니다.',
 'Role created successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.updated',
 '권한이 수정되었습니다.',
 'Role updated successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.deleted',
 '권한이 삭제되었습니다.',
 'Role deleted successfully.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.confirm.delete',
 '해당 권한을 삭제하시겠습니까?',
 'Are you sure you want to delete this role?',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.error.load',
 '권한 정보를 불러오지 못했습니다.',
 'Failed to load role info.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('role.error.delete',
 '삭제 중 오류가 발생했습니다.',
 'An error occurred while deleting.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── validation.role 신규 키 ──────────────
INSERT INTO message_resource ("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES

('validation.role.code.required',
 '권한 코드를 입력해주세요.',
 'Please enter a role code.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.role.displayName.required',
 '표시명을 입력해주세요.',
 'Please enter a display name.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW()),

('validation.role.color.required',
 '색상을 선택해주세요.',
 'Please select a color.',
 true, 'SENTENCE', 'system', NOW(), 'system', NOW())

ON CONFLICT ("key") DO NOTHING;

-- ────────────── code msgKey 패턴 적용 — DDL ──────────────
-- code_group, code_detail 테이블에 msgKey 컬럼 추가
ALTER TABLE code_group  ADD COLUMN IF NOT EXISTS group_name_msg_key VARCHAR(255);
ALTER TABLE code_detail ADD COLUMN IF NOT EXISTS name_msg_key       VARCHAR(255);
