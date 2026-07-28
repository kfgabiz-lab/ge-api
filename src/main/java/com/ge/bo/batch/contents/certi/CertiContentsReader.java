package com.ge.bo.batch.contents.certi;

import com.ge.bo.entity.IfCertiMaster;
import com.ge.bo.repository.IfCertiMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * IF_R_CERTI_MASTER 원천 조회 및 IF_RESULT 갱신 Reader — CATALOG/SSQ Reader와 동일한 이유로
 * 오케스트레이터(CertiContentsBatchService)와 다른 Bean으로 분리했다.
 * CERTI는 한 테이블에 문서·카테고리·부가정보가 모두 들어있어 별도 파일 IF가 없다(헤더-파일 불일치 케이스 자체가 없음).
 */
@Component
@RequiredArgsConstructor
public class CertiContentsReader {

    private static final String PENDING = "N";

    private final IfCertiMasterRepository ifCertiMasterRepository;

    /** key = "CERTI_NO|BI" — 인증서 자연키(source_doc_key)와 동일한 형식 */
    @Transactional(readOnly = true)
    public Map<String, List<IfCertiMaster>> loadPendingGroups() {
        Map<String, List<IfCertiMaster>> groups = new LinkedHashMap<>();
        try (Stream<IfCertiMaster> stream = ifCertiMasterRepository.streamPending(PENDING)) {
            stream.forEach(row -> groups
                .computeIfAbsent(row.getCertiNo() + "|" + row.getBi(), k -> new ArrayList<>())
                .add(row));
        }
        return groups;
    }

    @Transactional
    public void markIfResult(String certiNo, String bi, String ifResult) {
        ifCertiMasterRepository.updateIfResultByCertiNoAndBi(certiNo, bi, ifResult);
    }
}
