package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 다운로드 이력 엔티티 — download_log 테이블 매핑
 * 이력성 테이블이므로 수정 없이 저장만 한다
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "download_log")
public class DownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 다운로드한 페이지 slug */
    @Column(name = "template_slug", nullable = false, length = 255)
    private String templateSlug;

    /** 개인정보 다운로드 사유 (10~50자) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** 파일 형식 (xlsx / csv) */
    @Column(nullable = false, length = 10)
    private String format;

    /** 다운로드 사용자 이메일 */
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    /** 요청 IP */
    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    /** 다운로드 시각 */
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public DownloadLog(String templateSlug, String reason, String format,
                       String createdBy, String ipAddress) {
        this.templateSlug = templateSlug;
        this.reason = reason;
        this.format = format;
        this.createdBy = createdBy;
        this.ipAddress = ipAddress;
        this.createdAt = OffsetDateTime.now();
    }
}
