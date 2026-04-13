package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TSX 생성 이력 저장 요청 DTO
 * [생성] 버튼 클릭 시 FE에서 전송
 */
@Getter
@NoArgsConstructor
public class TsxGenerationRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    private String name;

    @NotBlank(message = "폴더명을 입력해주세요.")
    private String folderName;

    @NotBlank(message = "파일명을 입력해주세요.")
    private String fileName;

    @NotBlank(message = "템플릿 타입을 입력해주세요.")
    private String templateType;

    @NotBlank(message = "빌더 설정 JSON이 없습니다.")
    private String configJson;

    @NotBlank(message = "생성된 TSX 코드가 없습니다.")
    private String tsxCode;
}
