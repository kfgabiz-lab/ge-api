package com.ge.bo.batch.contents.ssq;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SSQ 전체 경로(level_1~4) → NAHP 카테고리 변환 — 정적 매핑표(SsqCategoryMappingEntry) 기반 longest-match 조회.
 *
 * 단일 레벨이 아닌 전체 경로 기준으로 매핑하며, 등록된 경로가 입력 경로의 접두(prefix)이면 매칭된다.
 * 여러 접두가 후보일 수 있으므로 가장 긴(가장 구체적인) 등록 경로를 우선한다(longest-match) — 이렇게 하면
 * 표에 없는 더 깊은 하위 레벨도 가장 가까운 조상 등록값을 자연스럽게 물려받는다.
 *
 * nahp_category_id에는 코드가 아니라 "L1|L2|L3" 이름 문자열을 저장한다(SsqCategoryMappingEntry#toNahpCategoryId) —
 * 소스 무관 공통 식별자로 CATALOG/CERTI와 형태를 맞추기 위함이며, 실제 코드는 category_l1/l2/l3_id 컬럼에 별도로
 * 저장한다(2026-07-24부터 page_data 기준 코드 반영, SsqCategoryMappingEntry 참고).
 */
@Component
public class SsqCategoryMapping {

    private final Map<String, SsqCategoryMappingEntry> byPath;

    public SsqCategoryMapping() {
        Map<String, SsqCategoryMappingEntry> map = new LinkedHashMap<>();
        for (SsqCategoryMappingEntry entry : SsqCategoryMappingEntry.values()) {
            map.put(joinKey(entry.getSsqPath()), entry);
        }
        this.byPath = map;
    }

    /**
     * level_1~4를 순서대로 전달(SSQ 원천 자체는 4레벨까지 있을 수 있어 매칭에는 전부 사용) — trim 후 빈 레벨은
     * 제외하고, 남은 경로를 가장 긴 접두부터 짧은 접두까지 순서대로 조회한다. 매칭되면 NAHP 카테고리 값(이름 조합 +
     * 레벨별 코드)을 반환하고, 없으면 미매핑(empty). NAHP 자체는 3단계 고정이라 반환값도 L1~L3까지만 있다.
     */
    public Optional<SsqCategoryResolution> resolve(String level1, String level2, String level3, String level4) {
        List<String> levels = new ArrayList<>();
        for (String level : new String[]{level1, level2, level3, level4}) {
            if (level != null) {
                String v = level.trim();
                if (!v.isEmpty()) {
                    levels.add(v);
                }
            }
        }
        for (int len = levels.size(); len >= 1; len--) {
            SsqCategoryMappingEntry entry = byPath.get(joinKey(levels.subList(0, len)));
            if (entry != null) {
                return Optional.of(new SsqCategoryResolution(
                    entry.toNahpCategoryId(), entry.getL1Code(), entry.getL2Code(), entry.getL3Code()));
            }
        }
        return Optional.empty();
    }

    private String joinKey(List<String> segments) {
        return String.join(">", segments);
    }
}
