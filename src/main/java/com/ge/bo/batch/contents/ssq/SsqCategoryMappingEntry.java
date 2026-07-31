package com.ge.bo.batch.contents.ssq;

import java.util.List;

/**
 * SSQ Product Category → NAHP Devices & Systems 정적 매핑표.
 *
 * SSQ에는 NAHP L1(사업영역) 개념이 없어 SSQ L1이 NAHP (L1, L2) 조합으로 확장된다.
 * 중간 노드(예: "Servo Drive")는 NAHP에서 소멸하고 leaf가 NAHP L3로 직접 매핑된다.
 * 서로 다른 SSQ 경로가 같은 NAHP 카테고리에 매핑될 수 있다(N:1 — 예: S100 / S100 NEMA4X).
 *
 * E-01(Safety PLC)/E-02(iX7M) 모두 NAHP 쪽엔 이미 코드가 있고(page_data 기준), SSQ 쪽 실경로도 사내
 * 담당자 확인으로 확정되어 등록했다 — E-01은 "PLC>Safety PLC"(2단계, 다른 PLC 항목과 동일 구조),
 * E-02는 "Motion&Servo>Servo Drive>iX7M" 실데이터로 확인됨.
 *
 * NAHP 카테고리 코드는 page_data 테이블의 두 JSON 키로 나뉘어 확정된다 — L1(대분류)/L2(중분류)는
 * data_slug='category-data' 행의 category.code/depth/title/parentId, L3(소분류)는 같은 data_slug의 다른 행에
 * 담긴 product.depth="3" + _fetchedRel18.product.product_code(예: "L05-03-07"). L3는 일부만 코드가 배정돼
 * 있고(예: HMI 하위 LXP/eXP2/iXP3는 아직 product_code 없음) 나머지는 배정되는 대로 채우면 된다.
 */
public enum SsqCategoryMappingEntry {

    // ---- LV Drive (7건) ----
    LV_DRIVE_H100_PLUS(path("LV Drive", "H100+"), "LV Products & Systems", "Variable Frequency Drive", "H100 Plus", "L01", "L01-15", "L01-15-01"),
    LV_DRIVE_SP100(path("LV Drive", "SP100"), "LV Products & Systems", "Variable Frequency Drive", "SP100", "L01", "L01-15", "L01-15-02"),
    LV_DRIVE_G100(path("LV Drive", "G100"), "LV Products & Systems", "Variable Frequency Drive", "G100", "L01", "L01-15", "L01-15-03"),
    LV_DRIVE_M100(path("LV Drive", "M100"), "LV Products & Systems", "Variable Frequency Drive", "M100", "L01", "L01-15", "L01-15-04"),
    LV_DRIVE_S100(path("LV Drive", "S100"), "LV Products & Systems", "Variable Frequency Drive", "S100", "L01", "L01-15", "L01-15-05"),
    // N:1 — S100 NEMA4X도 S100과 동일 NAHP 카테고리로 통합(코드도 S100과 동일)
    LV_DRIVE_S100_NEMA4X(path("LV Drive", "S100 NEMA4X"), "LV Products & Systems", "Variable Frequency Drive", "S100", "L01", "L01-15", "L01-15-05"),
    LV_DRIVE_IS7(path("LV Drive", "iS7"), "LV Products & Systems", "Variable Frequency Drive", "iS7", "L01", "L01-15", "L01-15-06"),

    // ---- HMI (3건 — page_data에 L3 product_code가 아직 배정 안 돼 l3Code는 null) ----
    HMI_EXP2(path("HMI", "eXP2"), "Industrial Automation and Control", "Human Machine Interface", "eXP2", "L05", "L05-01", null),
    HMI_IXP3(path("HMI", "iXP3"), "Industrial Automation and Control", "Human Machine Interface", "iXP3", "L05", "L05-01", null),
    HMI_LXP(path("HMI", "LXP"), "Industrial Automation and Control", "Human Machine Interface", "LXP", "L05", "L05-01", null),

