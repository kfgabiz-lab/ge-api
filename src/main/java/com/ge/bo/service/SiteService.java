package com.ge.bo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.dto.SiteDto;
import com.ge.bo.entity.AdminUserSite;
import com.ge.bo.entity.Site;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.AdminUserSiteRepository;
import com.ge.bo.repository.SiteRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteService {

  private final SiteRepository siteRepository;
  private final AdminUserSiteRepository adminUserSiteRepository;
  private final AdminRepository adminRepository;
  private final MessageResourceService messageResourceService;
  private final SiteTimeZoneResolver siteTimeZoneResolver;

  /** 홈페이지 목록 조회 */
  @Transactional(readOnly = true)
  public List<SiteDto.Response> getAllSites(Boolean isActive) {
    List<Site> sites = (isActive != null)
        ? siteRepository.findByIsActive(isActive)
        : siteRepository.findAll();
    return sites.stream().map(this::toResponse).collect(Collectors.toList());
  }

  /** 홈페이지 단건 조회 */
  @Transactional(readOnly = true)
  public SiteDto.Response getSiteById(Long id) {
    return toResponse(findSiteById(id));
  }

  /** 홈페이지 신규 등록 */
  @Transactional
  public SiteDto.Response createSite(SiteDto.CreateRequest request) {
    /* nameMsgKey로 message_resource 조회 → ko 값을 name에 저장 */
    String nameKo = messageResourceService.resolveKo(request.getNameMsgKey());

    Site site = Site.builder()
        .name(nameKo)
        .nameMsgKey(request.getNameMsgKey())
        .description(request.getDescription() != null ? request.getDescription().trim() : null)
        .domain(request.getDomain() != null ? request.getDomain().trim() : null)
        .timezone(request.getTimezone() != null ? request.getTimezone().trim() : null)
        .isActive(request.getIsActive())
        .build();

    return toResponse(siteRepository.save(site));
  }

  /** 홈페이지 정보 수정 */
  @Transactional
  public SiteDto.Response updateSite(Long id, SiteDto.UpdateRequest request) {
    Site site = findSiteById(id);

    /* nameMsgKey로 message_resource 조회 → ko 값을 name에 저장 */
    String nameKo = messageResourceService.resolveKo(request.getNameMsgKey());

    site.setName(nameKo);
    site.setNameMsgKey(request.getNameMsgKey());
    site.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
    site.setDomain(request.getDomain() != null ? request.getDomain().trim() : null);
    site.setTimezone(request.getTimezone() != null ? request.getTimezone().trim() : null);
    site.setActive(request.getIsActive());

    SiteDto.Response response = toResponse(siteRepository.save(site));
    /* timezone이 바뀌었을 수 있으므로 캐시 무효화 — 안 지우면 다음 조회 시 옛 값(또는 폴백)이 계속 쓰인다 */
    siteTimeZoneResolver.evict(id);
    return response;
  }

  /** 홈페이지 삭제 */
  @Transactional
  public void deleteSite(Long id) {
    siteRepository.delete(findSiteById(id));
    siteTimeZoneResolver.evict(id);
  }

  /** 관리자별 매핑된 홈페이지 목록 조회 */
  @Transactional(readOnly = true)
  public List<SiteDto.Response> getSitesByAdminUser(Long adminUserId) {
    if (!adminRepository.existsById(adminUserId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "해당 관리자를 찾을 수 없습니다.");
    }
    List<AdminUserSite> mappings = adminUserSiteRepository.findByAdminUserId(adminUserId);
    List<Long> siteIds = mappings.stream().map(AdminUserSite::getSiteId).collect(Collectors.toList());
    return siteRepository.findAllById(siteIds).stream().map(this::toResponse).collect(Collectors.toList());
  }

  /** 관리자 홈페이지 매핑 일괄 변경 */
  @Transactional
  public List<SiteDto.Response> updateAdminUserSites(Long adminUserId, SiteDto.SiteMappingRequest request) {
    if (!adminRepository.existsById(adminUserId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "해당 관리자를 찾을 수 없습니다.");
    }

    List<Long> siteIds = request.getSiteIds();
    if (!siteIds.isEmpty()) {
      long validCount = siteRepository.findAllById(siteIds).size();
      if (validCount != siteIds.size()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "INVALID_SITE_ID", "유효하지 않은 홈페이지 ID가 포함되어 있습니다.");
      }
    }

    adminUserSiteRepository.deleteByAdminUserId(adminUserId);
    List<AdminUserSite> newMappings = siteIds.stream()
        .map(siteId -> AdminUserSite.builder()
            .adminUserId(adminUserId)
            .siteId(siteId)
            .build())
        .collect(Collectors.toList());
    adminUserSiteRepository.saveAll(newMappings);

    return getSitesByAdminUser(adminUserId);
  }

    /* ══════════════════════════════════════ */
    /*  내부 헬퍼                              */
    /* ══════════════════════════════════════ */


  private Site findSiteById(Long id) {
    return siteRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "SITE_NOT_FOUND", "해당 홈페이지를 찾을 수 없습니다."));
  }

  private SiteDto.Response toResponse(Site site) {
    return SiteDto.Response.builder()
        .id(site.getId())
        .name(site.getName())
        .nameMsgKey(site.getNameMsgKey())
        .description(site.getDescription())
        .domain(site.getDomain())
        .timezone(site.getTimezone())
        .isActive(site.isActive())
        .createdBy(site.getCreatedBy())
        .createdAt(site.getCreatedAt())
        .updatedBy(site.getUpdatedBy())
        .updatedAt(site.getUpdatedAt())
        .build();
  }
}
