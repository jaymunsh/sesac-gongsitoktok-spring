/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/company/dto/CompanyResponse.java
 */
package com.gongsitoktok.assistant.company.dto;

import com.gongsitoktok.assistant.company.entity.Company;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 기업 상세 응답 (제작요청 v6 §4-7 + isActive 노출).
 *
 * <p>내부 {@code companySeq} 는 노출하지 않는다. {@code isActive} 는 공개 조회({@code /companies/{corpCode}}) 에서는 항상 {@code true}
 * (비활성 기업은 404 차단), 운영자 endpoint({@code /internal/companies/{corpCode}}) 에서는 실제 상태를 그대로 노출.</p>
 */
@Schema(description = "기업 상세")
public record CompanyResponse(
        @Schema(description = "DART 기업 고유번호", example = "00126380") String corpCode,
        @Schema(description = "기업명", example = "삼성전자") String corpName,
        @Schema(description = "로고 URL (없으면 null)") String logoUrl,
        @Schema(description = "기업 요약본") String summaryContent,
        @Schema(description = "활성 상태", example = "true") boolean isActive,
        @Schema(description = "생성 시각") LocalDateTime createdAt,
        @Schema(description = "갱신 시각") LocalDateTime updatedAt
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(
                c.getCorpCode(),
                c.getCorpName(),
                c.getLogoUrl(),
                c.getSummaryContent(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