    // ---- PLC (4건) ----
    PLC_XGT(path("PLC", "XGT"), "Industrial Automation and Control", "Programmable Logic Controller", "XGT", "L05", "L05-02", "L05-02-01"),
    PLC_XGB(path("PLC", "XGB"), "Industrial Automation and Control", "Programmable Logic Controller", "XGB", "L05", "L05-02", "L05-02-02"),
    // E-01: SSQ 경로는 다른 PLC 항목과 동일하게 2단계(PLC>Safety PLC) — NAHP 매핑만 3단계(L1/L2/L3)로 확장됨
    PLC_SAFETY(path("PLC", "Safety PLC"), "Industrial Automation and Control", "Programmable Logic Controller", "SAFETY", "L05", "L05-02", "L05-02-03"),
    PLC_SMART_IO(path("PLC", "Smart I/O"), "Industrial Automation and Control", "Programmable Logic Controller", "SMART I/O", "L05", "L05-02", "L05-02-04"),

    // ---- Motion&Servo (7건) ----
    MOTION_CONTROLLER(path("Motion&Servo", "Controller"), "Industrial Automation and Control", "Motion & Servo", "Motion Controllers", "L05", "L05-03", "L05-03-01"),
    // 중간노드 "Servo Drive"는 NAHP에서 소멸 — leaf가 직접 L3로 매핑
    MOTION_SERVO_DRIVE_IX7NH(path("Motion&Servo", "Servo Drive", "iX7NH"), "Industrial Automation and Control", "Motion & Servo", "iX7NH Servo Drives", "L05", "L05-03", "L05-03-02"),
    // E-02: 2026-07-24 실데이터(Motion&Servo>Servo Drive>iX7M)로 확인되어 신규 등록
    MOTION_SERVO_DRIVE_IX7M(path("Motion&Servo", "Servo Drive", "iX7M"), "Industrial Automation and Control", "Motion & Servo", "iX7M Servo Drives", "L05", "L05-03", "L05-03-03"),
    MOTION_SERVO_DRIVE_L7NH(path("Motion&Servo", "Servo Drive", "L7NH"), "Industrial Automation and Control", "Motion & Servo", "L7NH Servo Drives", "L05", "L05-03", "L05-03-04"),
    MOTION_SERVO_DRIVE_L7P(path("Motion&Servo", "Servo Drive", "L7P"), "Industrial Automation and Control", "Motion & Servo", "L7P Servo Drives", "L05", "L05-03", "L05-03-05"),
    MOTION_SERVO_DRIVE_PHOX(path("Motion&Servo", "Servo Drive", "PHOX"), "Industrial Automation and Control", "Motion & Servo", "PHOX Servo Drives", "L05", "L05-03", "L05-03-06"),
    // E-04: Servo Motor 하위 L3(제품) 존재 여부 미확인 — 2레벨로 등록해두면 longest-match가 하위 레벨까지 흡수한다
    MOTION_SERVO_MOTOR(path("Motion&Servo", "Servo Motor"), "Industrial Automation and Control", "Motion & Servo", "Servo Motors", "L05", "L05-03", "L05-03-07");

    private final List<String> ssqPath;
    private final String nahpL1;
    private final String nahpL2;
    private final String nahpL3;
    private final String l1Code;
    private final String l2Code;
    private final String l3Code;

    SsqCategoryMappingEntry(List<String> ssqPath, String nahpL1, String nahpL2, String nahpL3,
                            String l1Code, String l2Code, String l3Code) {
        this.ssqPath = ssqPath;
        this.nahpL1 = nahpL1;
        this.nahpL2 = nahpL2;
        this.nahpL3 = nahpL3;
        this.l1Code = l1Code;
        this.l2Code = l2Code;
        this.l3Code = l3Code;
    }

    public List<String> getSsqPath() {
        return ssqPath;
    }

    /**
     * contents_category.nahp_category_id에 저장할 값 — CATALOG/CERTI와 같은 의미(NAHP 카테고리 참조)를 갖되,
     * SSQ는 이 표로 가공한 결과라는 점만 다르다. 코드가 아니라 "L1|L2|L3" 이름 경로를 쓰는 건 소스 무관 공통
     * 식별자로 CATALOG/CERTI와 형태를 맞추기 위한 설계이고, 실제 코드는 getL1Code()~getL3Code()로 별도 제공한다.
     */
    public String toNahpCategoryId() {
        return nahpL1 + "|" + nahpL2 + "|" + nahpL3;
    }

    /** category_l1_id/l2_id/l3_id에 저장할 실제 코드 — page_data(category-data) 기준(2026-07-24 확정) */
    public String getL1Code() {
        return l1Code;
    }

    public String getL2Code() {
        return l2Code;
    }

    public String getL3Code() {
        return l3Code;
    }

    private static List<String> path(String... segments) {
        return List.of(segments);
    }
}
