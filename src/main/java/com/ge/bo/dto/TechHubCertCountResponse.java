package com.ge.bo.dto;

/**
 * FO Tech Hub 인증(Certification)별 콘텐츠 건수 — 필터 패널 UL/IEC 옆 숫자 표시용.
 * - 원천값은 contents_master.attrs->>'video_prod_standard' 코드('1'=IEC, '2'=UL, '3'=IEC/UL 동시).
 *   '3'은 UL·IEC 양쪽 건수에 모두 가산되므로 두 count 의 합이 전체 건수와 다를 수 있다.
 * - UL/IEC 두 항목을 항상 반환한다(해당 콘텐츠 없으면 count=0). FE 라벨은 정적(UL/IEC).
 *
 * @param certCode 인증 코드(ul/iec) — FO 필터 옵션 id 와 동일
 * @param count    해당 인증에 해당하는 노출 영상 콘텐츠(master) 수
 */
public record TechHubCertCountResponse(
        String certCode,
        int count
) {}
