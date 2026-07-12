package com.ge.bo.service;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Service — 배너
 * 생성일시: 2026-07-12T13:37:46.284663+09:00
 * 원본 Slug Entity: id=1, tableName=banner
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.dto.BannerDataRequest;
import com.ge.bo.dto.BannerDataResponse;
import com.ge.bo.entity.BannerData;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.BannerDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배너 서비스
 */
@Service
@RequiredArgsConstructor
public class BannerDataService {

  private final BannerDataRepository bannerDataRepository;

  /** 목록 조회 (페이징) */
  @Transactional(readOnly = true)
  public Page<BannerDataResponse> getList(Pageable pageable) {
    return bannerDataRepository.findAll(pageable).map(BannerDataResponse::from);
  }

  /** 단건 조회 */
  @Transactional(readOnly = true)
  public BannerDataResponse getOne(Long id) {
    return BannerDataResponse.from(findOrThrow(id));
  }

  /** 등록 */
  @Transactional
  public BannerDataResponse create(BannerDataRequest request) {
    BannerData entity = BannerData.builder()
        .bannerPosition(request.bannerPosition())
        .title(request.title())
        .postDateFrom(request.postDateFrom())
        .prefix(request.prefix())
        .mainTitle(request.mainTitle())
        .bottomText(request.bottomText())
        .subTitle(request.subTitle())
        .url(request.url())
        .imageFileId(request.imageFileId())
        .sortOrder(request.sortOrder())
        .isVisible(request.isVisible())
        .infoSort(request.infoSort())
        .postDateTo(request.postDateTo())
        .build();
    return BannerDataResponse.from(bannerDataRepository.save(entity));
  }

  /** 수정 */
  @Transactional
  public BannerDataResponse update(Long id, BannerDataRequest request) {
    BannerData entity = findOrThrow(id);
    entity.setBannerPosition(request.bannerPosition());
    entity.setTitle(request.title());
    entity.setPostDateFrom(request.postDateFrom());
    entity.setPrefix(request.prefix());
    entity.setMainTitle(request.mainTitle());
    entity.setBottomText(request.bottomText());
    entity.setSubTitle(request.subTitle());
    entity.setUrl(request.url());
    entity.setImageFileId(request.imageFileId());
    entity.setSortOrder(request.sortOrder());
    entity.setIsVisible(request.isVisible());
    entity.setInfoSort(request.infoSort());
    entity.setPostDateTo(request.postDateTo());
    return BannerDataResponse.from(entity);
  }

  /** 삭제 */
  @Transactional
  public void delete(Long id) {
    bannerDataRepository.delete(findOrThrow(id));
  }

  /** id로 조회, 없으면 예외 */
  private BannerData findOrThrow(Long id) {
    return bannerDataRepository.findById(id)
        .orElseThrow(() -> BusinessException.notFound("해당 데이터를 찾을 수 없습니다. id=" + id));
  }
}
