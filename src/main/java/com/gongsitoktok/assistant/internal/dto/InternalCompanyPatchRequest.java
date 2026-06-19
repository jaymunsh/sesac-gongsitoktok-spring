/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/internal/dto/InternalCompanyPatchRequest.java
 */
package com.gongsitoktok.assistant.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 운영자용 기업 upsert 요청 (제작요청 v6 §4-14).
 *
 * <ul>
 *     <li>기존 행 갱신 — 모든 필드 optional. {@code null} 이면 해당 필드 변경 안 함.</li>
 *     <li>신규 생성 — {@link #corpName} 필수 (서비스에서 명시 검증, 누락 시 {@code VALIDATION_FAILED}). {@code isActive} 미지정 시 신규 생성 시 {@code true} 기본.</li>
 * </ul>
 *
 * @param isActive 활성 상태 토글. {@code null} 이면 변경 안 함. 비활성화 시 공개 조회·메인 노출·신규 채팅방 생성에서 제외됨.
 */
@Schema(description = "운영자 기업 upsert 요청")
public record InternalCompanyPatchRequest(
        @Schema(description = "기업명") @Size(max = 255) String corpName,
        @Schema(description = "로고 URL (CDN)") @Size(max = 500) String logoUrl,
        @Schema(description = "기업 요약본") String summaryContent,
        @Schema(description = "활성 상태 (null 이면 변경 안 함)", example = "true") Boolean isActive
) {
}
