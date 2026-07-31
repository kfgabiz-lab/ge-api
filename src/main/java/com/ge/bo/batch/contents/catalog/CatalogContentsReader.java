package com.ge.bo.batch.contents.catalog;

import com.ge.bo.entity.IfCatalogFileInfo;
import com.ge.bo.entity.IfCatalogInfo;
import com.ge.bo.repository.IfCatalogFileInfoRepository;
import com.ge.bo.repository.IfCatalogInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * IF_R_CATALOG_INFO / IF_R_CATALOG_FILE_INFO 원천 조회 및 IF_RESULT 갱신 Reader.
 *
 * 미처리 행(if_result='N')을 ctlg_code 기준으로 그룹화해 반환한다. 완전한 2-cursor 병합 스트리밍은 아니고,
 * ctlg_code별로 축약된 문서 단위 데이터만 메모리에 유지하는 절충안이다(원시 행 전체를 한 번에 올리지 않는 것과는
 * 다름 — 카탈로그 데이터 규모가 매우 커지면 진짜 커서 기반 2-pass 방식으로 교체가 필요하다).
 * CatalogContentsBatchService(오케스트레이터)와 다른 Bean으로 분리해 @Transactional이 프록시를 타도록 한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogContentsReader {

    private static final String PENDING = "N";

    private final IfCatalogInfoRepository ifCatalogInfoRepository;
    private final IfCatalogFileInfoRepository ifCatalogFileInfoRepository;

    /** 미처리 행 중 복합키(ctlg_code, nahp_level_seq) 중복 목록 조회(로그용) — quarantineDuplicateKeys()와 짝 */
    @Transactional(readOnly = true)
    public List<Object[]> findDuplicateKeyRows() {
        return ifCatalogInfoRepository.findDuplicateKeyRows();
    }

    /** 복합키 중복 행(가장 이른 1건 제외) 격리(E) — loadPendingHeaderGroups() 호출 전에 먼저 실행해야 한다 */
    @Transactional
    public int quarantineDuplicateKeys() {
        return ifCatalogInfoRepository.quarantineDuplicateKeys();
    }

    @Transactional(readOnly = true)
    public Map<String, List<IfCatalogInfo>> loadPendingHeaderGroups() {
        Map<String, List<IfCatalogInfo>> groups = new LinkedHashMap<>();
        for (IfCatalogInfo row : ifCatalogInfoRepository.findPending(PENDING)) {
            // quarantineDuplicateKeys() 실행 직후에도 그 찰나에 동일 복합키 행이 새로 들어오면, Hibernate가
            // 그 행을 null로 반환하는 경우가 있다 — 다음 회차에
            // 다시 처리되므로 이번 회차에서는 건너뛴다.
            if (row == null) {
                continue;
            }
            groups.computeIfAbsent(row.getCtlgCode(), k -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public Map<String, List<IfCatalogFileInfo>> loadPendingFileGroups() {
        Map<String, List<IfCatalogFileInfo>> groups = new LinkedHashMap<>();
        try (Stream<IfCatalogFileInfo> stream = ifCatalogFileInfoRepository.streamPending(PENDING)) {
            stream.forEach(row -> groups.computeIfAbsent(row.getCtlgCode(), k -> new ArrayList<>()).add(row));
        }
        return groups;
    }

    /** 문서 처리 결과에 따라 헤더·파일 IF의 if_result를 'P'(성공) 또는 'E'(격리)로 갱신 */
    @Transactional
    public void markIfResult(String ctlgCode, String ifResult) {
        ifCatalogInfoRepository.updateIfResultByCtlgCode(ctlgCode, ifResult);
        ifCatalogFileInfoRepository.updateIfResultByCtlgCode(ctlgCode, ifResult);
    }

    /** 헤더 없이 파일 IF만 존재하는 ctlg_code의 파일 행만 별도로 격리 표시할 때 사용 */
    @Transactional
    public void markFileResultOnly(String ctlgCode, String ifResult) {
        ifCatalogFileInfoRepository.updateIfResultByCtlgCode(ctlgCode, ifResult);
    }
}
