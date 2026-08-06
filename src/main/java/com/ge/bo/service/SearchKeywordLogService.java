package com.ge.bo.service;

import com.ge.bo.common.search.BannedKeywordFilter;
import com.ge.bo.entity.SearchKeywordLog;
import com.ge.bo.repository.SearchKeywordLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 검색어 로그 / 인기검색어(Popular Keywords) 서비스
 * - 저장: 정규화 키워드를 함께 적재하는 fire-and-forget 성격 (빈 검색어는 조용히 무시)
 * - 집계: 최근 30일, source별, 정규화 키워드 기준 상위 5건 (EntityManager 네이티브 쿼리 — PageDataService 컨벤션)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordLogService {

    /** 인기검색어 집계 기간(일) */
    private static final int POPULAR_PERIOD_DAYS = 30;
    /** 인기검색어 노출 건수 */
    private static final int POPULAR_LIMIT = 5;

    private final SearchKeywordLogRepository searchKeywordLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 검색어 저장 — 원본 keyword + 정규화 keywordNorm 동시 적재
     * 검색 동작을 막지 않는 부가 기능이므로 빈 검색어는 예외 없이 저장만 건너뛴다
     *
     * @param source  DOWNLOAD_CENTER / UNIFIED_SEARCH
     * @param keyword 사용자가 입력한 원본 검색어
     * @param siteId  X-Site-Id 헤더 (없으면 null)
     * @param ip      요청자 IP (X-Forwarded-For 우선)
     */
    @Transactional
    public void logKeyword(String source, String keyword, Long siteId, String ip) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        // 정규화: trim → 소문자 → 연속 공백 단일 공백
        String keywordNorm = trimmed.toLowerCase().replaceAll("\\s+", " ");

        // 다운로드센터 검색어만 금지어(포함 매칭) 필터링 — 통합검색은 대상 아님
        if (SearchKeywordLog.SOURCE_DOWNLOAD_CENTER.equals(source)
                && BannedKeywordFilter.containsBannedWord(keywordNorm)) {
            return;
        }

        searchKeywordLogRepository.save(SearchKeywordLog.builder()
                .source(source)
                // 원본 검색어는 입력값 그대로 보존(집계/표기는 keywordNorm 기준 그룹의 최신 원본 사용)
                .keyword(keyword)
                .keywordNorm(keywordNorm)
                .siteId(siteId)
                .createdIp(ip)
                .build());
    }

    /**
     * 인기검색어 조회 — 최근 30일 / 해당 source / keyword_norm 기준 그룹핑 후 건수 내림차순 상위 5건
     * 같은 그룹의 화면 표기는 그 그룹에서 가장 최근에 검색된 원본 keyword를 사용한다
     * (윈도우 함수로 그룹별 건수 + 최신 원본을 한 번에 계산)
     * 데이터가 없으면 빈 리스트 반환 — 정적 폴백 처리는 FE 책임
     */
    @Transactional(readOnly = true)
    public List<String> findPopularKeywords(String source, Long siteId) {
        String sql = "SELECT keyword, cnt FROM ("
                + "  SELECT keyword, keyword_norm,"
                + "         count(*) OVER (PARTITION BY keyword_norm) AS cnt,"
                + "         row_number() OVER (PARTITION BY keyword_norm ORDER BY created_at DESC) AS rn"
                + "  FROM search_keyword_log"
                + "  WHERE source = :source"
                // 기간/건수는 상수라 바인딩 없이 SQL에 직접 전개 (findProductInsights의 LIMIT 3 컨벤션)
                + "   AND created_at >= now() - INTERVAL '" + POPULAR_PERIOD_DAYS + " days'"
                + (siteId != null ? "   AND (site_id = :siteId OR site_id IS NULL)" : "")
                + ") t"
                + " WHERE rn = 1"
                + " ORDER BY cnt DESC, keyword_norm ASC"
                + " LIMIT " + POPULAR_LIMIT;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("source", source);
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<String> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.add(row[0].toString());
            }
        }
        return result;
    }
}
