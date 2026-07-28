package com.ge.bo.batch.contents.ssq;

import com.ge.bo.entity.ContentsCategory;
import com.ge.bo.repository.ContentsCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SSQ 미매핑 카테고리 재처리(remap) — 매핑정의서 04시트의 "변환표에 없는 경로는 보류(NULL+리포트)로 두고,
 * 변환표 보완 후 재처리" 요구를 만족한다.
 *
 * 원본 IF 행(if_r_ssq_document)은 한 번 처리되면 if_result='P'로 바뀌어 배치가 다시 읽지 않으므로, 원본을 다시
 * 조회하지 않고 이미 저장된 contents_category.source_path(예: "PLC&gt;Smart I/O&gt;...")를 그대로 재사용해
 * SsqCategoryMapping을 재조회한다. SsqCategoryMappingEntry(정적 enum)에 새 항목이 추가된 뒤 이 메서드를
 * 실행하면 그동안 미매핑이었던 카테고리가 채워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsqCategoryRemapService {

    private static final String SOURCE_SYSTEM = "SSQ";

    private final ContentsCategoryRepository categoryRepository;
    private final SsqCategoryMapping categoryMapping;

    @Transactional
    public int remapUnmapped() {
        List<ContentsCategory> targets =
            categoryRepository.findUnmapped(SOURCE_SYSTEM);
        // 이미 매핑돼 nahp_category_id는 있지만 코드가 아직 없던 행 — 코드 신규 확정(2026-07-24) 후 소급 반영 대상
        targets.addAll(
            categoryRepository.findMappedWithoutCode(SOURCE_SYSTEM));

        int remapped = 0;
        for (ContentsCategory category : targets) {
            String[] levels = category.getSourcePath().split(">", -1);
            String level1 = levels.length > 0 ? levels[0] : null;
            String level2 = levels.length > 1 ? levels[1] : null;
            String level3 = levels.length > 2 ? levels[2] : null;
            String level4 = levels.length > 3 ? levels[3] : null;

            Optional<SsqCategoryResolution> resolved = categoryMapping.resolve(level1, level2, level3, level4);
            if (resolved.isPresent()) {
                SsqCategoryResolution r = resolved.get();
                category.applyRemappedCategory(r.nahpCategoryId(), r.categoryL1Id(), r.categoryL2Id(), r.categoryL3Id());
                remapped++;
            }
        }
        log.info("SSQ 카테고리 재매핑 완료 — 대상 {}건 중 {}건 신규 매핑", targets.size(), remapped);
        return remapped;
    }
}
