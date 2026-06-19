/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/internal/dto/InternalCompanyMainPinRequest.java
 */
package com.gongsitoktok.assistant.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자용 메인 노출 등록·갱신 요청 (tb_company_main upsert).
 *
 * <p>이미 핀이 있으면 displayOrder 만 변경, 없으면 신규 등록.</p>
 */
@Schema(description = "메인 노출 핀 등록/갱신 요청")
public record InternalCompanyMainPinRequest(
        @Schema(description = "DART 기업 고유번호", example = "00126380")
        @NotBlank(message = "corpCode 는 필수입니다.")
        String corpCode,

        @Schema(description = "표시 순서 (ASC, 작을수록 먼저 노출)", example = "1")
        @NotNull(message = "displayOrder 는 필수입니다.")
        @Min(value = 0, message = "displayOrder 는 0 이상이어야 합니다.")
        Integer displayOrder
) {
}
