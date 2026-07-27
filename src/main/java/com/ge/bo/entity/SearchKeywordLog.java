package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * FO 검색어 로그 엔티티 — search_keyword_log 테이블 매핑
 * 이력성(append-only) 테이블이므로 수정/삭제 없이 저장만 한다 (DownloadLog / ContactUsInquiry 패턴)
 * 인기검색어(Popular Keywords) 집계용이며, source 값으로 다운로드센터/통합검색 랭킹을 분리한다
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "search_keyword_log",
        indexes = {
                // 인기검색어 집계 쿼리(source + 정규화 키워드 + 최근 30일) 대응 인덱스
                @Index(name = "IDX_SEARCH_KEYWORD_LOG_SOURCE_NORM_CREATED",
                        columnList = "source, keyword_norm, created_at")
        })
public class SearchKeywordLog {

    /** 검색어 출처 — 다운로드센터 */
    public static final String SOURCE_DOWNLOAD_CENTER = "DOWNLOAD_CENTER";
    /** 검색어 출처 — 통합검색(GNB 헤더검색 / 404 페이지 검색 등) */
    public static final String SOURCE_UNIFIED_SEARCH = "UNIFIED_SEARCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 검색어 출처 — DOWNLOAD_CENTER / UNIFIED_SEARCH (공통코드가 아닌 앱코드 고정값, DownloadLog.format 패턴) */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /** 사용자가 입력한 원본 검색어 */
    @Column(name = "keyword", nullable = false, length = 255)
    private String keyword;

    /** 집계용 정규화 검색어 (trim → 소문자 → 연속 공백 단일화) */
    @Column(name = "keyword_norm", nullable = false, length = 255)
    private String keywordNorm;

    /** 사이트 ID (X-Site-Id 헤더, 미지정 가능) */
    @Column(name = "site_id")
    private Long siteId;

    /** 요청자 IP (X-Forwarded-For 우선) */
    @Column(name = "created_ip", length = 50)
    private String createdIp;

    /** 검색 시각 (TIME ZONE 포함) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public SearchKeywordLog(String source, String keyword, String keywordNorm, Long siteId, String createdIp) {
        this.source = source;
        this.keyword = keyword;
        this.keywordNorm = keywordNorm;
        this.siteId = siteId;
        this.createdIp = createdIp;
        // 검색 시각은 서버가 채운다 (DownloadLog 패턴)
        this.createdAt = OffsetDateTime.now();
    }
}
