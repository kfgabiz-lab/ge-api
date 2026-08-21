package com.ge.bo.batch.contents.ssq;

import com.ge.bo.entity.ContentsCategory;
import com.ge.bo.repository.ContentsCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

        int backfilled = backfillDualCategories();
        log.info("SSQ 카테고리 재매핑 완료 — 대상 {}건 중 {}건 신규 매핑, 보조 매핑 {}건 소급 반영", targets.size(), remapped, backfilled);
        return remapped + backfilled;
    }

    /**
     * SsqCategoryMappingEntry에 보조 매핑(예: VFD의 L05-04)이 나중에 추가된 경우, 이미 처리 완료돼 기본
     * 매핑만 갖고 있던 기존 카테고리 행에도 소급으로 보조 카테고리 행을 만들어 채운다.
     * SsqContentsConverter#convert()와 동일한 규칙(source_path + "#dual:" + 보조 L2 코드)으로 대상을 식별해
     * 이미 백필된 행은 건너뛴다.
     */
    private int backfillDualCategories() {
        List<ContentsCategory> all = categoryRepository.findBySourceSystemAndIsDeletedFalse(SOURCE_SYSTEM);
        Map<Long, Set<String>> pathsByContentsId = all.stream()
            .collect(Collectors.groupingBy(ContentsCategory::getContentsId,
                Collectors.mapping(ContentsCategory::getSourcePath, Collectors.toSet())));

        int backfilled = 0;
        for (ContentsCategory category : all) {
            String sourcePath = category.getSourcePath();
            if (sourcePath.contains("#dual:")) {
                continue;
            }
            String[] levels = sourcePath.split(">", -1);
            List<SsqCategoryResolution> resolutions = categoryMapping.resolveAll(
                levels.length > 0 ? levels[0] : null, levels.length > 1 ? levels[1] : null,
                levels.length > 2 ? levels[2] : null, levels.length > 3 ? levels[3] : null);
            if (resolutions.size() < 2) {
                continue;
            }
            SsqCategoryResolution secondary = resolutions.get(1);
            String dualPath = sourcePath + "#dual:" + secondary.categoryL2Id();
            Set<String> existingPaths = pathsByContentsId.computeIfAbsent(category.getContentsId(), k -> new java.util.HashSet<>());
            if (existingPaths.contains(dualPath)) {
                continue;
            }

            OffsetDateTime now = OffsetDateTime.now();
            categoryRepository.save(ContentsCategory.builder()
                .contentsId(category.getContentsId())
                .sourceSystem(SOURCE_SYSTEM)
                .sourcePath(dualPath)
                .nahpCategoryId(secondary.nahpCategoryId())
                .categoryL1Id(secondary.categoryL1Id())
                .categoryL2Id(secondary.categoryL2Id())
                .categoryL3Id(secondary.categoryL3Id())
                .nahpLevelSeq(category.getNahpLevelSeq())
                .nahpDisplayFlag(category.getNahpDisplayFlag())
                .isDeleted(false)
                .deleteCheckBatchId(category.getDeleteCheckBatchId())
                .createdAt(now)
                .updatedAt(now)
                .build());
            existingPaths.add(dualPath);
            backfilled++;
        }
        return backfilled;
    }
}
