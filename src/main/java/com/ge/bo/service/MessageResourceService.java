package com.ge.bo.service;

import com.ge.bo.dto.MessageResourceDto;
import com.ge.bo.entity.MessageResource;
import com.ge.bo.entity.MessageResourceType;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.MessageResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageResourceService {

  private final MessageResourceRepository messageResourceRepository;

    /**
     * 목록 조회 (검색 조건 AND 조합, 페이징)
     * - active 파라미터: "true"/"false" 문자열 → Boolean 변환, "전체"/""/null이면 null로 처리
     */
  @Transactional(readOnly = true)
    public MessageResourceDto.PageResponse getList(
            String key, String ko, String en, String activeStr, String resourceTypeStr, int page, int size) {

        /* 사용여부 문자열 → Boolean 변환 */
    Boolean active = null;
    if ("true".equals(activeStr) || "사용".equals(activeStr)) {
      active = true;
    }
    if ("false".equals(activeStr) || "미사용".equals(activeStr)) {
      active = false;
    }

        /* 유형 문자열 → Enum 변환 */
    MessageResourceType resourceType = null;
    if ("단어".equals(resourceTypeStr) || "WORD".equals(resourceTypeStr)) {
      resourceType = MessageResourceType.WORD;
    }
    if ("문장".equals(resourceTypeStr) || "SENTENCE".equals(resourceTypeStr)) {
      resourceType = MessageResourceType.SENTENCE;
    }

    Page<MessageResource> result = messageResourceRepository.search(
                key, ko, en, active, resourceType, PageRequest.of(page, size));

    return MessageResourceDto.PageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .size(result.getSize())
                .build();
  }

    /**
     * 등록
     * - key 중복 시 409 CONFLICT
     */
  @Transactional
    public MessageResourceDto.Response create(MessageResourceDto.CreateRequest request) {
        /* key 중복 확인 */
    if (messageResourceRepository.existsByKey(request.getKey())) {
      throw BusinessException.conflict("이미 사용 중인 번역 키입니다.");
    }

    MessageResource entity = MessageResource.builder()
                .key(request.getKey())
                .ko(request.getKo())
                .en(request.getEn())
                .active(true) /* 등록 시 기본 사용 상태 */
                .resourceType(request.getResourceType() != null ? request.getResourceType() : MessageResourceType.WORD)
                .build();

    return toResponse(messageResourceRepository.save(entity));
  }

    /**
     * 수정 — key 변경 불가 (ko, en, active만 수정)
     * - 존재하지 않는 id → 404 NOT_FOUND
     */
  @Transactional
    public MessageResourceDto.Response update(Long id, MessageResourceDto.UpdateRequest request) {
    MessageResource entity = messageResourceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("다국어 항목을 찾을 수 없습니다."));

    entity.setKo(request.getKo());
    entity.setEn(request.getEn());
    entity.setActive(request.getActive());
    if (request.getResourceType() != null) {
      entity.setResourceType(request.getResourceType());
    }

    return toResponse(messageResourceRepository.save(entity));
  }

    /**
     * 삭제
     * - 존재하지 않는 id → 404 NOT_FOUND
     */
  @Transactional
    public void delete(Long id) {
    if (!messageResourceRepository.existsById(id)) {
      throw BusinessException.notFound("다국어 항목을 찾을 수 없습니다.");
    }
    messageResourceRepository.deleteById(id);
  }

    /**
     * msgKey → ko 텍스트 반환 (공통 헬퍼)
     * MenuService, SiteService, CodeService 등 msgKey 패턴 사용 서비스에서 공유
     * msgKey가 null이거나 존재하지 않으면 빈 문자열 반환
     */
  @Transactional(readOnly = true)
    public String resolveKo(String msgKey) {
    if (msgKey == null || msgKey.isBlank()) return "";
    return messageResourceRepository.findByKey(msgKey)
                .map(r -> r.getKo() != null ? r.getKo() : "")
                .orElse("");
  }

    /**
     * msgKey 목록 → en 텍스트 배치 조회 (공통 헬퍼)
     * - findByKeyIn 1회로 배치 조회하여 N+1 방지
     * - 반환 맵: key → (en이 비어있지 않으면 en, 아니면 ko 폴백)
     * - null/blank key는 맵에서 제외 (호출부가 원래값으로 폴백하도록)
     * FO 공개 API에서 다국어(en) 치환 시 사용
     */
  @Transactional(readOnly = true)
    public Map<String, String> resolveEnMap(Collection<String> keys) {
    if (keys == null || keys.isEmpty()) return Map.of();
        /* null/blank 제거 + 중복 제거 */
    var distinctKeys = keys.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .collect(Collectors.toList());
    if (distinctKeys.isEmpty()) return Map.of();
    return messageResourceRepository.findByKeyIn(distinctKeys).stream()
                .collect(Collectors.toMap(
                    MessageResource::getKey,
                    r -> (r.getEn() != null && !r.getEn().isBlank()) ? r.getEn()
                            : (r.getKo() != null ? r.getKo() : "")
                ));
  }

    /** Entity → Response DTO 변환 */
  private MessageResourceDto.Response toResponse(MessageResource entity) {
    return MessageResourceDto.Response.builder()
                .id(entity.getId())
                .key(entity.getKey())
                .ko(entity.getKo())
                .en(entity.getEn())
                .active(entity.isActive())
                .resourceType(entity.getResourceType())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
  }
}
