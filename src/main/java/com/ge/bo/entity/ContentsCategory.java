package com.ge.bo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 콘텐츠 카테고리 등록 엔티티 — contents_category 테이블 매핑 (N:M)
 * (NAHP_콘텐츠테이블_명세서_v1.5 02_contents_category 시트 기준)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contents_category",
    uniqueConstraints = @UniqueConstraint(name = "uk_contents_category_path", columnNames = {"contents_id", "source_path"}),
    indexes = {
        @Index(name = "idx_contents_category_active_nahp", columnList = "nahp_category_id, contents_id"),
        @Index(name = "idx_contents_category_delete_check", columnList = "source_system, delete_check_batch_id")
    })
public class ContentsCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contents_id", nullable = false, updatable = false)
    private Long contentsId;

    @Column(name = "source_system", nullable = false, length = 10, updatable = false)
    private String sourceSystem;

    /** 소스가 보낸 카테고리 위치 원본 표현. SSQ='PLC>Smart I/O>...', CATALOG/CERTI='L01|L01-01|L01-01-01' */
    @Column(name = "source_path", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String sourcePath;

    /**
     * NAHP 카테고리 참조값 — 소스와 무관하게 항상 같은 의미(콘텐츠가 속한 NAHP 카테고리)를 가리키는 공통 컬럼이다.
     * CATALOG/CERTI는 원천 자체가 이미 NAHP 카테고리 구조(L01-01 체계)로 보내주므로 원값을 그대로 쓴다.
     * SSQ는 원천이 자기 자신의 제품군 구조로 보내기 때문에, 저장 전에 SsqCategoryMapping으로 NAHP 구조로
     * 가공(변환)하는 중간 단계가 필요할 뿐이다 — 컬럼의 의미나 소스별 저장 방식이 다른 게 아니다.
     * SSQ 변환 결과는 코드가 아니라 "L1|L2|L3" 이름 경로로 저장한다 — 소스 무관 공통 식별자로 CATALOG/CERTI와
     * 형태를 맞추기 위한 설계이고, 실제 코드는 아래 category_l1/l2/l3_id 컬럼에 별도로 저장한다.
     */
    @Column(name = "nahp_category_id", length = 300)
    private String nahpCategoryId;

    /**
     * 레벨별 NAHP 카테고리 코드(L01, L01-01 체계) — NAHP는 3단계 고정이라 L4는 없다.
     * CATALOG/CERTI는 원천 NAHP_LEVEL1~3_ID를 그대로 채운다. SSQ는 원천에 코드가 없어 변환표(SsqCategoryMapping,
     * 실제로는 SsqCategoryMappingEntry에 등록된 값)로 채운다. page_data(category-data)에 아직 3단계(소분류)
     * 코드가 등록되지 않아(2026-07-24 기준) 당분간 소스 불문 category_l3_id는 null — 3단계가 등록되면 채우면 된다.
     */
    @Column(name = "category_l1_id", length = 50)
    private String categoryL1Id;

    @Column(name = "category_l2_id", length = 50)
    private String categoryL2Id;

    @Column(name = "category_l3_id", length = 50)
    private String categoryL3Id;

    @Column(name = "nahp_level_seq")
    private Integer nahpLevelSeq;

    @Column(name = "nahp_display_flag", nullable = false)
    private Boolean nahpDisplayFlag;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "delete_check_batch_id", nullable = false)
    private Long deleteCheckBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public ContentsCategory(Long id, Long contentsId, String sourceSystem, String sourcePath, String nahpCategoryId,
                            String categoryL1Id, String categoryL2Id, String categoryL3Id, Integer nahpLevelSeq,
                            Boolean nahpDisplayFlag, Boolean isDeleted, OffsetDateTime deletedAt,
                            Long deleteCheckBatchId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.contentsId = contentsId;
        this.sourceSystem = sourceSystem;
        this.sourcePath = sourcePath;
        this.nahpCategoryId = nahpCategoryId;
        this.categoryL1Id = categoryL1Id;
        this.categoryL2Id = categoryL2Id;
        this.categoryL3Id = categoryL3Id;
        this.nahpLevelSeq = nahpLevelSeq;
        this.nahpDisplayFlag = nahpDisplayFlag;
        this.isDeleted = isDeleted != null ? isDeleted : Boolean.FALSE;
        this.deletedAt = deletedAt;
        this.deleteCheckBatchId = deleteCheckBatchId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 미매핑 카테고리 재처리(remap) 전용 — 변환표가 보완된 뒤 source_path를 재조회해 얻은 값으로 갱신한다 */
    public void applyRemappedCategory(String nahpCategoryId, String categoryL1Id, String categoryL2Id, String categoryL3Id) {
        this.nahpCategoryId = nahpCategoryId;
        this.categoryL1Id = categoryL1Id;
        this.categoryL2Id = categoryL2Id;
        this.categoryL3Id = categoryL3Id;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 소스 재전송에서 이 카테고리 행이 더 이상 오지 않아(행 부재) 삭제된 것으로 판단될 때 소프트 삭제 처리 */
    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}
