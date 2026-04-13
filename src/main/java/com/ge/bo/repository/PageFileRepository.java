package com.ge.bo.repository;

import com.ge.bo.entity.PageFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 페이지 파일 레포지토리
 * 기본 CRUD는 JpaRepository 상속으로 제공
 */
public interface PageFileRepository extends JpaRepository<PageFile, Long> {

    /**
     * data_id로 파일 목록 조회
     * 주로 page_data 삭제 시 연관 파일 전체를 가져올 때 사용
     *
     * @param dataId page_data.id
     */
    List<PageFile> findByDataId(Long dataId);

    /**
     * data_id + field_key로 특정 필드의 파일 목록 조회
     *
     * @param dataId   page_data.id
     * @param fieldKey 필드 키 (예: attachFiles)
     */
    List<PageFile> findByDataIdAndFieldKey(Long dataId, String fieldKey);
}
