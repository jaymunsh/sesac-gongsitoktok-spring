/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/dto/CompanyCountResponse.java
 */
package com.gongsitoktok.assistant.company.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 활성 기업 개수 응답.
 *
 * <p>메인페이지의 'N대 기업' 같은 동적 카운트 표시용. 단순 숫자 대신 객체로 감싸 향후 메타(카테고리별 카운트 등) 확장 여지를 둔다.</p>
 */
@Schema(description = "활성 기업 개수")
public record CompanyCountResponse(
        @Schema(description = "isActive=true 인 기업 수", example = "10") long count
) {
}
